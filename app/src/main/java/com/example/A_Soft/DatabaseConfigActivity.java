package com.example.A_Soft;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class DatabaseConfigActivity extends AppCompatActivity {
    public EditText etServerAddress;
    public EditText etServerPort;
    public EditText etDatabaseName;
    public EditText etUsername;
    public EditText etPassword;
    public EditText etFirmNumber;
    public EditText etPeriodNumber;
    public Button btnSaveConfig;

    public DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

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
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "Configuration saved successfully", Toast.LENGTH_SHORT).show();

        // Optionally, finish the activity or do something else
        finish();
    }
}