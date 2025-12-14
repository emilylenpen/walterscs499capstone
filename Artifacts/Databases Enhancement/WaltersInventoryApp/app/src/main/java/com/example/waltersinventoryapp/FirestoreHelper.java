package com.example.waltersinventoryapp;

/*
 * A class that interprets between the application and Firestore by accessing data related to
 * users, item collections (inventories), and items. The class also contains role-based access
 * control by checking what users are allowed to access the collections.
 *
 * Emily Walters
 * CS499 Computer Science Capstone
 * Southern New Hampshire University
 *
 * Relied extensively on Firestore docs to get this working alongside Stack Exchange:
 * https://firebase.google.com/docs/firestore/quickstart
 */

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.List;

// Creating Firestore database and collections
public class FirestoreHelper {
    private final FirebaseFirestore db;

    public FirestoreHelper() {
        db = FirebaseFirestore.getInstance();
    }

    // User methods

    // Retrieves user data based on UID and stores it in User object
    public void getUser(String uid, OnUserLoadedListener listener) {
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String email = doc.getString("email");
                        String role = doc.getString("role");
                        User user = new User(uid, email, role);
                        listener.onLoaded(user);
                    } else {
                        listener.onError(new Exception("User document does not exist."));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    // Creates a user in Firestore with a User object
    public void createUser(User user, OnActionListener listener) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", user.getUsername());
        data.put("role", user.getRole());

        db.collection("users")
                .document(user.getUserId())
                .set(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    // ItemCollection methods (as in the collection of items that includes who has permissions)
    // Retrieves item collections based on user's UID and stores it in ItemCollection (as in a
    // single inventory) object. ItemCollection represents an inventory that contains Items.
    public void getItemCollections(String uid, OnItemCollectionsLoadedListener listener) {
        db.collection("itemcollections")
                .whereArrayContains("allowedUsers", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<ItemCollection> collections = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String collectionId = doc.getId();
                        String name = doc.getString("name");
                        collections.add(new ItemCollection(collectionId, name));
                    }
                    listener.onLoaded(collections);
                })
                .addOnFailureListener(listener::onError);
    }

    // Creates an item collection to be stored in Firestore
    public void createItemCollection(ItemCollection collection, String uid, OnActionListener listener) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", collection.getName());
        data.put("allowedUsers", collection.getAllowedUsers());

        db.collection("itemcollections")
                .document(collection.getCollectionId())
                .set(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    // Item methods for items within ItemCollections :)

    // Loads items from Firestore within a certain item collection to be stored as Item objects.
    // The app can then use the data stored in these Item objects for user interaction.
    public void getItems(String collectionId, OnItemsLoadedListener listener) {
        CollectionReference itemsRef = db.collection("itemcollections")
                .document(collectionId)
                .collection("items");

        itemsRef.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Item> items = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String itemId = doc.getId();
                        String name = doc.getString("name");
                        Long amountLong = doc.getLong("amount"); // Converting from long to double safely
                        int amount = amountLong != null ? amountLong.intValue() : 0;
                        String category = doc.getString("category");
                        items.add(new Item(itemId, name, amount, category));
                    }
                    listener.onLoaded(items);
                })
                .addOnFailureListener(listener::onError);
    }

    // Adds an item to the collection in Firestore based on info contained within Item object.
    // (Basically makes item readable for Firestore and stores it there as an item in a collection)
    public void addItem(String collectionId, Item item, OnActionListener listener) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", item.getName());
        data.put("amount", item.getAmount());
        data.put("category", item.getCategory());

        db.collection("itemcollections")
                .document(collectionId)
                .collection("items")
                .document(item.getItemId())
                .set(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    // Updates an item in the collection in Firestore based on Item object info.
    // (Interprets changes to item into readable changes for Firestore)
    public void updateItem(String collectionId, Item item, OnActionListener listener) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", item.getName());
        data.put("amount", item.getAmount());
        data.put("category", item.getCategory());

        db.collection("itemcollections")
                .document(collectionId)
                .collection("items")
                .document(item.getItemId())
                .update(data)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    // Deletes an item in the given item collection based on Item object provided
    public void deleteItem(String collectionId, String itemId, OnActionListener listener) {
        db.collection("itemcollections")
                .document(collectionId)
                .collection("items")
                .document(itemId)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    // Listeners (there were so many errors before these were properly defined...)
    // Essentially serve to wait for when Firestore completes tasks to then update changes
    // since the service is asynchronous.

    // Listener for a loaded User object
    public interface OnUserLoadedListener {
        void onLoaded(User user);
        void onError(Exception e);
    }

    // Listener for a loaded Item object
    public interface OnItemsLoadedListener {
        void onLoaded(List<Item> items);
        void onError(Exception e);
    }

    // Listener for a loaded item collection
    public interface OnItemCollectionsLoadedListener {
        void onLoaded(List<ItemCollection> collections);
        void onError(Exception e);
    }

    // Listener for CRUD interactions with items
    public interface OnActionListener {
        void onSuccess();
        void onError(Exception e);
    }

}
