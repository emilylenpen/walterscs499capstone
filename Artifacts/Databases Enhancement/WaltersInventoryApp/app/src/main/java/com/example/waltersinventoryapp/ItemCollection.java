package com.example.waltersinventoryapp;

/*
 * A POJO class for ItemCollection objects that include the collection ID, name, and users
 * that are allowed to access an item collection (or inventory). Contains getters and setters
 * for the class alongside an overloaded method with allowed users, otherwise blank.
 *
 * Emily Walters
 * CS499 Computer Science Capstone
 * Southern New Hampshire University
 */

import java.util.List;

public class ItemCollection {
    private String collectionId;
    private String name;
    private List<String> allowedUsers;

    // Default initialization
    public ItemCollection(String collectionId, String name) {
        this.collectionId = collectionId;
        this.name = name;
    }

    // Overload with allowed users
    public ItemCollection(String collectionId, String name, List<String> allowedUsers) {
        this.collectionId = collectionId;
        this.name = name;
        this.allowedUsers = allowedUsers;
    }

    // Gettersss
    public String getCollectionId() {
        return collectionId;
    }

    public String getName() {
        return name;
    }

    public List<String> getAllowedUsers() {
        return allowedUsers;
    }

    // Setters!
    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAllowedUsers(List<String> allowedUsers) {
        this.allowedUsers = allowedUsers;
    }
}

