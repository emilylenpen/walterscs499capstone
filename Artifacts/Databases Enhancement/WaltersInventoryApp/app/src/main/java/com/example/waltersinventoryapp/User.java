package com.example.waltersinventoryapp;

/*
 * A POJO class for User objects  with information including the user ID, username (email), and
 * role. Contains class constructor as well as getters and setters.
 *
 * Emily Walters
 * CS499 Computer Science Capstone
 * Southern New Hampshire University
 *
 */

// Implementation of a separate user class to mediate establishing and getting/setting values.
public class User {
    private String userId;
    private String username;
    private String role;

    public User(String uid, String username, String role) {
        this.userId = uid;
        this.username = username;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public void setUserId(String uid) {
        this.userId = uid;
    }

    public void setUsername (String username) {
        this.username = username;
    }

    public void setRole (String role) {
        this.role = role;
    }
}
