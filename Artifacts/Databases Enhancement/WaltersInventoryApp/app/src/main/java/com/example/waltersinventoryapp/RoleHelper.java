package com.example.waltersinventoryapp;

/*
 * Includes role checks that exist within the main activity to ensure the user has proper access
 * to make certain changes to item information.
 *
 * Emily Walters
 * CS499 Computer Science Capstone
 * Southern New Hampshire University
 */

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
