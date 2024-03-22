package com.example.userpanel2;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class HomePage extends AppCompatActivity {
    private FloatingActionButton fab;
    private FloatingActionButton option1Fab;
    private FloatingActionButton option2Fab;
    private boolean isExpanded = false; // Track whether FAB options are expanded

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_page);

        fab = findViewById(R.id.FloatBt);
        option1Fab = findViewById(R.id.option1);
        option2Fab = findViewById(R.id.option2);

        // Set click listener for the FAB
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFABOptions(); // Toggle between expanding and collapsing FAB options
            }
        });

        // Set click listener for option1Fab
        option1Fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Redirect to ReceiptActivity
                startActivity(new Intent(HomePage.this, ReceiptActivity.class));
            }
        });
    }

    // Method to toggle between expanding and collapsing FAB options
    private void toggleFABOptions() {
        if (!isExpanded) {
            // Expand FAB options
            option1Fab.setVisibility(View.VISIBLE);
            option2Fab.setVisibility(View.VISIBLE);
        } else {
            // Collapse FAB options
            option1Fab.setVisibility(View.GONE);
            option2Fab.setVisibility(View.GONE);
        }

        isExpanded = !isExpanded; // Toggle expanded state
    }
}
