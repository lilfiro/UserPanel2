package com.example.A_Soft;

import java.util.Map;
import java.util.Set;

// Add this static class for draft data
public class DraftData {
    String receiptNo;
    Set<String> scannedSerials;
    Map<String, Integer> scannedItemCounts;
    Map<String, Integer> itemQuantities;
    Map<String, String> itemNames;

    public DraftData(String receiptNo, Set<String> scannedSerials,
                     Map<String, Integer> scannedItemCounts,
                     Map<String, Integer> itemQuantities,
                     Map<String, String> itemNames) {
        this.receiptNo = receiptNo;
        this.scannedSerials = scannedSerials;
        this.scannedItemCounts = scannedItemCounts;
        this.itemQuantities = itemQuantities;
        this.itemNames = itemNames;
    }
}
