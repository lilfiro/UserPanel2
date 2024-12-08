package com.example.A_Soft;

public class DraftReceipt {
    private String date, materialCode, materialName, amount, receiptNo, status, oprFicheNo, carPlate;

    public DraftReceipt(String materialName, String amount) {
        this.materialName = materialName;
        this.amount = amount;
        this.date = null;
        this.materialCode = null;
        this.receiptNo = null;
        this.status = null;
        this.oprFicheNo = null;
    }

    public DraftReceipt(String date, String amount, String status, String oprFicheNo, String carPlate, String receiptNo) {
        this.date = date;
        this.amount = amount;
        this.status = status;
        this.oprFicheNo = oprFicheNo;
        this.carPlate = carPlate;
        this.receiptNo = receiptNo;
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
    public String getCarPlate() {
        return carPlate;
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
