import java.net.*;
import java.util.*;

public class Serveur {
	// Global variable
    private static final int SERVER_PORT = 5000; // Value chosen
    private static final int BUFFER_SIZE = 1024; // Value chosen
    private static Map<String, ClientInfo> clients = new HashMap<>(); // "Database" use for now until creation of database class
    
    public static void main(String[] args) {
    	// Open the socket using UDP DatagramSockets
        try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {
            System.out.println("Server is running on port " + SERVER_PORT);
            byte[] receiveBuffer = new byte[BUFFER_SIZE];
            
            while (true) {
            	// Receive packet variable creation
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, BUFFER_SIZE);
                
                // Receive the packet from the socket (serverSocket) to the packet (receivePacket)
                serverSocket.receive(receivePacket);
                
                // To string
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                
                // Process the response to send from the function processMessage()
                String response = processMessage(message);
                
                // response to byte (packet)
                byte[] sendBuffer = response.getBytes();
                
                // Send packet variable creation
                DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
                
             // Send the packet
                serverSocket.send(sendPacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Function to process the string that comes from the packet 
    private static String processMessage(String message) {
        // Data separated with spaces
    	String[] parts = message.split(" ");
        
        // Register has 7 parts and de-register has 3 parts
        if (parts.length < 2 ) {
            return "INVALID REQUEST";
        }

        // The two first variable received
        String command = parts[0];
        String requestId = parts[1];

        // If command is to register
        if (command.equals("REGISTER")) {
            
        	// Send invalid message using the require format for invalid request parameter
        	if (parts.length < 6) 
            	return "REGISTER-DENIED " + requestId + " Invalid Request Parameters";
            
            // The 5 other variable received
            String name = parts[2];
            String role = parts[3];
            String ip = parts[4];
            int udpPort = Integer.parseInt(parts[5]);
            int tcpPort = Integer.parseInt(parts[6]);
            
            // See map if it contain the name 
            if (clients.containsKey(name)) {
            	// Send invalid message using the require format for invalid name
                return "REGISTER-DENIED " + requestId + " Name Already Taken";
            }
            
            // if not add to the map 
            clients.put(name, new ClientInfo(name, role, ip, udpPort, tcpPort));
            
            //Send valid message using the require format for registering
            return "REGISTERED " + requestId;
        } 
        // if command is to de-register
        else if (command.equals("DE-REGISTER")) {
        	// Send invalid message using the require format for invalid request parameter
            if (parts.length < 3) return "INVALID REQUEST";
            
            // Get name and remove it from map
            String name = parts[2];
            clients.remove(name);
            
            //Send valid message using the require format for de-registering
            return "DE-REGISTERED " + requestId;
        }
        
        // when anything
        return "UNKNOWN COMMAND";
    }
}

// Class for client
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
