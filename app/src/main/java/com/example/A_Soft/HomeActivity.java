package com.example.A_Soft;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        // Initialize buttons as ImageButton
        ImageButton buttonUretim = findViewById(R.id.button_uretim);
        ImageButton buttonSevkiyat = findViewById(R.id.button_sevkiyat);
        ImageButton buttonLogout = findViewById(R.id.button_logout); // New logout button

        // Set click listener for uretim button
        buttonUretim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to UretimActivity
                startActivity(new Intent(HomeActivity.this, UretimMainActivity.class));
            }
        });

        // Set click listener for sevkiyat button
        buttonSevkiyat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to SevkiyatActivity
                startActivity(new Intent(HomeActivity.this, SevkiyatMainActivity.class));
            }
        });

        // Set click listener for logout button
        buttonLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Clear session or login preferences
                SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.clear();
                editor.apply();

                // Navigate back to LoginActivity
                Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }
}
