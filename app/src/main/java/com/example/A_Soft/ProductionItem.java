package com.example.A_Soft;

public class ProductionItem {
    private String serialNo;
    private String itemCode;
    private String materialName;
    private String type;

    public ProductionItem(String serialNo, String itemCode, String materialName, String type) {
        this.serialNo = serialNo;
        this.itemCode = itemCode;
        this.materialName = materialName;
        this.type = type;
    }

    // Add getters
    public String getSerialNo() { return serialNo; }
    public String getItemCode() { return itemCode; }
    public String getMaterialName() { return materialName; }
    public String getType() { return type; }
}
