package com.example.waltersinventoryapp;

/*
 * A class that handles logic for a user within the login screen creating an account,
 * logging in, and proceeding to the main activity. Also handles storing login preferences
 * and user authentication with Firebase.
 *
 * Emily Walters
 * CS499 Computer Science Capstone
 * Southern New Hampshire University
 */

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {
    private EditText usernameEntry, passwordEntry;
    private Button btnLogin, btnCreateAcct;

    // Creating Firebase authentication to simplify login and tie to Firebase.
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // private DatabaseHelper db;
    private SharedPreferences prefs; // For remembering user login information between sessions

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Instances of Firebase setup
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialization of preferences
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        // db = new DatabaseHelper(this);

        // Establishes what elements work with certain functionality
        usernameEntry = findViewById(R.id.usernameEntry);
        passwordEntry = findViewById(R.id.passwordEntry);
        btnLogin = findViewById(R.id.btnLogin);
        btnCreateAcct = findViewById(R.id.btnCreateAcct);

        // Logs user in if has already been authenticated
        if (mAuth.getCurrentUser() != null && prefs.getBoolean("logged_in", false)) {
            FirebaseUser firebaseUser = mAuth.getCurrentUser();
            goToMain(
                    firebaseUser.getUid(),
                    prefs.getString("user_role", "")
            );
            return;
        }

        // Listener for when user clicks button
        btnLogin.setOnClickListener(v -> handleLogin());
        btnCreateAcct.setOnClickListener(v -> handleCreateAcct());
    }

    // Logic for handling user login upon button press
    private void handleLogin() {
        String email = usernameEntry.getText().toString().trim();
        String password = passwordEntry.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        // Uses email and password sign-in method
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        Toast.makeText(this, R.string.login_unsuccessful, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Fetch user role
                    db.collection("users")
                            .document(user.getUid())
                            .get()
                            .addOnSuccessListener((DocumentSnapshot doc) -> {
                                if (doc.exists()) {
                                    String role = doc.getString("role");

                                    // Save user login preferences
                                    prefs.edit()
                                            .putBoolean("logged_in", true)
                                            .putString("user_role", role)
                                            .apply();

                                    Toast.makeText(this, R.string.login_successful, Toast.LENGTH_SHORT).show();
                                    goToMain(user.getUid(), role);
                                } else {
                                    Toast.makeText(this, R.string.missing_profile, Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );

    }

    // Creation of user account.
    // Now includes user role and logic to better handle account creation with new info.
    private void handleCreateAcct() {
        String email = usernameEntry.getText().toString().trim();
        String password = passwordEntry.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        // Uses email and password for user creation
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                String uid = result.getUser().getUid();

                // Default role is user
                User newUser = new User(uid, email, "user");

                // Save document to Firestore
                    db.collection("users")
                            .document(uid)
                            .set(newUser)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, R.string.acct_creation_successful, Toast.LENGTH_SHORT).show();
                            });

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.acct_creation_unsuccessful, Toast.LENGTH_SHORT).show()
                );
    }

    // Passes userId along and role for proper display dependent on role.
    // Using this function with finish() ensures logging in is a one-time event with no return
    private void goToMain(String uid, String role) {
        Intent intent = new Intent(this, MainActivity.class); // separated out to include putExtra()
        intent.putExtra("USER_ID", uid);
        intent.putExtra("USER_ROLE", role);
        startActivity(intent);
        finish();
    }
}
