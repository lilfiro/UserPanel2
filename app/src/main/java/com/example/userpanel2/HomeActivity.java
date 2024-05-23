package com.example.userpanel2;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        // Initialize buttons
        Button buttonUretim = findViewById(R.id.button_uretim);
        Button buttonSevkiyat = findViewById(R.id.button_sevkiyat);

        // Set click listener for uretim button
        buttonUretim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to UretimActivity
                startActivity(new Intent(HomeActivity.this, FragmentActivity.class));
            }
        });

        // Set click listener for sevkiyat button
        buttonSevkiyat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to SevkiyatActivity tuşu otomatik olarka sevkiyata çektirebilirim idk
                startActivity(new Intent(HomeActivity.this, FragmentActivity.class));
            }
        });
    }
}