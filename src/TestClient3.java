import java.net.*;
import java.io.*;
import java.util.Scanner;

public class TestClient3 {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 5000;
    private static final int TCP_PORT = 7003;
    private DatagramSocket listenerSocket;
    private InetAddress serverAddress;
    private int requestId = 300;
    private static final String ITEM_NAME = "Camera";

    public TestClient3() throws Exception {
        listenerSocket = new DatagramSocket();
        serverAddress = InetAddress.getByName(SERVER_IP);
        System.out.println("Alice (Buyer) started on port: " + listenerSocket.getLocalPort());
        
        // Start TCP listener
        new Thread(this::listenTCP).start();
        
        // Start UDP listener
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    listenerSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("[UDP Broadcast Received] " + msg);
                    
                    // Parse bid updates to show price changes
                    if (msg.startsWith("BID_UPDATE")) {
                        String[] parts = msg.split(" ");
                        if (parts.length >= 4) {
                            System.out.println("Current price for " + parts[2] + " is now: $" + parts[3]);
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
                System.out.println("[TCP -> Alice] " + msg);
                
                if (msg.startsWith("INFORM_Req")) {
                    String[] parts = msg.split(" ");
                    String rq = parts[1];
                    writer.println("INFORM_Res " + rq + " Alice 4444-3333-2222-1111 01/27 789_Buyer_Street");
                } else if (msg.startsWith("WINNER")) {
                    System.out.println("🎉 CONGRATULATIONS! You won the auction!");
                }
                
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Sends a UDP message using a temporary DatagramSocket
    public void sendMessage(String message) throws Exception {
        try (DatagramSocket tempSocket = new DatagramSocket()) {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
            tempSocket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            tempSocket.setSoTimeout(3000); // Optional: timeout
            tempSocket.receive(response);
            String reply = new String(response.getData(), 0, response.getLength());
            System.out.println("[UDP <- Server] " + reply);
        } catch (SocketTimeoutException e) {
            System.out.println("[UDP <- Server] No response (timeout)");
        }
    }

    public void close() {
        listenerSocket.close();
    }

    public static void main(String[] args) {
        try {
            TestClient3 client = new TestClient3();
            Scanner scanner = new Scanner(System.in);
            
            System.out.println("\n--- Alice (Buyer) Menu ---");
            System.out.println("1. Register");
            System.out.println("2. Subscribe to Camera");
            System.out.println("3. Place First Bid (520)");
            System.out.println("4. Place Higher Bid (570)");
            System.out.println("5. Test Low Bid (490)");
            System.out.println("6. Subscribe Again (Test Duplicate)");
            System.out.println("7. Unsubscribe from Camera");
            System.out.println("8. Deregister");
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
                    case 1 -> client.sendMessage("REGISTER " + client.requestId++ + " Alice buyer 127.0.0.2 " + client.listenerSocket.getLocalPort() + " " + TCP_PORT);
                    case 2 -> client.sendMessage("SUBSCRIBE " + client.requestId++ + " " + ITEM_NAME);
                    case 3 -> client.sendMessage("BID " + client.requestId++ + " " + ITEM_NAME + " 520");
                    case 4 -> client.sendMessage("BID " + client.requestId++ + " " + ITEM_NAME + " 570");
                    case 5 -> client.sendMessage("BID " + client.requestId++ + " " + ITEM_NAME + " 490");
                    case 6 -> client.sendMessage("SUBSCRIBE " + client.requestId++ + " " + ITEM_NAME);
                    case 7 -> client.sendMessage("DE-SUBSCRIBE " + client.requestId++ + " " + ITEM_NAME);
                    case 8 -> client.sendMessage("DE-REGISTER " + client.requestId++ + " Alice");
                    default -> System.out.println("Invalid option.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}