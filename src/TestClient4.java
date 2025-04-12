import java.net.*;
import java.io.*;
import java.util.Scanner;

public class TestClient4 {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 5000;
    private static final int TCP_PORT = 7004;
    private DatagramSocket listenerSocket;      // For broadcast messages
    private DatagramSocket requestSocket;       // For synchronous request-response
    private InetAddress serverAddress;
    private int requestId = 400;
    private static final String CLIENT_ID = "Charlie"; // Unique identifier
    private static final String ITEM_NAME = "Camera";

    public TestClient4() throws Exception {
        // Create the listener socket (its port is used during registration)
        listenerSocket = new DatagramSocket();
        // Create the request socket for sending commands
        requestSocket = new DatagramSocket();
        serverAddress = InetAddress.getByName(SERVER_IP);
        System.out.println("Charlie (Buyer) started on port: " + listenerSocket.getLocalPort());
        
        // Start TCP listener thread
        new Thread(this::listenTCP).start();
        
        // Start UDP broadcast listener thread
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    listenerSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("[UDP Broadcast Received] " + msg);
                    
                    // Parse for bid updates
                    if (msg.startsWith("BID_UPDATE")) {
                        String[] parts = msg.split(" ");
                        if (parts.length >= 5) {
                            System.out.println("New bid on " + parts[2] + ": $" + parts[3] + " by " + parts[4]);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void listenTCP() {
        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                
                String msg = reader.readLine();
                System.out.println("[TCP -> Charlie] " + msg);
                
                if (msg.startsWith("INFORM_Req")) {
                    String[] parts = msg.split(" ");
                    String rq = parts[1];
                    writer.println("INFORM_Res " + rq + " " + CLIENT_ID + " 7777-6666-5555-4444 05/28 456_Buyer_Ave");
                } else if (msg.startsWith("WINNER")) {
                    System.out.println("🎉 CONGRATULATIONS! You won the auction!");
                }
                
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Use the dedicated requestSocket for sending commands and awaiting replies.
    public void sendMessage(String message) throws Exception {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
        requestSocket.send(packet);
        
        // Wait for a response on the requestSocket.
        byte[] buffer = new byte[1024];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        requestSocket.setSoTimeout(3000);
        try {
            requestSocket.receive(response);
            String reply = new String(response.getData(), 0, response.getLength());
            System.out.println("[UDP <- Server] " + reply);
        } catch (SocketTimeoutException e) {
            System.out.println("[UDP <- Server] No response (timeout)");
        }
    }

    public void close() {
        listenerSocket.close();
        requestSocket.close();
    }

    public static void main(String[] args) {
        try {
            TestClient4 client = new TestClient4();
            Scanner scanner = new Scanner(System.in);
            
            System.out.println("\n--- Charlie (Buyer) Menu ---");
            System.out.println("1. Register");
            System.out.println("2. Subscribe to Camera");
            System.out.println("3. Place Bid (550)");
            System.out.println("4. Place Higher Bid (600)");
            System.out.println("5. Place Final Bid (650)");
            System.out.println("6. Unsubscribe from Camera");
            System.out.println("7. Deregister");
            System.out.println("0. Exit");
            
            while (true) {
                System.out.print("\nSelect option: ");
                int choice = Integer.parseInt(scanner.nextLine());
                
                switch (choice) {
                    case 0 -> {
                        client.close();
                        System.out.println("Disconnected.");
                        return;
                    }
                    // Note: Each command now includes the client identifier as the last argument.
                    case 1 -> client.sendMessage("REGISTER " + client.requestId++ + " " + CLIENT_ID + " buyer 127.0.0.1 " +
                            client.listenerSocket.getLocalPort() + " " + TCP_PORT);
                    case 2 -> client.sendMessage("SUBSCRIBE " + client.requestId++ + " " + ITEM_NAME + " " + CLIENT_ID);
                    case 3 -> client.sendMessage("BID " + client.requestId++ + " " + ITEM_NAME + " 550 " + CLIENT_ID);
                    case 4 -> client.sendMessage("BID " + client.requestId++ + " " + ITEM_NAME + " 600 " + CLIENT_ID);
                    case 5 -> client.sendMessage("BID " + client.requestId++ + " " + ITEM_NAME + " 650 " + CLIENT_ID);
                    case 6 -> client.sendMessage("UNSUBSCRIBE " + client.requestId++ + " " + ITEM_NAME + " " + CLIENT_ID);
                    case 7 -> client.sendMessage("DE-REGISTER " + client.requestId++ + " " + CLIENT_ID);
                    default -> System.out.println("Invalid option.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
