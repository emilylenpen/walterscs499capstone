package com.example.waltersinventoryapp;

/*
 * A POJO class for Item objects with information including the item ID, name, amount,
 * and category. Contains class constructor as well as getters and setters.
 *
 * Emily Walters
 * CS499 Computer Science Capstone
 * Southern New Hampshire University
 */

public class Item {
    private String itemId;
    private String name;
    private int amount;
    private String category;

    // Object initialization
    public Item(String id, String name, int amount, String category) {
        this.itemId = id;
        this.name = name;
        this.amount = amount;
        this.category = category;
    }

    // Getters
    public String getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public int getAmount() {
            return amount;
    }

    // Setters
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
