import java.net.*;
import java.io.*;
import java.util.Scanner;

public class TestClient2 {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 5000;
    private static final int TCP_PORT = 7002;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int requestId = 200;

    public TestClient2() throws Exception {
        socket = new DatagramSocket();
        serverAddress = InetAddress.getByName(SERVER_IP);
        System.out.println("Bob (Seller) started on port: " + socket.getLocalPort());

        // Start TCP listener
        new Thread(this::listenTCP).start();
    }

    public void listenTCP() {
        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                String msg = reader.readLine();
                System.out.println("[TCP -> Bob] " + msg);

                if (msg.startsWith("NEGOTIATE_REQ")) {
                    String[] parts = msg.split(" ");
                    String rq = parts[1];
                    String item = parts[2];
                    String currentPrice = parts[3];
                    String newPrice = String.valueOf(Double.parseDouble(currentPrice) - 50); // Automatically lower the price by 50
                    System.out.println("[NEGOTIATION] " + item + " at " + currentPrice + ". New price: " + newPrice);
                    writer.println("ACCEPT " + rq + " " + item + " " + newPrice);  // Respond with ACCEPT and new price
                } else if (msg.startsWith("INFORM_Req")) {
                    String[] parts = msg.split(" ");
                    String rq = parts[1];
                    writer.println("INFORM_Res " + rq + " Bob 9999-8888-7777-6666 12/26 123_Seller_Street");
                }

                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) throws Exception {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
        socket.send(packet);

        byte[] buffer = new byte[1024];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);
        String reply = new String(response.getData(), 0, response.getLength());
        System.out.println("[UDP <- Server] " + reply);
    }

    public void close() {
        socket.close();
    }

    public static void main(String[] args) {
        try {
            TestClient2 client = new TestClient2();
            Scanner scanner = new Scanner(System.in);

            System.out.println("\n--- Bob (Seller) Menu ---");
            System.out.println("1. Register");
            System.out.println("2. List Item");
            System.out.println("3. Deregister");
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
                    case 1 -> client.sendMessage("REGISTER " + client.requestId++ + " Bob seller 127.0.0.1 " + client.socket.getLocalPort() + " " + TCP_PORT);
                    case 2 -> client.sendMessage("LIST_ITEM " + client.requestId++ + " Camera NikonD750 500 60s");
                    case 3 -> client.sendMessage("DE-REGISTER " + client.requestId++ + " Bob");
                    default -> System.out.println("Invalid option.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
