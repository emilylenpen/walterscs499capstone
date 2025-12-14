package com.example.walterscs360inventoryapp;

// Implementation of a separate user class to mediate establishing and getting/setting values.
public class User {
    private int userId;
    private String username;
    private String password; // will be obsolete once we implement Firebase; just for testing system
    private String role;

    public User(int id, String username, String password, String role) {
        this.userId = id;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public void setUserId(int id) {
        this.userId = id;
    }

    public void setUsername (String username) {
        this.username = username;
    }

    public void setPassword (String password) {
        this.password = password;
    }

    public void setRole (String role) {
        this.role = role;
    }
}
