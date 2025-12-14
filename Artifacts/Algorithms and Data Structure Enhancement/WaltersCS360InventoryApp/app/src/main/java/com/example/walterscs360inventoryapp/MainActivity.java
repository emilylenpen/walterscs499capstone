package com.example.walterscs360inventoryapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SearchView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity {

    // Elements of UI
    private TextView itemNameHdr, itemIdHdr, itemAmtHdr, itemCatHdr;
    private Drawable arrowUp, arrowDown, noArrow = null;
    private SearchView searchBar;
    private EditText itemNameAdd, itemIdAdd, itemAmtAdd;
    private AutoCompleteTextView itemCatAdd;
    private Button btnAddItem;
    private LinearLayout itemsList;

    private DatabaseHelper db; // Database setup
    private SharedPreferences prefs; // Preferences
    private int currentUserId;
    private String currentUserRole; // User role that is viewing to help establish UI

    // Creating ArrayLists outside of method so they can be used dynamically
    private ArrayList<Item> allItems;
    private ArrayList<Item> filteredItems;

    // Creating toggle for sorting
    private String lastSortedValue = "";
    private boolean ascending = true;

    // Creating array adapter so we can actually update our category options and reuse adapter
    private ArrayAdapter<String> catAdapter;

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

        // Creates search bar for items
        searchBar = findViewById(R.id.searchBar);

        // Creates clickable headers for sorting
        itemNameHdr = findViewById(R.id.itemNameHdr);
        itemIdHdr = findViewById(R.id.itemIdHdr);
        itemAmtHdr = findViewById(R.id.itemAmtHdr);
        itemCatHdr = findViewById(R.id.itemCatHdr);
        // Drawables for arrows that appear when sorting
        arrowUp = AppCompatResources.getDrawable(this, R.drawable.ic_arrow_up);
        arrowDown = AppCompatResources.getDrawable(this, R.drawable.ic_arrow_down);

        // Set up all of the grid components
        itemNameAdd = findViewById(R.id.itemNameAdd);
        itemIdAdd = findViewById(R.id.itemIdAdd);
        itemAmtAdd = findViewById(R.id.itemAmtAdd);
        itemCatAdd = findViewById(R.id.itemCatAdd);
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

        itemNameHdr.setOnClickListener(v -> sortByValue("name"));
        itemIdHdr.setOnClickListener(v -> sortByValue("id"));
        itemAmtHdr.setOnClickListener(v -> sortByValue("amount"));
        itemCatHdr.setOnClickListener(v -> sortByValue("category"));

        btnAddItem.setOnClickListener(v -> addItem()); // not an else to avoid forcing with null value

        loadItems(); // loads inventory items
        loadSearchBar(); // loads search bar
        checkSmsPerms(); // checks user's permissions

        // For autocompleting categories
        ArrayList<String> categories = db.getCategories(); // pulls cats from db
        if (categories == null) categories = new ArrayList<>();

        catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, categories);
        itemCatAdd.setAdapter(catAdapter);
        itemCatAdd.setThreshold(1); // one character suggestion amount

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
        String category = itemCatAdd.getText().toString().trim();
        int amount;

        // Making sure there is valid data to enter in all fields
        if (name.isEmpty() || id.isEmpty() || amtEntry.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        // Category is going to be optional, keeping separate from other stuff
        if (category.isEmpty()) {
            category = "Other";
        }

        // Need a try catch so that we can prevent invalid entry ahead of time
        try {
            amount = Integer.parseInt(amtEntry);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            Toast.makeText(this, R.string.invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        if (db.addItem(id, name, amount, category, currentUserId)) {
            clearEntries();
            loadItems();
            checkStock();
            updateCategories(category);
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

    // Item filtering from search bar query
    private void filterItems(String query) {
        // null checks
        if (allItems == null ) {
            allItems = new ArrayList<>();
        }
        if (filteredItems == null) {
            filteredItems = new ArrayList<>();
        }
        filteredItems.clear();

        if (query == null) query = "";

        for (Item item : allItems) {
            if (item == null) continue;
            String name = item.getName();
            if (name != null && name.toLowerCase().contains(query.toLowerCase())) {
                filteredItems.add(item);
            }
        }

        loadFilteredItems();
    }

    private void loadFilteredItems() {
        if (filteredItems == null) filteredItems = new ArrayList<>();
        if (itemsList == null) return;
        itemsList.removeAllViews();

        for (Item item : filteredItems) {
            if (item != null) {
                addItemRow(item.getItemId(), item.getName(), item.getAmount(), item.getCategory());
            }
        }
    }

    private void loadItems() {
        itemsList.removeAllViews(); // removes views for cleanup/refresh of items
        // ArrayList<String[]> items= db.getItems(currentUserId);
        allItems = db.getItemObjects(currentUserId); // Items before search
        if (allItems == null) allItems = new ArrayList<>();
        filteredItems = new ArrayList<>(allItems); // Items filtered from search

        // Updated to use Item ArrayList with all items rather than the String ArrayList
        for (Item item : allItems) { // iterator for loading in each item
            addItemRow(item.getItemId(), item.getName(), item.getAmount(), item.getCategory());
        }

        loadFilteredItems();
    }

    private void addItemRow(String id, String name, int amount, String category) {
        // using my item_row.xml for adding a row
        LinearLayout row = (LinearLayout) getLayoutInflater().inflate(R.layout.item_row,
                itemsList, false);
        Log.d("MainActivity", "Adding row: " + name + ", " + id + ", " + amount);

        // TextViews for data in the layout
        TextView nameView = row.findViewById(R.id.itemNameRow);
        TextView idView = row.findViewById(R.id.itemIdRow);
        TextView amtView = row.findViewById(R.id.itemAmtRow);
        TextView catView = row.findViewById(R.id.itemCatRow);
        Button deleteBtn = row.findViewById(R.id.btnDelItemRow);

        // Setting appropriate data
        nameView.setText(name);
        idView.setText(id);
        amtView.setText(String.valueOf(amount));
        catView.setText(category);

        // Listeners for clicks, enhanced to check for RBAC edit permissions
        // Amount edit
        amtView.setOnClickListener(v -> {
            if (RoleHelper.canEditItems(currentUserRole)) {
                editItemAmt(id, name, amount, category);
            } else {
                Toast.makeText(this, R.string.invalid_perms, Toast.LENGTH_SHORT).show();
            }
        });
        // Category edit
        catView.setOnClickListener(v -> {
            if (RoleHelper.canEditItems(currentUserRole)) {
                editItemCat(id, name, amount, category);
            } else {
                Toast.makeText(this, R.string.invalid_perms, Toast.LENGTH_SHORT).show();
            }
        });

        // Deletion, conditional upon RBAC permissions, old functionality nested
        if (!RoleHelper.canDeleteItems(currentUserRole)) {
            deleteBtn.setVisibility(View.GONE);
        } else {
            deleteBtn.setOnClickListener(v -> {
                // Storing in case of an undo
                final Item deletedItem = new Item(id, name, amount, category);

                // Item deletion
                if (db.deleteItem(id, currentUserId)) {
                    Toast.makeText(this, R.string.item_deleted, Toast.LENGTH_SHORT).show();
                    allItems.removeIf(item -> item.getItemId().equals(id));
                    filteredItems.removeIf(item -> item.getItemId().equals(id));
                    loadFilteredItems();

                    Snackbar.make(itemsList, name + "deleted.", Snackbar.LENGTH_LONG)
                            .setAction("Undo deletion?", undoView -> {
                                db.addItem(deletedItem.getItemId(), // Add item back to database
                                        deletedItem.getName(),
                                        deletedItem.getAmount(),
                                        deletedItem.getCategory(),
                                        currentUserId);

                                filteredItems.add(deletedItem);
                                loadFilteredItems(); // Display re-added item
                            }).show();
                } else {
                    Toast.makeText(this, R.string.item_deletion_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }

        itemsList.addView(row);
    }

    private void loadSearchBar() {
        // Establishing search bar listener
        searchBar.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterItems(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterItems(newText);
                return false;
            }
        });
    }

    private void sortByValue(String value) {
        if (filteredItems == null || filteredItems.isEmpty()) return;

        // If reclicking same sorted column, swaps from ascending to descending
        if (lastSortedValue.equals(value)) {
            ascending = !ascending; // flipping boolean
        } else {
            ascending = true; // correcting in case other sorts
        }
        lastSortedValue = value;

        Comparator<Item> comparator;

        switch (value) {
            case "id":
                comparator = Comparator.comparing(Item::getItemId, String.CASE_INSENSITIVE_ORDER);
                break;
            case "amount":
                comparator = Comparator.comparing(Item::getAmount);
                break;
            case "category":
                comparator = Comparator.comparing(Item::getCategory, String.CASE_INSENSITIVE_ORDER);
                break;
            default: // name by default
                comparator = Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER);
                break;
        }
        if (!ascending) {
            comparator = comparator.reversed();
        }

        filteredItems.sort(comparator);
        loadFilteredItems();
        updateArrows(value, ascending);
    }

    // Updates the visual cues that show whether it is ascending or descending.
    private void updateArrows(String lastSortedValue, boolean ascending) {
        TextView[] headers = {itemNameHdr, itemIdHdr, itemAmtHdr, itemCatHdr};
        String[] values = {"name", "id", "amount", "category"};

        for (int i = 0; i < headers.length; i++) {
            if (values[i].equals(lastSortedValue)) {
                headers[i].setCompoundDrawablesWithIntrinsicBounds(
                        null, null, ascending ? arrowUp : arrowDown, null
                );
            } else {
                headers[i].setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }
        }
    }

    private void updateCategories(String newCategory) {
        if (newCategory == null || newCategory.isEmpty()) return;

        // Seeing if category already exists
        boolean exists = false;
        for (int i = 0; i < catAdapter.getCount(); i++) {
            if (catAdapter.getItem(i).equalsIgnoreCase(newCategory)) {
                exists = true;
                break;
            }
        }

        // Add category if not in adapter
        if (!exists) {
            catAdapter.add(newCategory);
            catAdapter.notifyDataSetChanged();
        }
    }

    // using Dialog Builder so that a popup functions for editing the item quantity
    // Referenced https://startandroid.ru/ but removed and changed a lot of what was referenced.
    private void editItemAmt(String id, String name, int currAmt, String category) {
        // Implementing RBAC check here so that we can leave our try-catch strictly for parsing.
        if (!RoleHelper.canEditItems(currentUserRole)) {
            Toast.makeText(this, R.string.invalid_perms, Toast.LENGTH_SHORT).show();
            return;
        }

        // Allows for input
        EditText amtEdit = new EditText(this);
        amtEdit.setText(String.valueOf(currAmt));
        amtEdit.setHint("New Amount");
        amtEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        amtEdit.selectAll();
        amtEdit.setPadding(16,16,16,16);

        // Dialog builder for popup to assist user
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Amount");
        builder.setMessage("Item: " + name + " (ID: " + id + ")");
        builder.setView(amtEdit);


        builder.setPositiveButton("Update Amt", (dialog, which) -> {
            String newAmtStr = amtEdit.getText().toString().trim();

            // Like other inputs from before, needs validation
            if (newAmtStr.isEmpty()) {
                Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
                return;
            }

            // Again, checking to make sure the number is valid, positive, and possible.
            try {
                int newAmount = Integer.parseInt(newAmtStr);
                if (newAmount < 0) throw new NumberFormatException();
                if (db.updateItem(id, name, newAmount, category, currentUserId)) {
                    Toast.makeText(this, "Amount updated successfully.", Toast.LENGTH_SHORT).show();
                    loadItems();
                    checkStock();
                } else {
                    Toast.makeText(this, "Amount update failed.", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "This amount is invalid.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void editItemCat(String id, String name, int amount, String currCat) {
        // Implementing RBAC check here so that we can leave our try-catch strictly for parsing.
        if (!RoleHelper.canEditItems(currentUserRole)) {
            Toast.makeText(this, R.string.invalid_perms, Toast.LENGTH_SHORT).show();
            return;
        }

        // Allows for input
        EditText catEdit = new EditText(this);
        catEdit.setText(currCat);
        catEdit.setHint("New Category");
        catEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        catEdit.selectAll();
        catEdit.setPadding(16,16,16,16);

        // Dialog builder for popup to assist user
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Category");
        builder.setMessage("Item: " + name + " (ID: " + id + ")");
        builder.setView(catEdit);


        builder.setPositiveButton("Update Category", (dialog, which) -> {
            String newCat = catEdit.getText().toString().trim();

            // Like other inputs from before, needs validation
            if (newCat.isEmpty()) {
                Toast.makeText(this, "Please enter a category", Toast.LENGTH_SHORT).show();
                return;
            }

            if (db.updateItem(id, name, amount, newCat, currentUserId)) {
                Toast.makeText(this, "Category updated successfully.", Toast.LENGTH_SHORT).show();
                loadItems();
                checkStock();
                updateCategories(newCat);
            } else {
                Toast.makeText(this, "Category update failed.", Toast.LENGTH_SHORT).show();
            }

        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }


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



