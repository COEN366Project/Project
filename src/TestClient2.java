import java.net.*;
import java.util.Scanner;

public class TestClient2 {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 5000;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int requestId = 200;

    public TestClient2() throws Exception {
        socket = new DatagramSocket(); // Let OS assign an available port
        serverAddress = InetAddress.getByName(SERVER_IP);
        System.out.println("Client started on port: " + socket.getLocalPort());
    }

    public void sendMessage(String message) throws Exception {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
        socket.send(packet);

        byte[] buffer = new byte[1024];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);
        String reply = new String(response.getData(), 0, response.getLength());
        System.out.println("Server: " + reply);
    }

    public void close() {
        socket.close();
    }

    public static void main(String[] args) {
        try {
            TestClient2 client = new TestClient2();
            Scanner scanner = new Scanner(System.in);

            System.out.println("\n--- P2PAS Client Menu ---");
            System.out.println("1. Register");
            System.out.println("2. List Item");
            System.out.println("3. Subscribe to Item");
            System.out.println("4. Bid on Item");
            System.out.println("5. Deregister");
            System.out.println("0. Exit");

            while (true) {
                System.out.print("\nSelect option: ");
                String input = scanner.nextLine().trim();
                int choice = input.isEmpty() ? -1 : Integer.parseInt(input);

                switch (choice) {
                    case 1 -> client.sendMessage("REGISTER " + client.requestId++ + " Bob buyer 127.0.0.1 " + client.socket.getLocalPort() + " 7002");
                    case 2 -> client.sendMessage("LIST_ITEM " + client.requestId++ + " Camera NikonD750 500 5d");
                    case 3 -> client.sendMessage("SUBSCRIBE " + client.requestId++ + " Camera");
                    case 4 -> client.sendMessage("BID " + client.requestId++ + " Camera 550");
                    case 5 -> client.sendMessage("DE-REGISTER " + client.requestId++ + " Bob");
                    case 0 -> {
                        client.close();
                        System.out.println("Disconnected.");
                        return;
                    }
                    default -> System.out.println("Invalid option.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
