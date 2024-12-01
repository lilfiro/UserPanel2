package com.example.A_Soft;

public class DraftReceipt {
    private String date;
    private String materialCode;
    private String materialName;
    private String amount;
    private String receiptNo;
    private String status;
    private String oprFicheNo;

    // Updated constructor to match the query results
// Updated constructor
    public DraftReceipt(String date, String materialCode, String materialName,
                        String amount, String receiptNo, String status, String oprFicheNo) {
        this.date = date;
        this.materialCode = materialCode;
        this.materialName = materialName;
        this.amount = amount;
        this.receiptNo = receiptNo;
        this.status = status;
        this.oprFicheNo = oprFicheNo;
    }

    // Getters and setters
    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOprFicheNo() {
        return oprFicheNo;
    }

    public void setOprFicheNo(String oprFicheNo) {
        this.oprFicheNo = oprFicheNo;
    }
}