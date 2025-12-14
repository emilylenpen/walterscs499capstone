package com.example.waltersinventoryapp;

/*
 * A class that handles the main activity including UI and listener initialization,
 * carrying over Intent, setting up communication with Firestore, loading inventory items,
 * interacting with Item objects to communicate CRUD operation requests to database with helper
 * method, searching for and sorting items, and sending SMS notifications when stock is low after
 * stock checks.
 *
 * Emily Walters
 * CS499 Computer Science Capstone
 * Southern New Hampshire University
 */

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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Elements of UI
    private TextView itemNameHdr, itemIdHdr, itemAmtHdr, itemCatHdr;
    private Drawable arrowUp, arrowDown, noArrow = null;
    private SearchView searchBar;
    private EditText itemNameAdd, itemIdAdd, itemAmtAdd;
    private AutoCompleteTextView itemCatAdd;
    private Button btnAddItem;
    private LinearLayout itemsList;

    private SharedPreferences prefs; // Preferences
    private String currentUserUid;      // Changed to uid and to String to reflect changes
    private String currentUserRole; // User role that is viewing to help establish UI

    private FirestoreHelper firestoreHelper;

    // Creating ArrayLists outside of method so they can be used dynamically
    private ArrayList<Item> allItems;
    private ArrayList<Item> filteredItems;
    private List<ItemCollection> allCollections;

    // Creating toggle for sorting
    private String lastSortedValue = "";
    private boolean ascending = true;

    // Creating array adapter so we can actually update our category options and reuse adapter
    private ArrayAdapter<String> catAdapter;

    // SMS setup variables
    private static final String SMS_PHONE_NUM = "15555215554"; // Android emulator testing number
    private static final int STOCK_WARNING_AMT = 5;
    private boolean smsEnabled = false;

    private String currentCollectionId = null; // determines current item collection

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

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        // Replaced to include intent info with a fallback in case there are none
        currentUserUid = getIntent().getStringExtra("USER_ID");
        currentUserRole = getIntent().getStringExtra("USER_ROLE");

        // This log was for debugging when I had a fallback role established that was interfering.
        Log.d("RBAC_DEBUG", "currentUserRole = " + currentUserRole);
        if (!RoleHelper.canAddItems(currentUserRole)) {
            btnAddItem.setVisibility(View.GONE); // using View for simplicity
        }

        // Setting up Firestore helper
        firestoreHelper = new FirestoreHelper();

        // Setting up category adapter
        catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        itemCatAdd.setAdapter(catAdapter);
        itemCatAdd.setThreshold(1); // one character suggestion amount


        itemNameHdr.setOnClickListener(v -> sortByValue("name"));
        itemIdHdr.setOnClickListener(v -> sortByValue("id"));
        itemAmtHdr.setOnClickListener(v -> sortByValue("amount"));
        itemCatHdr.setOnClickListener(v -> sortByValue("category"));

        btnAddItem.setOnClickListener(v -> addItem()); // not an else to avoid forcing with null value

        // Search filtering
        searchBar.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { filterItems(query); return false; }
            @Override public boolean onQueryTextChange(String newText) { filterItems(newText); return false; }
        });

        loadItemCollections(); // loads inventory item collections, will then load items
        loadSearchBar(); // loads search bar
        checkSmsPerms(); // checks user's permissions
    }

    // Loading collection of items, otherwise known as inventory
    private void loadItemCollections() {
        firestoreHelper.getItemCollections(currentUserUid, new FirestoreHelper.OnItemCollectionsLoadedListener() {
            @Override
            public void onLoaded(List<ItemCollection> collections) {
                allCollections = collections;
                if (collections.isEmpty()) {
                    // Create a default collection if it is empty to avoid a null collection on first load
                    String defaultCollectionId = "collection"; // Could be auto-generated
                    String defaultCollectionName = "My Inventory";
                    List<String> allowedUsers = new ArrayList<>();
                    allowedUsers.add(currentUserUid);

                    ItemCollection defaultCollection = new ItemCollection(defaultCollectionId, defaultCollectionName, allowedUsers);

                    firestoreHelper.createItemCollection(defaultCollection, currentUserUid, new FirestoreHelper.OnActionListener() {
                        @Override
                        public void onSuccess() {
                            currentCollectionId = defaultCollectionId;
                            loadItems(); // Now safe to load items
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(MainActivity.this,
                                    "Failed to create default collection: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    // Use the first collection
                    currentCollectionId = collections.get(0).getCollectionId();
                    loadItems();
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(MainActivity.this, "Failed to load collections: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Load items that exist within the collection (also used for refreshing)
    private void loadItems() {
        if (currentCollectionId == null) return;

        firestoreHelper.getItems(currentCollectionId, new FirestoreHelper.OnItemsLoadedListener() {
            @Override
            public void onLoaded(List<Item> items) {
                allItems = new ArrayList<>(items);
                filteredItems = new ArrayList<>(allItems);
                loadFilteredItems();
                loadCategories();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(MainActivity.this, "Failed to load items: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Load item categories (exists separately so that suggested categories can be prompted
    // to the user when they attempt to type one for a new item being added.
    private void loadCategories() {
        catAdapter.clear();
        for (Item item : allItems) {
            if (item.getCategory() != null && catAdapter.getPosition(item.getCategory()) == -1)
                catAdapter.add(item.getCategory());
        }
        catAdapter.notifyDataSetChanged();
    }

    // User prompts for CRUD interactions
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

        // Basically just replacing old CRUD with new Firestore related functionality.
        Item newItem = new Item(id, name, amount, category);

        final String catFinal = category; // get rid of error where it needs to be immutable
        firestoreHelper.addItem(currentCollectionId, newItem, new FirestoreHelper.OnActionListener() {
            @Override
            public void onSuccess() {
                clearEntries();
                loadItems();
                updateCategories(catFinal);
                checkStock();
                Toast.makeText(MainActivity.this, R.string.item_added, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(MainActivity.this, "Failed to add item: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });


    }

    // Empties the item adding once the item is actually added to reset the field for new items
    private void clearEntries() {
        itemNameAdd.setText("");
        itemIdAdd.setText("");
        itemAmtAdd.setText("");
    }

    // using Dialog Builder so that a popup functions for editing the item quantity
    // Referenced https://startandroid.ru/ but removed and changed a lot of what was referenced.
    // Used for editing an item's amount
    private void editItemAmt(Item item) {
        // Implementing RBAC check here so that we can leave our try-catch strictly for parsing.
        if (!RoleHelper.canEditItems(currentUserRole)) {
            Toast.makeText(this, R.string.invalid_perms, Toast.LENGTH_SHORT).show();
            return;
        }

        // Allows for input
        EditText amtEdit = new EditText(this);
        amtEdit.setText(String.valueOf(item.getAmount()));
        amtEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        amtEdit.selectAll();
        amtEdit.setPadding(16,16,16,16);

        // Dialog builder for popup to assist user
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Amount");
        builder.setMessage("Item: " + item.getName() + " (ID: " + item.getItemId() + ")");
        builder.setView(amtEdit);

        builder.setPositiveButton("Update Amt", (dialog, which) -> {
            String newAmtStr = amtEdit.getText().toString().trim();

            // Like other inputs from before, needs validation
            if (newAmtStr.isEmpty()) {
                Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
                return;
            }

            // Again, checking to make sure the number is valid, positive, and possible.
            // Now works with Firestore.
            try {
                int newAmount = Integer.parseInt(amtEdit.getText().toString().trim());
                item.setAmount(newAmount);
                firestoreHelper.updateItem(currentCollectionId, item, new FirestoreHelper.OnActionListener() {
                    @Override public void onSuccess() { loadItems(); checkStock(); }
                    @Override public void onError(Exception e) { Toast.makeText(MainActivity.this, "Update failed: "+e.getMessage(), Toast.LENGTH_SHORT).show(); }
                });
            } catch (NumberFormatException e) { Toast.makeText(this, R.string.invalid_amount, Toast.LENGTH_SHORT).show(); }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Functions just like the editing item amount but now categories can also be edited.
    private void editItemCat(Item item) {
        // Implementing RBAC check here so that we can leave our try-catch strictly for parsing.
        if (!RoleHelper.canEditItems(currentUserRole)) {
            Toast.makeText(this, R.string.invalid_perms, Toast.LENGTH_SHORT).show();
            return;
        }

        // Allows for input
        EditText catEdit = new EditText(this);
        catEdit.setText(item.getCategory());
        catEdit.setHint("New Category");
        catEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        catEdit.selectAll();
        catEdit.setPadding(16,16,16,16);

        // Dialog builder for popup to assist user
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Category");
        builder.setMessage("Item: " + item.getName() + " (ID: " + item.getItemId() + ")");
        builder.setView(catEdit);


        builder.setPositiveButton("Update Category", (dialog, which) -> {
            String newCat = catEdit.getText().toString().trim();

            // Validating input and updated to include Firestore
            item.setCategory(newCat);
            firestoreHelper.updateItem(currentCollectionId, item, new FirestoreHelper.OnActionListener() {
                @Override
                public void onSuccess() {
                    loadItems();
                    updateCategories(newCat);
                }

                @Override
                public void onError(Exception e) {
                    Toast.makeText(MainActivity.this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Listens for and establishes visual changes associated with the search bar
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

    // Loads the newly filtered items onto the inventory screen
    private void loadFilteredItems() {
        if (filteredItems == null) filteredItems = new ArrayList<>();
        if (itemsList == null) return;
        itemsList.removeAllViews();

        for (Item item : filteredItems) {
            if (item != null) {
                addItemRow(item);
            }
        }
    }

    // Used to add each row containing an item alongside the item_row layout
    private void addItemRow(Item item) {
        // using my item_row.xml for adding a row
        LinearLayout row = (LinearLayout) getLayoutInflater().inflate(R.layout.item_row,
                itemsList, false);
        Log.d("MainActivity", "Adding row: " + item.getName() + ", " + item.getItemId() + ", " + item.getAmount());

        // TextViews for data in the layout
        TextView nameView = row.findViewById(R.id.itemNameRow);
        TextView idView = row.findViewById(R.id.itemIdRow);
        TextView amtView = row.findViewById(R.id.itemAmtRow);
        TextView catView = row.findViewById(R.id.itemCatRow);
        Button deleteBtn = row.findViewById(R.id.btnDelItemRow);

        // Setting appropriate data
        nameView.setText(item.getName());
        idView.setText(item.getItemId());
        amtView.setText(String.valueOf(item.getAmount()));
        catView.setText(item.getCategory());

        // Listeners for clicks, enhanced to check for RBAC edit permissions
        // Amount edit
        amtView.setOnClickListener(v -> {
            if (RoleHelper.canEditItems(currentUserRole)) {
                editItemAmt(item);
            } else {
                Toast.makeText(this, R.string.invalid_perms, Toast.LENGTH_SHORT).show();
            }
        });
        // Category edit
        catView.setOnClickListener(v -> {
            if (RoleHelper.canEditItems(currentUserRole)) {
                editItemCat(item);
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
                final Item deletedItem = item;

                // Item deletion and had to change Snackbar as well to include new functionality
                firestoreHelper.deleteItem(currentCollectionId, item.getItemId(), new FirestoreHelper.OnActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(MainActivity.this, R.string.item_deleted, Toast.LENGTH_SHORT).show();
                        allItems.removeIf(i -> i.getItemId().equals(item.getItemId()));
                        filteredItems.removeIf(i -> i.getItemId().equals(item.getItemId()));
                        loadFilteredItems();

                        // Undo via Snackbar
                        Snackbar.make(itemsList, item.getName() + " deleted.", Snackbar.LENGTH_LONG)
                                .setAction("Undo deletion?", undoView -> {
                                    firestoreHelper.addItem(currentCollectionId, deletedItem, new FirestoreHelper.OnActionListener() {
                                        @Override
                                        public void onSuccess() {
                                            allItems.add(deletedItem);
                                            filteredItems.add(deletedItem);
                                            loadFilteredItems();
                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            Toast.makeText(MainActivity.this, "Undo failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }).show();
                    }
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(MainActivity.this, R.string.item_deletion_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        itemsList.addView(row);
    }

    // Sorts the items with ascending or descending value depending on user header click
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

        // Switch case that allows user to sort by any of the header values
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

    // Updates the user categories to actually contain the new categories into the list
    private void updateCategories(String newCategory) {
        if (newCategory == null || newCategory.isEmpty()) return;

        if (catAdapter.getPosition(newCategory) == -1) {
            catAdapter.add(newCategory);
            catAdapter.notifyDataSetChanged();
        }
    }

    // Checks all item stocks and adds them to a list if they fall below a certain amount
    private void checkStock() {
        if (!smsEnabled) {
            return; // Don't check if SMS is disabled
        }

        List<Item> lowStockItems = new ArrayList<>();
        for (Item item : allItems) if (item.getAmount() <= STOCK_WARNING_AMT) lowStockItems.add(item);

        if (!lowStockItems.isEmpty()) {
            StringBuilder message = new StringBuilder("Low Stock Alert:\n");

            for (Item item : lowStockItems) {
                message.append("- ").append(item.getName()).append(": ").append(item.getAmount()).append(" left\n");
            }

            sendSmsNotif(message.toString());
        }
    }

    // Checks user's SMS permissions to know whether notifications should be sent on low stock
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

    // Sends user an SMS notification if an item's stock is low and if they have permission
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



