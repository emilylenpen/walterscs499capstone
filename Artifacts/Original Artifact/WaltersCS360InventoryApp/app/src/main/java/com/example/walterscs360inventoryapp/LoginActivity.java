package com.example.walterscs360inventoryapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        db = new DatabaseHelper(this);

        usernameEntry = findViewById(R.id.usernameEntry);
        passwordEntry = findViewById(R.id.passwordEntry);
        btnLogin = findViewById(R.id.btnLogin);
        btnCreateAcct = findViewById(R.id.btnCreateAcct);

        if (prefs.getBoolean("logged_in", false)) {
            goToMain();
            return;
        }

        // Listener for when user clicks button
        btnLogin.setOnClickListener(v -> handleLogin());
        btnCreateAcct.setOnClickListener(v -> handleCreateAcct());
    }

    // Originally used isBlank in the if statement checking empty fields, but I'm trimming the
    // values which makes it unnecessary.
    private void handleLogin() {
        String username = usernameEntry.getText().toString().trim();
        String password = passwordEntry.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        // checks login info
        if (db.checkUser(username, password)) {
            // save preference for next session
            prefs.edit()
                    .putBoolean("logged_in", true)
                    .putString("username", username)
                    .apply();

            Toast.makeText(this, R.string.login_successful, Toast.LENGTH_SHORT).show();

            goToMain();
        }
    }

    private void handleCreateAcct() {
        String username = usernameEntry.getText().toString().trim();
        String password = passwordEntry.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        if (db.createUser(username, password)) {
            Toast.makeText(this, R.string.acct_creation_successful, Toast.LENGTH_SHORT).show();
            passwordEntry.setText("");
        } else {
            Toast.makeText(this, R.string.username_exists, Toast.LENGTH_SHORT).show();
        }
    }

    // Using this function with finish() ensures logging in is a one-time event with no return
    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
