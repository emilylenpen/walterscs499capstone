package com.example.walterscs360inventoryapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    // Elements of UI
    private EditText itemNameAdd, itemIdAdd, itemAmtAdd;
    private Button btnAddItem;
    private LinearLayout itemsList;

    private DatabaseHelper db; // Database setup
    private SharedPreferences prefs; // Preferences
    private int currentUserId;
    private String currentUserRole; // User role that is viewing to help establish UI

    // SMS setup variables
    private static final String SMS_PHONE_NUM = "15555215554"; // Android emulator testing number
    private static final int STOCK_WARNING_AMT = 5;
    private boolean smsEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hiding the action bar since it serves no purpose
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_database);

        // Set up all of the grid components
        itemNameAdd = findViewById(R.id.itemNameAdd);
        itemIdAdd = findViewById(R.id.itemIdAdd);
        itemAmtAdd = findViewById(R.id.itemAmtAdd);
        btnAddItem = findViewById(R.id.btnAddItem);

        itemsList = findViewById(R.id.inventoryGrid);

        db = new DatabaseHelper(this);
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        // Replaced to include intent info with a fallback in case there are none
        currentUserId = getIntent().getIntExtra("USER_ID", -1);
        currentUserRole = getIntent().getStringExtra("USER_ROLE");

        // This log was for debugging when I had a fallback role established that was interfering.
        Log.d("RBAC_DEBUG", "currentUserRole = " + currentUserRole);
        if (!RoleHelper.canAddItems(currentUserRole)) {
            btnAddItem.setVisibility(View.GONE); // using View for simplicity
        }

        btnAddItem.setOnClickListener(v -> addItem()); // not an else to avoid forcing with null value

        loadItems(); // loads inventory items
        checkSmsPerms(); // checks user's permissions
    }

    private void addItem() {
        // Even though the button should be hidden, this is an additional RBAC check
        // to make sure the user cannot access the item. Notifies user and ends action.
        if (!RoleHelper.canAddItems(currentUserRole)) {
            Toast.makeText(this, R.string.invalid_perms,Toast.LENGTH_SHORT).show();
            return;
        }

        String name = itemNameAdd.getText().toString().trim();
        String id = itemIdAdd.getText().toString().trim();
        String amtEntry = itemAmtAdd.getText().toString().trim();
        int amount;

        // Making sure there is valid data to enter in all fields
        if (name.isEmpty() || id.isEmpty() || amtEntry.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        // Need a try catch so that we can prevent invalid entry ahead of time
        try {
            amount = Integer.parseInt(amtEntry);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            Toast.makeText(this, R.string.invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        if (db.addItem(id, name, amount, currentUserId)) {
            clearEntries();
            loadItems();
            checkStock();
            Toast.makeText(this, R.string.item_added, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.id_exists, Toast.LENGTH_SHORT).show();
        }

        loadItems();

    }

    private void clearEntries() {
        itemNameAdd.setText("");
        itemIdAdd.setText("");
        itemAmtAdd.setText("");
    }

    private void loadItems() {
        itemsList.removeAllViews(); // removes views for cleanup/refresh of items
        ArrayList<String[]> items= db.getItems(currentUserId);

        for (String[] item : items) { // iterator for loading in each item
            addItemRow(item[0], item[1], Integer.parseInt(item[2]));
        }
    }

    private void addItemRow(String id, String name, int amount) {
        // using my item_row.xml for adding a row
        LinearLayout row = (LinearLayout) getLayoutInflater().inflate(R.layout.item_row,
                itemsList, false);
        Log.d("MainActivity", "Adding row: " + name + ", " + id + ", " + amount);

        // TextViews for data in the layout
        TextView nameView = row.findViewById(R.id.itemNameRow);
        TextView idView = row.findViewById(R.id.itemIdRow);
        TextView amtView = row.findViewById(R.id.itemAmtRow);
        Button deleteBtn = row.findViewById(R.id.btnDelItemRow);

        // Setting appropriate data
        nameView.setText(name);
        idView.setText(id);
        amtView.setText(String.valueOf(amount));

        // Listeners for clicks
        // Enhanced to check for RBAC edit permissions
        row.setOnClickListener(v -> {
            if (RoleHelper.canEditItems(currentUserRole)) {
                editItemQty(id, name, amount);
            } else {
                Toast.makeText(this, R.string.invalid_perms, Toast.LENGTH_SHORT).show();
            }
        });

        // Deletion, conditional upon RBAC permissions, old functionality nested
        if (!RoleHelper.canDeleteItems(currentUserRole)) {
            deleteBtn.setVisibility(View.GONE);
        } else {
            deleteBtn.setOnClickListener(v -> {
                if (db.deleteItem(id, currentUserId)) {
                    Toast.makeText(this, R.string.item_deleted, Toast.LENGTH_SHORT).show();
                    loadItems();
                } else {
                    Toast.makeText(this, R.string.item_deletion_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }

        itemsList.addView(row);
    }

    // using Dialog Builder so that a popup functions for editing the item quantity
    // Referenced https://startandroid.ru/ but removed and changed a lot of what was referenced.
    private void editItemQty(String id, String name, int currAmt) {
        // Implementing RBAC check here so that we can leave our try-catch strictly for parsing.
        if (!RoleHelper.canEditItems(currentUserRole)) {
            Toast.makeText(this, R.string.invalid_perms, Toast.LENGTH_SHORT).show();
        }

        // Allows for input
        EditText amtEdit = new EditText(this);
        amtEdit.setText(String.valueOf(currAmt));
        amtEdit.setHint("New Quantity");
        amtEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        amtEdit.selectAll();
        amtEdit.setPadding(16,16,16,16);

        // Dialog builder for popup to assist user
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Quantity");
        builder.setMessage("Item: " + name + " (ID: " + id + ")");
        builder.setView(amtEdit);


        builder.setPositiveButton("Update Qty", (dialog, which) -> {
            String newAmountStr = amtEdit.getText().toString().trim();

            // Like other inputs from before, needs validation
            if (newAmountStr.isEmpty()) {
                Toast.makeText(this, "Please enter a quantity", Toast.LENGTH_SHORT).show();
                return;
            }

            // Again, checking to make sure the number is valid, positive, and possible.
            try {
                int newAmount = Integer.parseInt(newAmountStr);
                if (newAmount < 0) throw new NumberFormatException();
                if (db.updateItem(id, name, newAmount, currentUserId)) {
                    Toast.makeText(this, "Quantity updated successfully.", Toast.LENGTH_SHORT).show();
                    loadItems();
                    checkStock();
                } else {
                    Toast.makeText(this, "Quantity update failed.", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "This quantity is invalid.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // TODO: Implement delete confirmation so that unwanted item deletion does not occur.
    //private void confirmDelete(String id, String name) { }

    private void checkStock() {
        if (!smsEnabled) {
            return; // Don't check if SMS is disabled
        }

        ArrayList<String[]> lowStockItems = db.getLowStockItems(currentUserId, STOCK_WARNING_AMT);

        if (!lowStockItems.isEmpty()) {
            StringBuilder message = new StringBuilder("Low Stock Alert:\n");

            for (String[] item : lowStockItems) {
                message.append("- ").append(item[0]).append(": ").append(item[1]).append(" left\n");
            }

            sendSmsNotif(message.toString());
        }
    }
    private void checkSmsPerms() {
        smsEnabled = prefs.getBoolean("sms_asked", false);
        // Checking for preexisting user permissions
        if (!smsEnabled) {
            // Go to SMS permission activity
            Intent smsIntent = new Intent(this, SmsPermissionActivity.class);
            startActivity(smsIntent);
            //finish(); // Was destroying main activity on launch
        } else {
            // User was already asked
            smsEnabled = prefs.getBoolean("sms_enabled", false);
        }

    }
    private void sendSmsNotif(String message) {
        if (!smsEnabled) {
            // No need to send notification if no permission enabled, Toast would be unnecessary here.
            return;
        }
        // Check to see if we already have permissions first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {

            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(SMS_PHONE_NUM, null, message, null, null);
                Toast.makeText(this, "Low stock SMS notification sent!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to send SMS notification", Toast.LENGTH_SHORT).show();
            }
        } else { // Just a fallback mechanism since there was already a check for perms
            Toast.makeText(this, "SMS permission not granted", Toast.LENGTH_SHORT).show();
        }
    }


}



