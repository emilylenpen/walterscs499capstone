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

    // Need two databases, one for items and one for user accounts.
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (username TEXT PRIMARY KEY, password TEXT)");
        db.execSQL("CREATE TABLE inventory (id TEXT, name TEXT, amount INTEGER, user TEXT)");
    }

    // This is needed by default in case the app is upgraded so that the data
    // can be properly adjusted to the version. Using a switch case so versions can be added later.
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        for (int version = oldVersion + 1; version <= newVersion; version++) {
            switch (version) {
                case 2:
                    db.execSQL("ALTER TABLE inventory ADD COLUMN item_type"); // Theoretical upgrade
                    break;
                case 3:
                    break;
            }
        }
    }

    public boolean createUser(String username, String password) {
        SQLiteDatabase db = getWritableDatabase(); // needs to be editable
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password", password);
        return db.insert("users", null, values) != -1;
    }

    // Decided against using a Cursor here for such a simple check.
    public boolean checkUser(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        long count = DatabaseUtils.longForQuery(db,
                "SELECT COUNT(*) FROM users WHERE username=? AND password=?",
                new String[]{username, password});
        return count > 0;
    }

    // Database CRUD operations; decided to just keep them in the DB class for simplicity
    // and since the user additions were here already it felt logical to keep both db
    // alteration method groups together.
    public boolean addItem(String id, String name, int amount, String user) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", name);
        values.put("amount", amount);
        values.put("user", user);
        return db.insert("inventory", null, values) != -1;
    }

    // Using a cursor to iterate and store the items to be returned to the user
    public ArrayList<String[]> getItems(String user) {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<String[]> items = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT id, name, amount FROM inventory WHERE user=?",
                new String[]{user});

        while (cursor.moveToNext()) {
            items.add(new String[]{
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2)
            });
        }
        cursor.close();
        return items;
    }

    // Update item given the information input
    public boolean updateItem(String id, String name, int amount, String user) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("amount", amount);
        return db.update("inventory", values, "id=? AND user =?",
                new String[]{id, user}) > 0;
    }

    public boolean deleteItem(String id, String user) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("inventory", "id=? AND user=?", new String[]{id, user}) > 0;
    }

    // Iterate through items similar to the getItems method, but is specifically
    // looking for items with a low amount that will prompt a warning
    public ArrayList<String[]> getLowStockItems(String user, int lowStockValue) {
        SQLiteDatabase db = getReadableDatabase();
        ArrayList<String[]> items = new ArrayList<>();
        Cursor cursor = db.rawQuery(
                "SELECT name, amount FROM inventory WHERE user=? AND amount<?",
                new String[]{user, String.valueOf(lowStockValue)});

        while (cursor.moveToNext()) {
            items.add(new String[]{cursor.getString(0), cursor.getString(1)});
        }
        cursor.close();
        return items;
    }

}
