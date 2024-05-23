package com.example.userpanel2;

public class Item {
    private String name;
    private int quantity;
    private String description;

    public Item() {
    }

    public Item(String name, int quantity, String description) {
        this.name = name;
        this.quantity = quantity;
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
