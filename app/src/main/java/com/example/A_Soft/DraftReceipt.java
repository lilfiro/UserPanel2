package com.example.A_Soft;

public class DraftReceipt {
    private String date;
    private String carPlate;
    private String receiptNo;
    private String status;

    // Constructor to initialize the DraftReceipt object
    private String oprFicheNo;

    public DraftReceipt(String date, String carPlate, String receiptNo, String status, String oprFicheNo) {
        this.date = date;
        this.carPlate = carPlate;
        this.receiptNo = receiptNo;
        this.status = status;
        this.oprFicheNo = oprFicheNo;
    }

    public String getOprFicheNo() {
        return oprFicheNo;
    }

    public void setOprFicheNo(String oprFicheNo) {
        this.oprFicheNo = oprFicheNo;
    }


    // Getters and setters for each field (optional)
    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getCarPlate() {
        return carPlate;
    }

    public void setCarPlate(String carPlate) {
        this.carPlate = carPlate;
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
}
