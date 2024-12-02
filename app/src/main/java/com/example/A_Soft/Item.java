package com.example.A_Soft;

public class Item {
    private String name;
    private int quantity;
    private String description;

    public Item() {
    }

    // Existing constructor with three parameters
    public Item(String name, int quantity, String description) {
        this.name = name;
        this.quantity = quantity;
        this.description = description;
    }

    // New constructor with two parameters
    public Item(String name, String description) {
        this.name = name;
        this.quantity = 0; // Default value for quantity
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
