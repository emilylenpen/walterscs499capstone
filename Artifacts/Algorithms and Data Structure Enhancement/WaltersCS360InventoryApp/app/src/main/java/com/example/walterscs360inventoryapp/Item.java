package com.example.walterscs360inventoryapp;

public class Item {
    private String itemId;
    private String name;
    private int amount;
    private String category;

    public Item(String id, String name, int amount, String category) {
        this.itemId = id;
        this.name = name;
        this.amount = amount;
        this.category = category;
    }

    public String getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public int getAmount() {
            return amount;
    }

    public String getCategory() {
        return category;
    }

    public void setUserId(String id) {
        this.itemId = id;
    }

    public void setName (String name) {
        this.name = name;
    }

    public void setAmount (int amount) {
        this.amount = amount;
    }

    public void setCategory (String category) {
        this.category = category;
    }

}
