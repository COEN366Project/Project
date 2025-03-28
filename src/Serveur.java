/*
 * Multithreaded version - COEN366 Project Server with Debugging Console
 */

 import java.net.*;
 import java.util.*;
 import java.io.*;
 import java.util.concurrent.*;
 
 public class Serveur {
     private static final int SERVER_PORT = 5000;
     private static final int BUFFER_SIZE = 1024;
     private static final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
     private static final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
     private static final Map<String, Double> currentBids = new ConcurrentHashMap<>();
     private static final Map<String, String> highestBidders = new ConcurrentHashMap<>();
     private static final Map<String, Semaphore> auctionLocks = new ConcurrentHashMap<>();
     private static final DBClass db = new DBClass("auction_data.csv");
     private static final boolean DEBUG_MODE = false; // Toggle debugging console here
 
     public static void main(String[] args) {
         System.out.println("Server is running on port " + SERVER_PORT);
         ExecutorService executor = Executors.newFixedThreadPool(10);
 
         if (DEBUG_MODE) {
             Thread debugConsole = new Thread(() -> runDebugConsole());
             debugConsole.setDaemon(true);
             debugConsole.start();
         }
 
         try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {
             byte[] receiveBuffer = new byte[BUFFER_SIZE];
             while (true) {
                 DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, BUFFER_SIZE);
                 serverSocket.receive(receivePacket);
                 executor.submit(() -> handlePacket(serverSocket, receivePacket));
             }
         } catch (Exception e) {
             e.printStackTrace();
         } finally {
             executor.shutdown();
         }
     }
 
     private static void runDebugConsole() {
         Scanner scanner = new Scanner(System.in);
         while (true) {
             System.out.println("\n[DEBUG] Type 1 for client count, 2 for auction items, 0 to refresh menu");
             String input = scanner.nextLine();
             switch (input.trim()) {
                 case "1" -> System.out.println("[DEBUG] Current registered clients: " + clients.size());
                 case "2" -> {
                     List<Map<String, String>> items = db.listItems();
                     System.out.println("[DEBUG] Total items: " + items.size());
                     for (Map<String, String> item : items) {
                         System.out.print(item);
                         System.out.println(" ");
                     }
                 }
                 case "0" -> System.out.println("[DEBUG] Menu refreshed.");
                 default -> System.out.println("[DEBUG] Invalid option.");
             }
         }
     }
 
     private static void handlePacket(DatagramSocket serverSocket, DatagramPacket receivePacket) {
         try {
             String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
             InetAddress address = receivePacket.getAddress();
             int port = receivePacket.getPort();
             System.out.println("[Request Received] From: " + address + ":" + port + " | Message: " + message);
 
             String response = processMessage(message, address, port);
             byte[] sendBuffer = response.getBytes();
             DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, address, port);
             serverSocket.send(sendPacket);
 
             System.out.println("[Response Sent] To: " + address + ":" + port + " | Message: " + response);
         } catch (Exception e) {
             e.printStackTrace();
         }
     }
 
     private static String processMessage(String message, InetAddress address, int port) {
         String[] parts = message.split(" ");
         if (parts.length < 2) return "INVALID REQUEST";
 
         String command = parts[0];
         String requestId = parts[1];
 
         switch (command) {
             case "REGISTER": {
                 if (parts.length < 7)
                     return "REGISTER-DENIED " + requestId + " Invalid Request Parameters";
                 String name = parts[2];
                 String role = parts[3];
                 String ip = parts[4];
                 int udpPort = Integer.parseInt(parts[5]);
                 int tcpPort = Integer.parseInt(parts[6]);
                 if (clients.containsKey(name))
                     return "REGISTER-DENIED " + requestId + " Name Already Taken";
                 clients.put(name, new ClientInfo(name, role, ip, udpPort, tcpPort));
                 return "REGISTERED " + requestId;
             }
 
             case "DE-REGISTER": {
                 if (parts.length < 3) return "INVALID REQUEST";
                 String name = parts[2];
                 clients.remove(name);
                 return "DE-REGISTERED " + requestId;
             }
 
             case "LIST_ITEM": {
                 if (parts.length < 6) return "LIST-DENIED " + requestId + " Invalid Parameters";
                 String itemName = parts[2];
                 String desc = parts[3].replace('_', ' ');
                 String price = parts[4];
                 String duration = parts[5];
                 try {
                     Double.parseDouble(price); // Validate numeric price
                 } catch (NumberFormatException e) {
                     return "LIST-DENIED " + requestId + " Invalid price format";
                 }
                 db.addItem(itemName, desc, price, duration);
                 currentBids.put(itemName, Double.parseDouble(price));
                 auctionLocks.put(itemName, new Semaphore(1));
                 return "ITEM_LISTED " + requestId;
             }
 
             case "SUBSCRIBE": {
                 if (parts.length < 3) return "SUBSCRIBTION-DENIED " + requestId + " Missing item name";
                 String itemName = parts[2];
                 String name = findClientNameByAddress(address);
                 if (name == null) return "SUBSCRIBTION-DENIED " + requestId + " Client not registered";
                 subscriptions.putIfAbsent(itemName, ConcurrentHashMap.newKeySet());
                 subscriptions.get(itemName).add(name);
                 return "SUBSCRIBED " + requestId;
             }
 
             case "DE-SUBSCRIBE": {
                 if (parts.length < 3) return "INVALID REQUEST";
                 String itemName = parts[2];
                 String name = findClientNameByAddress(address);
                 if (subscriptions.containsKey(itemName)) {
                     subscriptions.get(itemName).remove(name);
                 }
                 return "DE-SUBSCRIBED " + requestId;
             }
 
             case "BID": {
                 if (parts.length < 4) return "BID_REJECTED " + requestId + " Invalid Parameters";
                 String itemName = parts[2];
                 double bidAmount = Double.parseDouble(parts[3]);
                 String name = findClientNameByAddress(address);
 
                 Semaphore lock = auctionLocks.get(itemName);
                 if (lock == null) return "BID_REJECTED " + requestId + " Item not listed";
 
                 try {
                     lock.acquire();
                     double current = currentBids.getOrDefault(itemName, 0.0);
                     if (bidAmount <= current) {
                         return "BID_REJECTED " + requestId + " Bid too low";
                     } else {
                         currentBids.put(itemName, bidAmount);
                         highestBidders.put(itemName, name);
                         broadcastUpdate("BID_UPDATE " + requestId + " " + itemName + " " + bidAmount + " " + name + " 5m", itemName);
                         return "BID_ACCEPTED " + requestId;
                     }
                 } catch (InterruptedException e) {
                     return "BID_REJECTED " + requestId + " Server error";
                 } finally {
                     lock.release();
                 }
             }
 
             default:
                 return "UNKNOWN COMMAND";
         }
     }
 
     private static String findClientNameByAddress(InetAddress address) {
         for (ClientInfo info : clients.values()) {
             if (info.ip.equals(address.getHostAddress())) return info.name;
         }
         return null;
     }
 
     private static void broadcastUpdate(String message, String itemName) {
         Set<String> subs = subscriptions.getOrDefault(itemName, new HashSet<>());
         for (String user : subs) {
             ClientInfo client = clients.get(user);
             try {
                 DatagramSocket socket = new DatagramSocket();
                 byte[] buffer = message.getBytes();
                 DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                         InetAddress.getByName(client.ip), client.udpPort);
                 socket.send(packet);
                 socket.close();
                 System.out.println("[Broadcast] Sent to " + user + " | Message: " + message);
             } catch (Exception e) {
                 e.printStackTrace();
             }
         }
     }
 }
 
 class ClientInfo {
     String name, role, ip;
     int udpPort, tcpPort;
 
     public ClientInfo(String name, String role, String ip, int udpPort, int tcpPort) {
         this.name = name;
         this.role = role;
         this.ip = ip;
         this.udpPort = udpPort;
         this.tcpPort = tcpPort;
     }
 }
 