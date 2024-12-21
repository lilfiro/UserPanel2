package com.example.A_Soft;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton; // Change Button to ImageButton
import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        // Initialize buttons as ImageButton
        ImageButton buttonUretim = findViewById(R.id.button_uretim);
        ImageButton buttonSevkiyat = findViewById(R.id.button_sevkiyat);

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
    }
}
