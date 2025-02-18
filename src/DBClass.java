/**
 * DBClass - CSV-based storage for the Peer-to-Peer Auction System (P2PAS).
 * Handles auction item storage, retrieval, and persistence via CSV.
 * Ensures data consistency with setters, getters, and file operations.
 *
 * Author: [Hagop Minassian] | Date: [2025/02/16]
 * Course: COEN 366, Concordia University
 */


import java.io.*;
import java.util.*;

public class DBClass {
    private String filePath;
    private List<Map<String, String>> records;
    private final String[] headers = {"Item_Name", "Item_Description", "Start_Price", "Duration"};

    public DBClass(String filePath) {
        this.filePath = filePath;
        this.records = new ArrayList<>();
        loadFromCSV();
    }

    // Load data from CSV file
    private void loadFromCSV() {
        File file = new File(filePath);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            br.readLine(); // Skip header line
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length == headers.length) {
                    Map<String, String> record = new HashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        record.put(headers[i], values[i]);
                    }
                    records.add(record);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Save data to CSV file
    private void saveToCSV() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            bw.write(String.join(",", headers) + "\n"); // Write header
            for (Map<String, String> record : records) {
                bw.write(String.join(",", record.values()) + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Add new auction item
    public void addItem(String itemName, String description, String startPrice, String duration) {
        Map<String, String> record = new HashMap<>();
        record.put("Item_Name", itemName);
        record.put("Item_Description", description);
        record.put("Start_Price", startPrice);
        record.put("Duration", duration);
        records.add(record);
        saveToCSV();
    }

    // Retrieve an item by name
    public Map<String, String> getItem(String itemName) {
        for (Map<String, String> record : records) {
            if (record.get("Item_Name").equals(itemName)) {
                return record;
            }
        }
        return null;
    }

    // Remove an item by name
    public boolean removeItem(String itemName) {
        boolean removed = records.removeIf(record -> record.get("Item_Name").equals(itemName));
        if (removed) saveToCSV();
        return removed;
    }

    // List all items
    public List<Map<String, String>> listItems() {
        return new ArrayList<>(records);
    }
/* 
    public static void main(String[] args) {
        DBClass db = new DBClass("auction_data.csv");

        // Example usage
        db.addItem("Laptop", "Gaming laptop", "1000", "7 days");
        System.out.println(db.getItem("Laptop"));
        db.removeItem("Laptop");
        System.out.println(db.listItems());
    }
        */
}
