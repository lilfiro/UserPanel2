package com.example.A_Soft;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseHelper {
    // SharedPreferences key constants
    private static final String PREF_NAME = "DatabaseConfig";
    private static final String KEY_SERVER_ADDRESS = "server_address";
    private static final String KEY_SERVER_PORT = "server_port";
    private static final String KEY_DB_NAME = "database_name";
    private static final String KEY_USERNAME = "db_username";
    private static final String KEY_PASSWORD = "db_password";
    private static final String KEY_FIRM_NUMBER = "firm_number";
    private static final String KEY_PERIOD_NUMBER = "period_number";

    // Database configuration
    private String serverAddress;
    private String serverPort;
    private String databaseName;
    private String username;
    private String password;
    private String firmNumber;
    private String periodNumber;

    private final Handler handler;
    private final Context context;

    public DatabaseHelper(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        loadDatabaseConfig();
    }

    // Save database configuration to SharedPreferences
    public void saveConfiguration(String serverAddress, String serverPort,
                                  String databaseName, String username,
                                  String password, String firmNumber,
                                  String periodNumber) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(KEY_SERVER_ADDRESS, serverAddress);
        editor.putString(KEY_SERVER_PORT, serverPort);
        editor.putString(KEY_DB_NAME, databaseName);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_PASSWORD, password);
        editor.putString(KEY_FIRM_NUMBER, firmNumber);
        editor.putString(KEY_PERIOD_NUMBER, periodNumber);

        editor.apply();

        // Update current instance variables
        loadDatabaseConfig();
    }

    // Load database configuration from SharedPreferences
    private void loadDatabaseConfig() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        serverAddress = prefs.getString(KEY_SERVER_ADDRESS, "192.168.1.113");
        serverPort = prefs.getString(KEY_SERVER_PORT, "1433");
        databaseName = prefs.getString(KEY_DB_NAME, "AndroidTest");
        username = prefs.getString(KEY_USERNAME, "androidemu");
        password = prefs.getString(KEY_PASSWORD, "AndroidEmu123");
        firmNumber = prefs.getString(KEY_FIRM_NUMBER, "001");
        periodNumber = prefs.getString(KEY_PERIOD_NUMBER, "01");
    }

    // Generate dynamic JDBC URL
    String getJdbcUrl() {
        return String.format("jdbc:jtds:sqlserver://%s:%s/%s",
                serverAddress, serverPort, databaseName);
    }

    // Get connection with dynamically loaded credentials
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl(), username, password);
    }

    // Dynamic table and schema generation methods
    public String getTigerDbTableName(String baseTableName) {
        return String.format("TIGERDB.dbo.LG_%s_%s_%s", firmNumber, periodNumber, baseTableName);
    }

    public String getAnatoliaSoftTableName(String baseTableName) {
        return String.format("ANATOLIASOFT.dbo.%s", baseTableName);
    }

    // User check method with dynamic connection
    public void checkUser(String username, String password, OnUserCheckListener listener) {
        new Thread(() -> {
            boolean userExists = performCheckUser(username, password);
            handler.post(() -> {
                if (listener != null) {
                    listener.onUserCheck(userExists);
                }
            });
        }).start();
    }

    // Perform user check with dynamic connection
    private boolean performCheckUser(String checkUsername, String checkPassword) {
        try (Connection connection = getConnection()) {
            String sql = "SELECT * FROM AndroidTest.dbo.users WHERE username = ? AND password = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, checkUsername);
                statement.setString(2, checkPassword);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        } catch (SQLException e) {
            Log.e("DatabaseHelper", "Error checking user", e);
            return false;
        }
    }

    // Getters for configuration values
    public String getServerAddress() { return serverAddress; }
    public String getServerPort() { return serverPort; }
    public String getDatabaseName() { return databaseName; }
    public String getUsername() { return username; }
    public String getFirmNumber() { return firmNumber; }
    public String getPeriodNumber() { return periodNumber; }

    public String getPassword() {return password;    }

    public interface OnUserCheckListener {
        void onUserCheck(boolean userExists);
    }
}