package com.example.A_Soft;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class DatabaseConfigActivity extends AppCompatActivity {
    public EditText etServerAddress, etServerPort, etDatabaseName, etUsername, etPassword, etFirmNumber, etPeriodNumber;
    public Button btnSaveConfig;

    public DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.db_settings);

        // Initialize DatabaseHelper
        databaseHelper = new DatabaseHelper(this);

        // Initialize views
        etServerAddress = findViewById(R.id.et_server_address);
        etServerPort = findViewById(R.id.et_server_port);
        etDatabaseName = findViewById(R.id.et_database_name);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etFirmNumber = findViewById(R.id.et_firm_number);
        etPeriodNumber = findViewById(R.id.et_period_number);
        btnSaveConfig = findViewById(R.id.btn_save_config);

        // Load existing configuration
        loadCurrentConfiguration();

        // Set up save button
        btnSaveConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfiguration();
            }
        });
    }

    private void loadCurrentConfiguration() {
        etServerAddress.setText(databaseHelper.getServerAddress());
        etServerPort.setText(databaseHelper.getServerPort());
        etUsername.setText(databaseHelper.getUsername());
        etFirmNumber.setText(databaseHelper.getFirmNumber());
        etPeriodNumber.setText(databaseHelper.getPeriodNumber());
    }

    private void saveConfiguration() {
        // Validate inputs (add more robust validation as needed)
        String serverAddress = etServerAddress.getText().toString().trim();
        String serverPort = etServerPort.getText().toString().trim();
        String databaseName = etDatabaseName.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String firmNumber = etFirmNumber.getText().toString().trim();
        String periodNumber = etPeriodNumber.getText().toString().trim();

        // Validate that required fields are not empty
        if (serverAddress.isEmpty() || serverPort.isEmpty() ||
                username.isEmpty() || firmNumber.isEmpty() || periodNumber.isEmpty()) {
            Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save configuration
        databaseHelper.saveConfiguration(
                serverAddress,
                serverPort,
                databaseName,
                username,
                password,
                firmNumber,
                periodNumber
        );

        // Show success message
        Toast.makeText(this, "Yapılandırılma başarılı", Toast.LENGTH_SHORT).show();

        // Optionally, finish the activity or do something else
        finish();
    }
}