import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Client {
    private static final int BUFFER_SIZE = 1024;
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_UDP_PORT = 5000;
    
    private String clientName;
    private String role;
    private String clientIP;
    private int udpPort;
    private int tcpPort;
    private DatagramSocket udpSocket;
    private ServerSocket tcpServerSocket;
    private boolean isRegistered = false;
    private int requestCount = 0;
    
    // Maps to track active subscriptions and items
    private final Map<String, String> subscriptions = new ConcurrentHashMap<>(); // item -> requestId
    private final Map<String, AuctionItem> activeItems = new ConcurrentHashMap<>(); // requestId -> item details
    private final Map<String, String> myListings = new ConcurrentHashMap<>(); // item -> requestId
    private final Map<String, Double> highestBids = new ConcurrentHashMap<>(); // item -> highest bid
    
    private ExecutorService executor;
    private Scanner scanner;
    
    public Client() {
        scanner = new Scanner(System.in);
        executor = Executors.newCachedThreadPool();
    }
    
    public static void main(String[] args) {
        Client client = new Client();
        client.start();
    }
    
    private void start() {
        System.out.println("P2P Auction System Client");
        System.out.println("=========================");
        
        // Setup client
        setupClient();
        
        if (setupNetworking()) {
            // Start UDP thread
            startUDPListener();
            
            // Start TCP thread
            startTCPListener();
            
            // Register with server
            if (registerWithServer()) {
                // Main menu loop
                showMenu();
            }
        }
        
        cleanup();
    }
    
    private void setupClient() {
        System.out.print("Enter your username: ");
        clientName = scanner.nextLine();
        
        System.out.print("Are you a buyer, seller, or both? (b/s/both): ");
        String roleInput = scanner.nextLine().toLowerCase();
        role = roleInput.equals("b") ? "buyer" : 
              roleInput.equals("s") ? "seller" : "both";
        
        // localhost or ask for IP
        clientIP = "127.0.0.1";
        System.out.print("Enter your IP address (press Enter for default 127.0.0.1): ");
        String inputIP = scanner.nextLine();
        if (!inputIP.isEmpty()) {
            clientIP = inputIP;
        }
        
      
        udpPort = 0; 
        tcpPort = 0; 
    }
    
    private boolean setupNetworking() {
        try {
            // Create UDP
            udpSocket = new DatagramSocket();
            udpPort = udpSocket.getLocalPort();
            
            // Create TCP 
            tcpServerSocket = new ServerSocket(0);
            tcpPort = tcpServerSocket.getLocalPort();
            
            System.out.println("UDP Port: " + udpPort);
            System.out.println("TCP Port: " + tcpPort);
            
            return true;
        } catch (IOException e) {
            System.err.println("Error setting up network connections: " + e.getMessage());
            return false;
        }
    }
    
    private void startUDPListener() {
        executor.submit(() -> {
            try {
                byte[] receiveBuffer = new byte[BUFFER_SIZE];
                
                while (!executor.isShutdown()) {
                    DatagramPacket packet = new DatagramPacket(receiveBuffer, BUFFER_SIZE);
                    
                    try {
                        udpSocket.receive(packet);
                        String message = new String(packet.getData(), 0, packet.getLength());
                        
                        System.out.println("\n[UDP] Received: " + message);
                        processUDPMessage(message);
                        
                    } catch (IOException e) {
                        if (!udpSocket.isClosed()) {
                            System.err.println("Error receiving UDP message: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("UDP listener error: " + e.getMessage());
            }
        });
    }
    
    private void startTCPListener() {
        executor.submit(() -> {
            try {
                while (!executor.isShutdown() && !tcpServerSocket.isClosed()) {
                    try {
                        Socket clientSocket = tcpServerSocket.accept();
                        
                        //Separate thread
                        executor.submit(() -> handleTCPConnection(clientSocket));
                        
                    } catch (IOException e) {
                        if (!tcpServerSocket.isClosed()) {
                            System.err.println("Error accepting TCP connection: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("TCP listener error: " + e.getMessage());
            }
        });
    }
    
    private void handleTCPConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            String message = in.readLine();
            System.out.println("\n[TCP] Received: " + message);
            
            String response = processTCPMessage(message, out);
            
            if (response != null) {
                out.println(response);
                System.out.println("[TCP] Sent: " + response);
            }
            
        } catch (IOException e) {
            System.err.println("Error handling TCP connection: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }
    
    private boolean registerWithServer() {

        String requestId = generateRequestId();
        
        String message = "REGISTER " + requestId + " " + clientName + " " + role + 
                         " " + clientIP + " " + udpPort + " " + tcpPort;
        
        try {

            System.out.println("[UDP] Sending: " + message);
            String response = sendUDPRequest(message);
            
            if (response.startsWith("REGISTERED")) {
                System.out.println("Successfully registered with server!");
                isRegistered = true;
                return true;
            } else if (response.startsWith("REGISTER-DENIED")) {
                System.out.println("Registration denied: " + response);
                return false;
            } else {
                System.out.println("Unexpected response: " + response);
                return false;
            }
            
        } catch (IOException e) {
            System.err.println("Error registering with server: " + e.getMessage());
            return false;
        }
    }
    
    private void showMenu() {
        boolean running = true;
        
        while (running && isRegistered) {
            System.out.println("\n==== P2P Auction System Menu ====");
            System.out.println("1. List an item for auction (Seller)");
            System.out.println("2. Subscribe to an item (Buyer)");
            System.out.println("3. Place a bid on an item (Buyer)");
            System.out.println("4. View my active subscriptions");
            System.out.println("5. View my listed items");
            System.out.println("6. De-register from server");
            System.out.println("7. Exit");
            System.out.print("Select an option: ");
            
            String option = scanner.nextLine();
            
            switch (option) {
                case "1":
                    if (role.equals("seller") || role.equals("both")) {
                        listItem();
                    } else {
                        System.out.println("You must be a seller to list items!");
                    }
                    break;
                case "2":
                    if (role.equals("buyer") || role.equals("both")) {
                        subscribeToItem();
                    } else {
                        System.out.println("You must be a buyer to subscribe to items!");
                    }
                    break;
                case "3":
                    if (role.equals("buyer") || role.equals("both")) {
                        placeBid();
                    } else {
                        System.out.println("You must be a buyer to place bids!");
                    }
                    break;
                case "4":
                    viewSubscriptions();
                    break;
                case "5":
                    viewListedItems();
                    break;
                case "6":
                    deregisterFromServer();
                    running = false;
                    break;
                case "7":
                    running = false;
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        }
    }
    
    private void listItem() {
        try {
            System.out.print("Enter item name: ");
            String itemName = scanner.nextLine();
            
            System.out.print("Enter item description: ");
            String description = scanner.nextLine().replace(' ', '_');
            
            System.out.print("Enter starting price: ");
            double startPrice = Double.parseDouble(scanner.nextLine());
            
            System.out.print("Enter auction duration in seconds: ");
            String duration = scanner.nextLine() + "s";
            
            String requestId = generateRequestId();
            String message = "LIST_ITEM " + requestId + " " + itemName + " " + 
                             description + " " + startPrice + " " + duration;
            
            System.out.println("[UDP] Sending: " + message);
            String response = sendUDPRequest(message);
            
            if (response.startsWith("ITEM_LISTED")) {
                System.out.println("Item successfully listed for auction!");
                myListings.put(itemName, requestId);
            } else {
                System.out.println("Failed to list item: " + response);
            }
            
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format. Please try again.");
        } catch (IOException e) {
            System.err.println("Error listing item: " + e.getMessage());
        }
    }
    
    private void subscribeToItem() {
        try {
            System.out.print("Enter item name to subscribe to: ");
            String itemName = scanner.nextLine();
            
            String requestId = generateRequestId();
            String message = "SUBSCRIBE " + requestId + " " + itemName;
            
            System.out.println("[UDP] Sending: " + message);
            String response = sendUDPRequest(message);
            
            if (response.startsWith("SUBSCRIBED")) {
                System.out.println("Successfully subscribed to " + itemName);
                subscriptions.put(itemName, requestId);
            } else {
                System.out.println("Failed to subscribe: " + response);
            }
            
        } catch (IOException e) {
            System.err.println("Error subscribing to item: " + e.getMessage());
        }
    }
    
    private void placeBid() {
        if (subscriptions.isEmpty()) {
            System.out.println("You haven't subscribed to any items yet!");
            return;
        }
        
        try {
            System.out.println("Your active subscriptions:");
            for (Map.Entry<String, AuctionItem> entry : activeItems.entrySet()) {
                AuctionItem item = entry.getValue();
                System.out.println("- " + item.name + " (Current price: $" + item.currentPrice + ")");
            }
            
            System.out.print("Enter item name to bid on: ");
            String itemName = scanner.nextLine();
            
            boolean found = false;
            String requestId = null;
            double currentPrice = 0.0;
            
            for (Map.Entry<String, AuctionItem> entry : activeItems.entrySet()) {
                AuctionItem item = entry.getValue();
                if (item.name.equals(itemName)) {
                    found = true;
                    requestId = entry.getKey();
                    currentPrice = item.currentPrice;
                    break;
                }
            }
            
            if (!found) {
                System.out.println("You haven't received auction announcements for this item!");
                return;
            }
            
            System.out.println("Current highest bid: $" + currentPrice);
            System.out.print("Enter your bid amount: ");
            double bidAmount = Double.parseDouble(scanner.nextLine());
            
            if (bidAmount <= currentPrice) {
                System.out.println("Bid must be higher than current price!");
                return;
            }
            
            String message = "BID " + requestId + " " + itemName + " " + bidAmount;
            
            System.out.println("[UDP] Sending: " + message);
            String response = sendUDPRequest(message);
            
            if (response.startsWith("BID_ACCEPTED")) {
                System.out.println("Bid accepted!");
            } else {
                System.out.println("Bid rejected: " + response);
            }
            
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format. Please try again.");
        } catch (IOException e) {
            System.err.println("Error placing bid: " + e.getMessage());
        }
    }
    
    private void viewSubscriptions() {
        System.out.println("\n==== Your Subscriptions ====");
        
        if (subscriptions.isEmpty()) {
            System.out.println("You haven't subscribed to any items yet.");
        } else {
            for (Map.Entry<String, String> entry : subscriptions.entrySet()) {
                System.out.println("- Item: " + entry.getKey() + ", Request ID: " + entry.getValue());
            }
        }
        
        System.out.println("\n==== Active Auctions ====");
        
        if (activeItems.isEmpty()) {
            System.out.println("You don't have any active auctions.");
        } else {
            for (Map.Entry<String, AuctionItem> entry : activeItems.entrySet()) {
                AuctionItem item = entry.getValue();
                System.out.println("- Item: " + item.name);
                System.out.println("  Description: " + item.description.replace('_', ' '));
                System.out.println("  Current Price: $" + item.currentPrice);
                System.out.println("  Time Left: " + item.timeLeft);
                System.out.println("  Request ID: " + entry.getKey());
                System.out.println();
            }
        }
    }
    
    private void viewListedItems() {
        System.out.println("\n==== Your Listed Items ====");
        
        if (myListings.isEmpty()) {
            System.out.println("You haven't listed any items yet.");
        } else {
            for (Map.Entry<String, String> entry : myListings.entrySet()) {
                System.out.println("- Item: " + entry.getKey() + ", Request ID: " + entry.getValue());
            }
        }
    }
    
    private void deregisterFromServer() {
        try {
            String requestId = generateRequestId();
            String message = "DE-REGISTER " + requestId + " " + clientName;
            
            System.out.println("[UDP] Sending: " + message);
            sendUDPRequest(message);
            
            System.out.println("Deregistered from server.");
            isRegistered = false;
            
        } catch (IOException e) {
            System.err.println("Error deregistering from server: " + e.getMessage());
        }
    }
    
    private void processUDPMessage(String message) {
        String[] parts = message.split(" ");
        String command = parts[0];
        String requestId = parts[1];
        
        switch (command) {
            case "AUCTION_ANNOUNCE":
                handleAuctionAnnounce(parts);
                break;
            case "BID_UPDATE":
                handleBidUpdate(parts);
                break;
            case "PRICE_ADJUSTMENT":
                handlePriceAdjustment(parts);
                break;
            default:
                System.out.println("Unknown UDP message type: " + command);
        }
        
        System.out.print("\nSelect an option: ");
    }
    
    private String processTCPMessage(String message, PrintWriter out) {
        String[] parts = message.split(" ");
        String command = parts[0];
        String requestId = parts[1];
        
        switch (command) {
            case "WINNER":
                return handleWinnerMessage(parts);
            case "SOLD":
                return handleSoldMessage(parts);
            case "NON_OFFER":
                return handleNonOfferMessage(parts);
            case "INFORM_Req":
                return handleInformRequest(parts);
            case "NEGOTIATE_REQ":
                return handleNegotiateRequest(parts);
            case "SHIPPING_INFO":
                return handleShippingInfo(parts);
            default:
                System.out.println("Unknown TCP message type: " + command);
                return null;
        }
    }
    
    private void handleAuctionAnnounce(String[] parts) {
        String requestId = parts[1];
        String itemName = parts[2];
        String description = parts[3];
        double currentPrice = Double.parseDouble(parts[4]);
        String timeLeft = parts[5];
        
        AuctionItem item = new AuctionItem(itemName, description, currentPrice, timeLeft);
        activeItems.put(requestId, item);
        
        System.out.println("\n[AUCTION ANNOUNCEMENT] Item: " + itemName);
        System.out.println("Description: " + description.replace('_', ' '));
        System.out.println("Current Price: $" + currentPrice);
        System.out.println("Time Left: " + timeLeft);
    }
    
    private void handleBidUpdate(String[] parts) {
        String requestId = parts[1];
        String itemName = parts[2];
        double highestBid = Double.parseDouble(parts[3]);
        String bidderName = parts[4];
        String timeLeft = parts[5];
        
        if (activeItems.containsKey(requestId)) {
            AuctionItem item = activeItems.get(requestId);
            item.currentPrice = highestBid;
            item.timeLeft = timeLeft;
            activeItems.put(requestId, item);
        }
        
        if (myListings.containsValue(requestId)) {
            System.out.println("\n[BID UPDATE] Your item " + itemName + " received a bid!");
            System.out.println("Highest Bid: $" + highestBid + " by " + bidderName);
            System.out.println("Time Left: " + timeLeft);
        } else {
            System.out.println("\n[BID UPDATE] Item: " + itemName);
            System.out.println("Highest Bid: $" + highestBid + " by " + bidderName);
            System.out.println("Time Left: " + timeLeft);
            
            highestBids.put(itemName, highestBid);
        }
    }
    
    private void handlePriceAdjustment(String[] parts) {
        String requestId = parts[1];
        String itemName = parts[2];
        double newPrice = Double.parseDouble(parts[3]);
        String timeLeft = parts[4];
        
        if (activeItems.containsKey(requestId)) {
            AuctionItem item = activeItems.get(requestId);
            item.currentPrice = newPrice;
            item.timeLeft = timeLeft;
            activeItems.put(requestId, item);
        }
        
        System.out.println("\n[PRICE ADJUSTMENT] Item: " + itemName);
        System.out.println("New Price: $" + newPrice);
        System.out.println("Time Left: " + timeLeft);
    }
    
    private String handleWinnerMessage(String[] parts) {
        String requestId = parts[1];
        String itemName = parts[2];
        double finalPrice = Double.parseDouble(parts[3]);
        String sellerName = parts[4];
        
        System.out.println("\n[CONGRATULATIONS] You won the auction for: " + itemName);
        System.out.println("Final Price: $" + finalPrice);
        System.out.println("Seller: " + sellerName);
        System.out.println("Please wait for payment instructions...");
        
        return null; 
    }
    
    private String handleSoldMessage(String[] parts) {
        String requestId = parts[1];
        String itemName = parts[2];
        double finalPrice = Double.parseDouble(parts[3]);
        String buyerName = parts[4];
        
        System.out.println("\n[ITEM SOLD] Your item has been sold: " + itemName);
        System.out.println("Final Price: $" + finalPrice);
        System.out.println("Buyer: " + buyerName);
        System.out.println("Please wait for payment processing...");
        
        return null; 
    }
    
    private String handleNonOfferMessage(String[] parts) {
        String requestId = parts[1];
        String itemName = parts[2];
        
        System.out.println("\n[NO OFFERS] Your item received no bids: " + itemName);
        //Fnction i dont really know....
        myListings.entrySet().removeIf(entry -> entry.getValue().equals(requestId));
        
        return null; 
    }
    
    private String handleInformRequest(String[] parts) {
        String requestId = parts[1];
        String itemName = parts[2];
        double finalPrice = Double.parseDouble(parts[3]);
        
        System.out.println("\n[PAYMENT INFO REQUEST] For item: " + itemName);
        System.out.println("Price: $" + finalPrice);
        
        // Get payment info from user
        System.out.print("Enter your credit card number: ");
        String creditCard = scanner.nextLine();
        
        System.out.print("Enter credit card expiration date (MM/YY): ");
        String expDate = scanner.nextLine();
        
        System.out.print("Enter your shipping address: ");
        String address = scanner.nextLine();
        
        // Construct and return response
        return "INFORM_Res " + requestId + " " + clientName + " " + creditCard + " " + expDate + " " + address;
    }
    
    private String handleNegotiateRequest(String[] parts) {
        String requestId = parts[1];
        String itemName = parts[2];
        double currentPrice = Double.parseDouble(parts[3]);
        String timeLeft = parts[4];
        
        System.out.println("\n[NEGOTIATION REQUEST] For your item: " + itemName);
        System.out.println("Current Price: $" + currentPrice);
        System.out.println("Time Left: " + timeLeft);
        System.out.println("No bids have been placed yet. Would you like to lower the price?");
        System.out.print("Enter Y to accept negotiation or N to refuse: ");
        
        String response = scanner.nextLine();
        
        if (response.equalsIgnoreCase("Y")) {
            System.out.print("Enter new price: ");
            double newPrice = Double.parseDouble(scanner.nextLine());
            
            return "ACCEPT " + requestId + " " + itemName + " " + newPrice;
        } else {
            return "REFUSE " + requestId + " " + itemName + " REJECT";
        }
    }
    
    private String handleShippingInfo(String[] parts) {
        String requestId = parts[1];
        String buyerName = parts[2];
        String buyerAddress = parts[3];
        
        System.out.println("\n[SHIPPING INFO] Buyer: " + buyerName);
        System.out.println("Shipping Address: " + buyerAddress);
        System.out.println("Please ship the item to the address above.");
        
        return null;
    }
    
    private String sendUDPRequest(String message) throws IOException {
        byte[] sendBuffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(
            sendBuffer, sendBuffer.length, 
            InetAddress.getByName(SERVER_IP), SERVER_UDP_PORT
        );
        
        udpSocket.send(packet);
        
        // Wait for response
        byte[] receiveBuffer = new byte[BUFFER_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, BUFFER_SIZE);
        
        udpSocket.receive(receivePacket);
        
        String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
        System.out.println("[UDP] Received: " + response);
        
        return response;
    }
    
    private String generateRequestId() {
        return String.valueOf(1000 + (++requestCount));
    }
    
    private void cleanup() {

        if (isRegistered) {
            try {
                deregisterFromServer();
            } catch (Exception e) {
                System.err.println("Error during deregistration: " + e.getMessage());
            }
        }
        
     
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        
        if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
            try {
                tcpServerSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing TCP server socket: " + e.getMessage());
            }
        }
        
        System.out.println("Client shutdown complete.");
    }
  
    private static class AuctionItem {
        String name;
        String description;
        double currentPrice;
        String timeLeft;
        
        public AuctionItem(String name, String description, double currentPrice, String timeLeft) {
            this.name = name;
            this.description = description;
            this.currentPrice = currentPrice;
            this.timeLeft = timeLeft;
        }
    }
}
