import java.net.*;
import java.io.*;
import java.util.Scanner;

public class TestClient2 {
    private static final String SERVER_IP = "192.168.189.4";
    private static final int SERVER_PORT = 5000;
    private static final int TCP_PORT = 7002;
    private DatagramSocket listenerSocket;
    private InetAddress serverAddress;
    private int requestId = 200;

    public TestClient2() throws Exception {
        listenerSocket = new DatagramSocket();  // Shared socket for listening only
        serverAddress = InetAddress.getByName(SERVER_IP);
        System.out.println("Bob (Seller) started on port: " + listenerSocket.getLocalPort());

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
                System.out.println("[TCP -> Bob] " + msg);

                if (msg.startsWith("NEGOTIATE_REQ")) {
                    String[] parts = msg.split(" ");
                    String rq = parts[1];
                    String item = parts[2];
                    String currentPrice = parts[3];
                    String newPrice = String.valueOf(Double.parseDouble(currentPrice) - 50);
                    System.out.println("[NEGOTIATION] " + item + " at " + currentPrice + ". New price: " + newPrice);
                    writer.println("ACCEPT " + rq + " " + item + " " + newPrice);
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

    public void sendTCPMessage(String message) {
        try (
            Socket socket = new Socket(SERVER_IP, TCP_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            out.println(message);
            String response = in.readLine();
            if (response != null) {
                System.out.println("[TCP <- Server] " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            TestClient2 client = new TestClient2();
            Scanner scanner = new Scanner(System.in);

            System.out.println("\n--- Bob (Seller) Menu ---");
            System.out.println("1. Register");
            System.out.println("2. List Item");
            System.out.println("3. Deregister");
            System.out.println("4. Send TCP ACCEPT");
            System.out.println("5. Send TCP REFUSE");
            System.out.println("6. Send TCP INFORM_Res");
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
                    case 1 -> client.sendMessage("REGISTER " + client.requestId++ + " Bob seller 192.168.189.5 " + client.listenerSocket.getLocalPort() + " " + TCP_PORT);
                    case 2 -> client.sendMessage("LIST_ITEM " + client.requestId++ + " Camera NikonD750 500 60s");
                    case 3 -> client.sendMessage("DE-REGISTER " + client.requestId++ + " Bob");
                    case 4 -> client.sendTCPMessage("ACCEPT " + client.requestId++ + " Camera 450");
                    case 5 -> client.sendTCPMessage("REFUSE " + client.requestId++ + " Camera");
                    case 6 -> client.sendTCPMessage("INFORM_Res " + client.requestId++ + " Bob 9999-8888-7777-6666 12/26 123_Seller_Street");
                    default -> System.out.println("Invalid option.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
