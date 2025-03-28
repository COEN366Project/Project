import java.net.*;
import java.util.Scanner;

public class TestClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 5000;
    private static final int CLIENT_PORT = 6001; // Unique UDP port for this client

    private DatagramSocket socket;
    private InetAddress serverAddress;

    public TestClient() throws Exception {
        socket = new DatagramSocket(CLIENT_PORT);
        serverAddress = InetAddress.getByName(SERVER_IP);
    }

    public void sendMessage(String message) throws Exception {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
        socket.send(packet);

        // Receive response
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
            TestClient client = new TestClient();
            Scanner scanner = new Scanner(System.in);

            System.out.println("Connected to server. Choose a command:");
            System.out.println("1. Register");
            System.out.println("2. List Item");
            System.out.println("3. Subscribe");
            System.out.println("4. Bid");
            System.out.println("5. De-Register");
            System.out.println("0. Exit");

            while (true) {
                System.out.print("\nEnter choice: ");
                int choice = Integer.parseInt(scanner.nextLine());

                switch (choice) {
                    case 1 -> client.sendMessage("REGISTER 101 Alice buyer 127.0.0.1 6001 7001");
                    case 2 -> client.sendMessage("LIST_ITEM 102 Macbook PowerfulLaptop 800 10d");
                    case 3 -> client.sendMessage("SUBSCRIBE 103 Macbook");
                    case 4 -> client.sendMessage("BID 104 Macbook 850");
                    case 5 -> client.sendMessage("DE-REGISTER 105 Alice");
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
