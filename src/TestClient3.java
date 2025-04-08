import java.net.*;
import java.io.*;
import java.util.Scanner;

public class TestClient3 {
    private static final String SERVER_IP = "192.168.189.4";
    private static final int SERVER_PORT = 5000;
    private static final int TCP_PORT = 7003;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int requestId = 300;

    public TestClient3() throws Exception {
        socket = new DatagramSocket();
        serverAddress = InetAddress.getByName(SERVER_IP);
        System.out.println("Alice (Buyer) started on port: " + socket.getLocalPort());

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
                System.out.println("[TCP -> Alice] " + msg);

                if (msg.startsWith("INFORM_Req")) {
                    String[] parts = msg.split(" ");
                    String rq = parts[1];
                    writer.println("INFORM_Res " + rq + " Alice 4444-3333-2222-1111 01/27 789_Buyer_Street");
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
            TestClient3 client = new TestClient3();
            Scanner scanner = new Scanner(System.in);

            System.out.println("\n--- Alice (Buyer) Menu ---");
            System.out.println("1. Register");
            System.out.println("2. Subscribe to Item");
            System.out.println("3. Bid on Item");
            System.out.println("4. Deregister");
            System.out.println("5. DE-Subscribe");
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
                    case 1 -> client.sendMessage("REGISTER " + client.requestId++ + " Alice buyer 192.168.189.6 " + client.socket.getLocalPort() + " " + TCP_PORT);
                    case 2 -> client.sendMessage("SUBSCRIBE " + client.requestId++ + " Camera");
                    case 3 -> client.sendMessage("BID " + client.requestId++ + " Camera 550");
                    case 4 -> client.sendMessage("DE-REGISTER " + client.requestId++ + " Alice");
                    case 5 -> client.sendMessage("DE-SUBSCRIBE " + client.requestId++ + " Camera");
                    default -> System.out.println("Invalid option.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
