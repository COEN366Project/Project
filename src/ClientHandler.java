import java.net.*;
import java.io.*;

public class ClientHandler {
    private String name;
    private String role;
    private String userIP; 
    private int serverPort;
    private int udpPort;
    private int tcpPort;
    private DatagramSocket socket;
    private static int requestId = 1;

    public ClientHandler(String name, String role, String userIP, int serverPort, int udpPort, int tcpPort) throws SocketException {
        this.name = name;
        this.role = role;
        this.userIP = userIP;
        this.serverPort = serverPort;
        this.udpPort = udpPort;
        this.tcpPort = tcpPort;
        this.socket = new DatagramSocket(udpPort);
    }
    //Format: REGISTER | RQ# | Name | Role | IP_Addr | UDP_SOCKET | TCP_SOCKET# 
    public String register() throws IOException {
        String message = "REGISTER " + requestId + " " + name + " " + role + " " + userIP + " " + udpPort + " " + tcpPort;
        return sendMessage(message);
    }

    public String deregister() throws IOException {
        String message = "DE-REGISTER " + requestId + " " + name;
        return sendMessage(message);
    }

    private String sendMessage(String message) throws IOException {
        byte[] buffer = message.getBytes();
        InetAddress address = InetAddress.getByName(userIP);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, serverPort);
        socket.send(packet);

        byte[] responseBuffer = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
        socket.receive(responsePacket);

        return new String(responsePacket.getData(), 0, responsePacket.getLength());
    }

    public static void main(String[] args) {
        try {
            ClientHandler client = new ClientHandler("Alice", "buyer", "127.0.0.1", 5000, 6000, 7000);
            System.out.println(client.register());
            ClientHandler client2 = new ClientHandler("Alice", "buyer", "127.0.0.1", 5000, 6000, 7000);
            System.out.println(client2.register());
            System.out.println(client.deregister());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
