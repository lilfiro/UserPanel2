package com.example.A_Soft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DraftData {
    String receiptNo;
    Set<String> scannedSerials;
    Map<String, Integer> scannedItemCounts;
    Map<String, Integer> itemQuantities;
    Map<String, String> itemNames;
    // Add a list to store all scanned QR items with complete data
    List<ScannedQRDetails> scannedQRDetails;

    // Add this inner class to store all relevant details for each scanned QR item
    public static class ScannedQRDetails {
        String serialNumber;
        String itemCode;
        String kareKodNo;
        Integer shipPlanLineId;

        public ScannedQRDetails(String serialNumber, String itemCode, String kareKodNo, Integer shipPlanLineId) {
            this.serialNumber = serialNumber;
            this.itemCode = itemCode;
            this.kareKodNo = kareKodNo;
            this.shipPlanLineId = shipPlanLineId;
        }
    }

    // Default constructor for Gson deserialization
    public DraftData() {
        this.receiptNo = "";
        this.scannedSerials = new HashSet<>();
        this.scannedItemCounts = new HashMap<>();
        this.itemQuantities = new HashMap<>();
        this.itemNames = new HashMap<>();
        this.scannedQRDetails = new ArrayList<>();
    }

    public DraftData(String receiptNo, Set<String> scannedSerials,
                     Map<String, Integer> scannedItemCounts,
                     Map<String, Integer> itemQuantities,
                     Map<String, String> itemNames,
                     List<ScannedQRDetails> scannedQRDetails) {
        this.receiptNo = receiptNo;
        this.scannedSerials = scannedSerials != null ? scannedSerials : new HashSet<>();
        this.scannedItemCounts = scannedItemCounts != null ? scannedItemCounts : new HashMap<>();
        this.itemQuantities = itemQuantities != null ? itemQuantities : new HashMap<>();
        this.itemNames = itemNames != null ? itemNames : new HashMap<>();
        this.scannedQRDetails = scannedQRDetails != null ? scannedQRDetails : new ArrayList<>();
    }
}
