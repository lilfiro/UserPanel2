package com.example.A_Soft;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProductionMainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ProductionReceiptAdapter adapter;
    private ProductionReceiptManager receiptManager;
    private FloatingActionButton addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uretim_main);

        receiptManager = new ProductionReceiptManager(this);
        setupRecyclerView();
        setupAddButton();
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.receiptsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ProductionReceiptAdapter(receiptManager.getAllReceipts(),
                // Click listener
                receipt -> {
                    Intent intent = new Intent(this, ProductionScanActivity.class);
                    intent.putExtra("RECEIPT_NO", receipt.getReceiptNo());
                    startActivity(intent);
                },
                // Long click listener
                receipt -> {
                    showDeleteDialog(receipt);
                    return true;
                }
        );

        recyclerView.setAdapter(adapter);
    }

    private void setupAddButton() {
        addButton = findViewById(R.id.addReceiptButton);
        addButton.setOnClickListener(v -> {
            // Get current operator from SharedPreferences
            SharedPreferences loginPrefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
            String currentOperator = loginPrefs.getString("logged_in_username", "");

            String newReceiptNo = receiptManager.generateNewReceiptNo();
            String creationTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            ProductionReceipt newReceipt = new ProductionReceipt(newReceiptNo, creationTime);
            receiptManager.saveReceipt(newReceipt);

            Intent intent = new Intent(this, ProductionScanActivity.class);
            intent.putExtra("RECEIPT_NO", newReceiptNo);
            intent.putExtra("CREATION_TIME", creationTime);
            intent.putExtra("OPERATOR", currentOperator);
            startActivity(intent);
        });
    }

    private void showDeleteDialog(ProductionReceipt receipt) {
        new AlertDialog.Builder(this)
                .setTitle("Fiş Sil")
                .setMessage("Bu fişi silmek istediğinizden emin misiniz?")
                .setPositiveButton("Evet", (dialog, which) -> {
                    receiptManager.deleteReceipt(receipt.getReceiptNo());
                    adapter.updateReceipts(receiptManager.getAllReceipts());
                })
                .setNegativeButton("Hayır", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        receiptManager.removeCompletedReceipts();
        adapter.updateReceipts(receiptManager.getAllReceipts());
    }
}

