package com.example.A_Soft;

import static com.example.A_Soft.LoginActivity.clearSavedUsername;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {
    private static final int MODULE_URETIM = 1061;  // Based on your database
    private static final int MODULE_SEVKIYAT = 46; // Adjust this value based on your actual MENUREF

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        // Find both layouts and buttons
        View layoutUretim = findViewById(R.id.layout_uretim);
        View layoutSevkiyat = findViewById(R.id.layout_sevkiyat);
        ImageButton buttonUretim = findViewById(R.id.button_uretim);
        ImageButton buttonSevkiyat = findViewById(R.id.button_sevkiyat);
        ImageButton buttonLogout = findViewById(R.id.button_logout);

        // Get user's module rights from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
        Set<String> moduleRights = prefs.getStringSet("module_rights", new HashSet<>());

        // Show/hide layouts based on module rights
        layoutUretim.setVisibility(moduleRights.contains(String.valueOf(MODULE_URETIM)) ?
                View.VISIBLE : View.GONE);
        layoutSevkiyat.setVisibility(moduleRights.contains(String.valueOf(MODULE_SEVKIYAT)) ?
                View.VISIBLE : View.GONE);

        // Set click listeners for the buttons (not the layouts)
        buttonUretim.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, ProductionMainActivity.class)));

        buttonSevkiyat.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, SevkiyatMainActivity.class)));

        buttonLogout.setOnClickListener(v -> {
            // Clear session or login preferences
            SharedPreferences loginPrefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = loginPrefs.edit();
            editor.clear();
            editor.apply();

            // Clear saved username
            clearSavedUsername(HomeActivity.this);

            // Navigate back to LoginActivity
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}