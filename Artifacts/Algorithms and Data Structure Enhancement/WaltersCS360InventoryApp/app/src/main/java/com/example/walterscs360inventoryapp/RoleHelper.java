package com.example.walterscs360inventoryapp;

// Defines what actions are allowed dependent on the user's role
public class RoleHelper {
    public static boolean canAddItems(String role) {
        return role.equals("admin");
    }

    public static boolean canEditItems(String role) {
        return role.equals("admin") || role.equals("user");
    }

    public static boolean canDeleteItems(String role){
        return role.equals("admin");
    }

    public static boolean canViewItems(String role){
        return role.equals("admin") || role.equals("user") || role.equals("viewer"); // There is no limitation for viewing items
    }
}
