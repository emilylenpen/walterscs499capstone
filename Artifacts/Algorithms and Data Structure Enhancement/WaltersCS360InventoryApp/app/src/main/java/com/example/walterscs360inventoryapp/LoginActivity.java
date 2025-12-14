package com.example.walterscs360inventoryapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private EditText usernameEntry, passwordEntry;
    private Button btnLogin, btnCreateAcct;
    private DatabaseHelper db;
    private SharedPreferences prefs; // For remembering user login information between sessions

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialization of preferences
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        db = new DatabaseHelper(this);

        // Establishes what elements work with certain functionality
        usernameEntry = findViewById(R.id.usernameEntry);
        passwordEntry = findViewById(R.id.passwordEntry);
        btnLogin = findViewById(R.id.btnLogin);
        btnCreateAcct = findViewById(R.id.btnCreateAcct);

        // Now indicates id and role preferences
        if (prefs.getBoolean("logged_in", false)) {
            goToMain(
                    prefs.getInt("user_id", -1),
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
        String username = usernameEntry.getText().toString().trim();
        String password = passwordEntry.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        // Object retrieval for user with new User class from database
        User user = db.getUser(username, password);

        // checks login info
        if (user != null) {
            // save preference for next session
            // updated preferences away from username and to id and role
            prefs.edit()
                    .putBoolean("logged_in", true)
                    .putInt("user_id", user.getUserId())
                    .putString("user_role", user.getRole())
                    .apply();

            Toast.makeText(this, R.string.login_successful, Toast.LENGTH_SHORT).show();
            Log.d("LOGIN", "User role: " + user.getRole());
            goToMain(user.getUserId(), user.getRole());
        } else {
            Toast.makeText(this, R.string.login_unsuccessful, Toast.LENGTH_SHORT).show();
        }
    }

    // Creation of user account.
    // Now includes user role and logic to better handle account creation with new info.
    private void handleCreateAcct() {
        String username = usernameEntry.getText().toString().trim();
        String password = passwordEntry.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        // User is automatically assigned with 'user' role rather than admin.
        // String defaultRole = "user";

        // FIXME: Re-implement defaultRole after testing. See DBHelper FIXME for more info.
        int userId = db.createUser(username, password);

        // User creation success check now updated to check with a user ID
        if (userId != -1) {
            Toast.makeText(this, R.string.acct_creation_successful, Toast.LENGTH_SHORT).show();
            passwordEntry.setText("");
        } else {
            Toast.makeText(this, R.string.username_exists, Toast.LENGTH_SHORT).show();
        }
    }

    // Passes userId along and role for proper display dependent on role.
    // Using this function with finish() ensures logging in is a one-time event with no return
    private void goToMain(int userId, String role) {
        Intent intent = new Intent(this, MainActivity.class); // separated out to include putExtra()
        intent.putExtra("USER_ID", userId);
        intent.putExtra("USER_ROLE", role);
        startActivity(intent);
        finish();
    }
}
