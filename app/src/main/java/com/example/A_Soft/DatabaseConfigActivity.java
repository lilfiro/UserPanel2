package com.example.A_Soft;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfigActivity extends AppCompatActivity {
    public EditText etServerAddress, etServerPort, etTigerDatabaseName, etAnatoliaSoftDatabaseName, etUsername, etPassword, etFirmNumber, etPeriodNumber;
    public Button btnSaveConfig, btnTestConnection;
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
        etTigerDatabaseName = findViewById(R.id.et_TigerDatabase_name);
        etAnatoliaSoftDatabaseName = findViewById(R.id.et_AnatoliaSoft_Database_name);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etFirmNumber = findViewById(R.id.et_firm_number);
        etPeriodNumber = findViewById(R.id.et_period_number);
        btnSaveConfig = findViewById(R.id.btn_save_config);
        btnTestConnection = findViewById(R.id.btn_test_connection); // Add this button in your XML layout


        // Load existing configuration
        loadCurrentConfiguration();

        // Set up save button
        btnSaveConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfiguration();
            }
        });

        // Set up test connection button
        btnTestConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testDatabaseConnection();
            }
        });
    }



    private void loadCurrentConfiguration() {
        etServerAddress.setText(databaseHelper.getServerAddress());
        etServerPort.setText(databaseHelper.getServerPort());
        etTigerDatabaseName.setText(databaseHelper.getTigerDatabaseName());
        etAnatoliaSoftDatabaseName.setText(databaseHelper.getAnatoliaSoftDatabaseName());
        etUsername.setText(databaseHelper.getUsername());
        etFirmNumber.setText(databaseHelper.getFirmNumber());
        etPeriodNumber.setText(databaseHelper.getPeriodNumber());
    }

    private void saveConfiguration() {
        // Validate inputs (add more robust validation as needed)
        String serverAddress = etServerAddress.getText().toString().trim();
        String serverPort = etServerPort.getText().toString().trim();
        String TigerDatabaseName = etTigerDatabaseName.getText().toString().trim();
        String AnatoliaSoftDatabaseName = etAnatoliaSoftDatabaseName.getText().toString().trim();
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
        databaseHelper.saveConfiguration(serverAddress, serverPort, TigerDatabaseName, AnatoliaSoftDatabaseName, username, password, firmNumber, periodNumber);

        // Show success message
        Toast.makeText(this, "Yapılandırılma başarılı", Toast.LENGTH_SHORT).show();

        // Optionally, finish the activity or do something else
        finish();
    }


    private void testDatabaseConnection() {
        String serverAddress = etServerAddress.getText().toString().trim();
        String serverPort = etServerPort.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String databaseName = etTigerDatabaseName.getText().toString().trim();

        if (serverAddress.isEmpty() || serverPort.isEmpty() || username.isEmpty() || password.isEmpty() || databaseName.isEmpty()) {
            showMessageDialog("Uyarı", "Lütfen tüm alanları doldurun.");
            return;
        }

        new Thread(() -> {
            Connection connection = null;
            try {
                String connectionUrl = "jdbc:jtds:sqlserver://" + serverAddress + ":" + serverPort + "/" + databaseName;
                connection = DriverManager.getConnection(connectionUrl, username, password);

                runOnUiThread(() -> showMessageDialog("Başarılı", "Veritabanı bağlantıları başarıyla sonuçlandı."));
            } catch (SQLException e) {
                String errorMessage = getDetailedError(e);
                runOnUiThread(() -> showMessageDialog("Bağlantı Başarısız", errorMessage));
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                    }
                }
            }
        }).start();
    }

    private String getDetailedError(SQLException e) {
        StringBuilder errorDetails = new StringBuilder("Bağlantı sırasında bir hata meydana geldi:\n");
        errorDetails.append(e.getMessage());

        // Optionally include more detailed error info
        Throwable cause = e.getCause();
        if (cause != null) {
            errorDetails.append("\nSebep: ").append(cause.getMessage());
        }

        return errorDetails.toString();
    }

    private void showMessageDialog(String title, String message) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Tamam", null)
                    .show();
        });
    }
}