package com.example.walterscs360inventoryapp;

/* Help from https://developer.android.com/training/permissions/requesting
* as well as https://developer.android.com/reference/android/telephony/SmsManager
* I realized that my comments were heavy on this for what it actually is but I
* was challenged by this section of the code so it was more for myself to be able
* to understand what was actually happening!
*/

import static android.view.Gravity.apply;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SmsPermissionActivity extends AppCompatActivity {
    private static final int permission_code = 111; // Request code for SEND_SMS permission
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms); // Setting SMS Screen

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        // Establish buttons
        Button smsBtnAllow = findViewById(R.id.smsBtnAllow);
        Button smsBtnDeny = findViewById(R.id.smsBtnDeny);

        // Create listener for the button click when they allow perms then requesting perms
        smsBtnAllow.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        permission_code);
            } else {
                savePerms(true);
            }
        });

        // Create listener for deny button and finishing screen while saving preferences
        smsBtnDeny.setOnClickListener( v -> {
            savePerms(false);
        });
    }

    // Processes results of Android interaction to verify they are true.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                     @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == permission_code) {
            // Once the user clicks allow, they still have to accept it on the system and we
            // are checking to make sure we actually have permissions.
            // Need to check for emptiness so it doesn't crash.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                savePerms(true); // Can save permission if it is valid.
            } else {
                savePerms(false);
            }
        }
    }

    // Saves the permissions that have been granted, passes along previous preferences as well
    // for first login, and moves along to the MainActivity. (This was a huge reason I was having
    // crashes before after going from hardcoded "admin" to "user"-no prefs remembered here before
    // and only in the login screen!)
    private void savePerms(boolean granted) {
        // Set preferences
        prefs.edit()
                .putBoolean("sms_asked", true)
                .putBoolean("sms_enabled", granted)
                .apply();

        if (granted) {
            Toast.makeText(this, getString(R.string.sms_perm_granted), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.sms_perm_denied), Toast.LENGTH_SHORT).show();
        }

        // Need to pass along the preferences so that the user role doesn't come back as null
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("USER_ID", prefs.getInt("user_id", -1));
        intent.putExtra("USER_ROLE", prefs.getString("user_role",""));
        startActivity(intent);
        finish();
    }


}
