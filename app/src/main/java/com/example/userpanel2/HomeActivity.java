package com.example.userpanel2;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class HomeActivity extends AppCompatActivity {
    private FloatingActionButton fab, option1Fab, option2Fab;
    private boolean isExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);

        fab = findViewById(R.id.FloatBt);
        option1Fab = findViewById(R.id.option1);
        option2Fab = findViewById(R.id.option2);

        // action button icin listener
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFABOptions(); // Toggle
            }
        });

        // ilk tus icin listener
        option1Fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ReceiptActivity yonlendirir
                startActivity(new Intent(HomeActivity.this, ReceiptActivity.class));
            }
        });
    }

    // acilip kapanma (sadece bir illusion :D) animasyonunu halleden fonksiyon
    private void toggleFABOptions() {
        if (!isExpanded) {
            option1Fab.setVisibility(View.VISIBLE);
            option2Fab.setVisibility(View.VISIBLE);
        } else {
            option1Fab.setVisibility(View.GONE);
            option2Fab.setVisibility(View.GONE);
        }

        isExpanded = !isExpanded; // Toggle expanded state
    }
}
