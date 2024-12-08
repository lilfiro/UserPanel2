package com.example.A_Soft;

import java.util.ArrayList;
import java.util.List;

// ProductionReceipt.java
public class ProductionReceipt {
    private String receiptNo;
    private String date;
    private List<ProductionItem> items;
    private String status; // "DRAFT" or "COMPLETED"

    public ProductionReceipt(String receiptNo, String date) {
        this.receiptNo = receiptNo;
        this.date = date;
        this.items = new ArrayList<>();
        this.status = "DEVAM EDİYOR";
    }

    // Add getters and setters
    public String getReceiptNo() { return receiptNo; }
    public String getDate() { return date; }
    public List<ProductionItem> getItems() { return items; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
