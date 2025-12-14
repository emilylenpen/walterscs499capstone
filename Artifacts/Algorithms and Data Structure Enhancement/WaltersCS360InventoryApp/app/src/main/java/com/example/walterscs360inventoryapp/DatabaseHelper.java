package com.example.walterscs360inventoryapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "inventory.db";
    private static final int DATABASE_VERSION = 1;

    public static final int SUCCESS = 1;
    public static final int FAILURE = 0;
    // public static final int USER_EXISTS = -1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Creation of two databases, one for items and one for user accounts.
    // Now includes a role value for users with the implementation of RBAC.
    // Also updated with a foreign key to connect users with entered inventory items.
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, password TEXT, role TEXT)");
        db.execSQL("CREATE TABLE inventory (item_id TEXT, name TEXT, amount INTEGER, category TEXT, user_id INTEGER, FOREIGN KEY(user_id) REFERENCES users(user_id))");
        int DATABASE_VERSION = 2;
    }

    // Allows for upgraded versions of DB; RBAC and other enhancements are in v2.
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        for (int version = oldVersion + 1; version <= newVersion; version++) {
            switch (version) {
                case 2:
                    // Drops old tables since they do not store valuable information and just
                    // contain old test data from previous submission-easier to dump than migrate.
                    db.execSQL("DROP TABLE IF EXISTS users");
                    db.execSQL("DROP TABLE IF EXISTS inventory");
                    onCreate(db);
                    break;
                case 3:
                    break;
            }
        }
    }

    // Creation of a new user in the database.
    // Used to be a boolean, now just returns int userId and is negative when fails.

    public int createUser(String username, String password, String role) {
        SQLiteDatabase db = getWritableDatabase(); // needs to be editable
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password", password);
        values.put("role", role);

        return (int) db.insert("users", null, values);
    }

    /* FIXME: Get rid of overloaded method that defaults to certain role.
     * We are going to implement user role changing into Firebase, so there is no reason
     * to introduce more complicated code that allows for user role selection in the application
     * as it will be a very brief period where Firebase does not exist and RBAC does. Until we
     * implement Firebase, I have a hardcoded "admin" or "user" value to test with. I used an
     * overloaded method so that I can easily set a default role without interfering with what
     * it is upon user creation like it would if i set a default role in the main activity.
     */
    public int createUser(String username, String password) {
        return createUser(username, password, "admin");
    }

    // Checks if a user exists within the database
    // Didn't use a cursor here with this simple check at this current time, may change-
    // will likely become obsolete with new method and can check for null with getUser().
    public boolean checkUser(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        long count = DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM users WHERE username=? AND password=?",
                new String[]{username, password});
        return count > 0;
    }

    // New method for passing information with new User class.
    public User getUser(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT user_id, username, password, role FROM users WHERE username=? AND password=?",
                new String[]{username, password}
        );

        if (cursor.moveToFirst()) {
            User user = new User(
                cursor.getInt(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3)
            );
            cursor.close();
            return user;
        }
        cursor.close();
        return null;
    }

    // Database CRUD operations; decided to just keep them in the DB class for simplicity.
    // Creates an item and adds it to the inventory
    public boolean addItem(String itemId, String name, int amount, String category, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("item_id", itemId);
        values.put("name", name);
        values.put("amount", amount);
        values.put("category", category);
        values.put("user_id", userId);
        return db.insert("inventory", null, values) != -1;
    }

    // Using a cursor to iterate and store the items to be returned to the user
//    public ArrayList<String[]> getItems(int userId) {
//        SQLiteDatabase db = getReadableDatabase();
//        ArrayList<String[]> items = new ArrayList<>();
//        Cursor cursor = db.rawQuery("SELECT item_id, name, amount FROM inventory WHERE user_id=?",
//                new String[]{String.valueOf(userId)});
//
//        while (cursor.moveToNext()) {
//            items.add(new String[]{
//                    cursor.getString(0),
//                    cursor.getString(1),
//                    cursor.getString(2)
//            });
//        }
//        cursor.close();
//        return items;
//    }

    // Retrieving items with newly implemented Item object
    public ArrayList<Item> getItemObjects (int userId) {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<Item> items = new ArrayList<>();

        Cursor cursor = db.rawQuery("SELECT item_id, name, amount, category FROM inventory WHERE user_id=?",
                new String[]{String.valueOf(userId)}
        );

        while (cursor.moveToNext()) {
            items.add(new Item(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getInt(2),
                    cursor.getString(3)
            ));
        }

        cursor.close();
        return items;
    }

    // Getting categories so we can add them to the AutoCompleteTextView
    public ArrayList<String> getCategories() {
        ArrayList<String> categories = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        // Only selects unique categories, no repeats.
        Cursor cursor = db.rawQuery("SELECT DISTINCT category FROM inventory", null);

        if (cursor.moveToFirst()) {
            do {
                String category = cursor.getString(0);
                if (category != null && !category.isEmpty()) {
                    categories.add(category);
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        return categories;
    }

    // Update item given the information input
    public boolean updateItem(String itemId, String name, int amount, String category, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("amount", amount);
        values.put("category", category);
        return db.update("inventory", values, "item_id=? AND user_id=?",
                new String[]{itemId, String.valueOf(userId)}) > 0;
    }

    // Delete an item from the inventory.
    // We implemented the delete item confirmation by using a snackbar in MainActivity.
    public boolean deleteItem(String itemId, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("inventory", "item_id=? AND user_id=?", new String[]{itemId, String.valueOf(userId)}) > 0;
    }

    // Iterate through items similar to the getItems method, but is specifically
    // looking for items with a low amount that will prompt a warning
    public ArrayList<String[]> getLowStockItems(int userId, int lowStockValue) {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<String[]> items = new ArrayList<>();
        Cursor cursor = db.rawQuery(
                "SELECT name, amount, category FROM inventory WHERE user_id=? AND amount<?",
                new String[]{String.valueOf(userId), String.valueOf(lowStockValue)});

        while (cursor.moveToNext()) {
            items.add(new String[]{cursor.getString(0), cursor.getString(1)});
        }
        cursor.close();
        return items;
    }

}
