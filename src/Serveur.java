import java.net.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;

public class Serveur {
    private static final int SERVER_PORT = 5000;
    private static final int TCP_PORT = 6000;
    private static final int BUFFER_SIZE = 1024;
    private static final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private static final Map<String, Double> currentBids = new ConcurrentHashMap<>();
    private static final Map<String, String> highestBidders = new ConcurrentHashMap<>();
    private static final Map<String, Semaphore> auctionLocks = new ConcurrentHashMap<>();
    private static final Map<String, Auction> auctions = new ConcurrentHashMap<>();
    private static final DBClass db = new DBClass("auction_data.csv");
    private static final boolean DEBUG_MODE = true;
    private static int requestIdCounter = 1000;

    public static void main(String[] args) {
        System.out.println("Server is running on UDP:" + SERVER_PORT + " TCP:" + TCP_PORT);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        if (DEBUG_MODE) {
            Thread debugConsole = new Thread(() -> runDebugConsole());
            debugConsole.setDaemon(true);
            debugConsole.start();
        }

        Thread tcpThread = new Thread(() -> startTCPServer());
        tcpThread.start();

        try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {
            byte[] receiveBuffer = new byte[BUFFER_SIZE];
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, BUFFER_SIZE);
                serverSocket.receive(receivePacket);
                executor.submit(() -> handlePacket(serverSocket, receivePacket));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startTCPServer() {
        try (ServerSocket tcpSocket = new ServerSocket(TCP_PORT)) {
            while (true) {
                Socket socket = tcpSocket.accept();
                new Thread(() -> handleTCPConnection(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleTCPConnection(Socket socket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line = in.readLine();
            System.out.println("[TCP Request Received] " + line);
            String[] parts = line.split(" ");
            String type = parts[0];
            String rq = parts[1];

            if ("INFORM_Res".equals(type)) {
                String name = parts[2];
                String cc = parts[3];
                String exp = parts[4];
                String addr = parts[5];
                System.out.println("[INFORM_Res] " + name + " | CC: " + cc + " | Addr: " + addr);
                out.println("ACK " + rq);
            } else if ("ACCEPT".equals(type)) {
                String item = parts[2];
                String newPrice = parts[3];
                currentBids.put(item, Double.parseDouble(newPrice));
                broadcastUpdate("PRICE_ADJUSTMENT " + rq + " " + item + " " + newPrice + " 5s", item);
            } else if ("REFUSE".equals(type)) {
                String item = parts[2];
                System.out.println("[NEGOTIATION REFUSED] " + item);
            }
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void runDebugConsole() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n[DEBUG] Type 1: Clients, 2: Items, 3: Auctions, 4: Trigger BID_UPDATE manually\n");
            String input = scanner.nextLine();
            switch (input.trim()) {
                case "1" -> System.out.println("[DEBUG] Registered clients: " + clients.size());
                case "2" -> {
                    List<Map<String, String>> items = db.listItems();
                    System.out.println("[DEBUG] Items: " + items.size());
                    for (Map<String, String> item : items)
                        System.out.println("- " + item.get("Item_Name"));
                }
                case "3" -> {
                    for (Auction a : auctions.values()) {
                        System.out.println("[AUCTION] " + a.itemName + " Seller: " + a.sellerName);
                    }
                }
                case "4" -> {
                    System.out.print("Enter item name: ");
                    String itemName = scanner.nextLine();
                    Auction auction = auctions.get(itemName);
                    if (auction == null) {
                        System.out.println("[ERROR] No such auction found.");
                        break;
                    }
                    double highestBid = currentBids.getOrDefault(itemName, auction.startPrice);
                    String highestBidder = highestBidders.getOrDefault(itemName, "None");
                    long timeLeft = (auction.endTime - System.currentTimeMillis()) / 1000;
                    String bidUpdateMsg = "BID_UPDATE " + (++requestIdCounter) + " " + itemName + " " + highestBid + " " + highestBidder + " " + timeLeft + "s";
                    broadcastUpdate(bidUpdateMsg, itemName);
                    System.out.println("[MANUAL BROADCAST] Sent BID_UPDATE for " + itemName);
                }
                default -> System.out.println("[DEBUG] Invalid option.");
            }
        }
    }

    private static void handlePacket(DatagramSocket serverSocket, DatagramPacket packet) {
        try {
            String message = new String(packet.getData(), 0, packet.getLength());
            InetAddress address = packet.getAddress();
            int port = packet.getPort();
            System.out.println("[UDP Request Received] From: " + address + ":" + port + " | " + message);

            String response = processMessage(message);
            byte[] sendBuffer = response.getBytes();
            serverSocket.send(new DatagramPacket(sendBuffer, sendBuffer.length, address, port));

            System.out.println("[UDP Response Sent] To: " + address + ":" + port + " | " + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // In this version, commands include the client identifier as the last argument.
    private static String processMessage(String msg) {
        String[] parts = msg.split(" ");
        String command = parts[0];
        String rq = parts[1];

        switch (command) {
            case "REGISTER": {
                // Format: REGISTER rq clientId role clientIp udpPort tcpPort
                String name = parts[2];
                String role = parts[3];
                String clientIp = parts[4];
                int udpPort = Integer.parseInt(parts[5]);
                int tcpPort = Integer.parseInt(parts[6]);

                System.out.println("[REGISTER] " + name + " | Role: " + role + " | IP: " + clientIp + " | UDP Port: " + udpPort + " | TCP Port: " + tcpPort);
                if (clients.containsKey(name)) {
                    return "REJECTED " + rq + " USER_ALREADY_EXISTS";
                } else {
                    clients.put(name, new ClientInfo(name, role, clientIp, udpPort, tcpPort));
                    return "REGISTERED " + rq;
                }
            }
            case "LIST_ITEM": {
                // Format: LIST_ITEM rq itemName itemDesc itemPrice duration clientId
                String clientId = parts[parts.length - 1];
                if (!clients.containsKey(clientId)) {
                    return "LIST_DENIED " + rq;
                } else {
                    String item = parts[2];
                    String desc = parts[3].replace('_', ' ');
                    String price = parts[4];
                    String durationStr = parts[5];
                    long durationMs = parseDuration(durationStr);
                    long now = System.currentTimeMillis();
                    db.addItem(item, desc, price, durationStr);
                    currentBids.put(item, Double.parseDouble(price));
                    auctionLocks.put(item, new Semaphore(1));
                    Auction auction = new Auction(item, clientId, desc, Double.parseDouble(price), now, now + durationMs, rq);
                    auctions.put(item, auction);
                    startAuctionMonitor(auction);
                    return "ITEM_LISTED " + rq;
                }
            }
            case "DE-REGISTER": {
                // Format: DE-REGISTER rq clientId
                String clientId = parts[2];
                if (!clients.containsKey(clientId)) {
                    return "UNREGISTER_DENIED " + rq;
                } else {
                    clients.remove(clientId);
                    return "UNREGISTERED " + rq;
                }
            }
            case "SUBSCRIBE": {
                // Format: SUBSCRIBE rq itemName clientId
                String clientId = parts[parts.length - 1];
                if (!clients.containsKey(clientId)) {
                    return "SUBSCRIPTION_DENIED " + rq;
                } else {
                    String item = parts[2];
                    subscriptions.putIfAbsent(item, ConcurrentHashMap.newKeySet());
                    Set<String> subs = subscriptions.get(item);
                    if (subs.contains(clientId)) {
                        return "SUBSCRIPTION_FAILED " + rq + " Already subscribed";
                    }
                    subs.add(clientId);
                    return "SUBSCRIBED " + rq;
                }
            }
            
            case "UNSUBSCRIBE": {
                // Format: DE-SUBSCRIBE rq itemName clientId
                String clientId = parts[parts.length - 1];
                if (!clients.containsKey(clientId)) {
                    return "UNSUBSCRIPTION_DENIED " + rq;
                } else {
                    String item = parts[2];
                    if (subscriptions.containsKey(item)) {
                        Set<String> subscribers = subscriptions.get(item);
                        if (subscribers.remove(clientId)) {
                            return "UNSUBSCRIBED " + rq;
                        } else {
                            return "UNSUBSCRIPTION_DENIED " + rq + " NOT_SUBSCRIBED";
                        }
                    } else {
                        return "UNSUBSCRIPTION_DENIED " + rq + " ITEM_NOT_FOUND";
                    }
                }
            }
            case "BID": {
                // Format: BID rq itemName amount clientId
                String clientId = parts[parts.length - 1];
                String item = parts[2];
                double amount = Double.parseDouble(parts[3]);
                if (!clients.containsKey(clientId)) {
                    return "BID_REJECTED " + rq;
                }
                Semaphore lock = auctionLocks.get(item);
                try {
                    lock.acquire();
                    if (amount > currentBids.getOrDefault(item, 0.0)) {
                        currentBids.put(item, amount);
                        highestBidders.put(item, clientId);
                        Auction a = auctions.get(item);
                        broadcastUpdate("BID_UPDATE " + rq + " " + item + " " + amount + " " + clientId + " 5s", item);
                        a.hasBid = true;
                        return "BID_ACCEPTED " + rq;
                    }
                } catch (Exception e) {
                    return "BID_REJECTED " + rq;
                } finally {
                    lock.release();
                }
                return "BID_REJECTED " + rq + " Too low";
            }
            default:
                return "UNKNOWN_COMMAND " + rq;
        }
    }

    private static void startAuctionMonitor(Auction a) {
        new Thread(() -> {
            try {
                long halfway = a.startTime + (a.endTime - a.startTime) / 2;
                while (System.currentTimeMillis() < a.endTime) {
                    Thread.sleep(1000);
                    if (!a.negotiationSent && !a.hasBid && System.currentTimeMillis() >= halfway) {
                        sendNegotiationReq(a);
                        a.negotiationSent = true;
                    }
                }
                finalizeAuction(a);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void finalizeAuction(Auction a) {
        if (highestBidders.containsKey(a.itemName)) {
            String buyer = highestBidders.get(a.itemName);
            String seller = a.sellerName;
            double price = currentBids.get(a.itemName);
            ClientInfo b = clients.get(buyer);
            ClientInfo s = clients.get(seller);
            sendTCPMessage(b, "WINNER " + requestIdCounter + " " + a.itemName + " " + price + " " + seller);
            sendTCPMessage(s, "SOLD " + requestIdCounter + " " + a.itemName + " " + price + " " + buyer);
            sendTCPMessage(b, "INFORM_Req " + (++requestIdCounter) + " " + a.itemName + " " + price);
            sendTCPMessage(s, "INFORM_Req " + requestIdCounter + " " + a.itemName + " " + price);
        } else {
            sendTCPMessage(clients.get(a.sellerName), "NON_OFFER " + a.requestId + " " + a.itemName);
        }
    }

    private static void sendTCPMessage(ClientInfo client, String msg) {
        try (Socket socket = new Socket(client.ip, client.tcpPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(msg);
            System.out.println("[TCP Sent] To: " + client.name + " | " + msg);
        } catch (IOException e) {
            System.err.println("[TCP Error] Could not send to " + client.name);
        }
    }

    private static void broadcastUpdate(String msg, String item) {
        Set<String> buyers = subscriptions.getOrDefault(item, new HashSet<>());
        buyers.add(auctions.get(item).sellerName); // include seller in updates
        for (String user : buyers) {
            ClientInfo client = clients.get(user);
            try (DatagramSocket socket = new DatagramSocket()) {
                byte[] buf = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(client.ip), client.udpPort);
                socket.send(packet);
                System.out.println("[UDP Broadcast] Sent to " + user + " | " + msg);
            } catch (IOException e) {
                System.err.println("[UDP Error] Failed to send to " + user);
            }
        }
    }

    private static void sendNegotiationReq(Auction a) {
        ClientInfo seller = clients.get(a.sellerName);
        String msg = "NEGOTIATE_REQ " + a.requestId + " " + a.itemName + " " + a.startPrice + " 5s";
        sendTCPMessage(seller, msg);
    }

    private static long parseDuration(String d) {
        if (d.endsWith("s")) return Integer.parseInt(d.replace("s", "")) * 1000L;
        return 5000L;
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
    public int getUdpPort() { return udpPort; }
    public int getTcpPort() { return tcpPort; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getClientIp() { return ip; }
}

class Auction {
    String itemName, sellerName, description, requestId;
    double startPrice;
    long startTime, endTime;
    boolean negotiationSent = false;
    boolean hasBid = false;
    public Auction(String itemName, String sellerName, String description, double price, long startTime, long endTime, String rq) {
        this.itemName = itemName;
        this.sellerName = sellerName;
        this.description = description;
        this.startPrice = price;
        this.startTime = startTime;
        this.endTime = endTime;
        this.requestId = rq;
    }
}
