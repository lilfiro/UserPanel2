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
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper {
    // SharedPreferences key constants
    private static final String PREF_NAME = "DatabaseConfig";
    private static final String KEY_SERVER_ADDRESS = "server_address";
    private static final String KEY_SERVER_PORT = "server_port";
    private static final String KEY_TIGER_DB_NAME = "Tiger_Database_name";
    private static final String KEY_ANATOLIASOFT_DB_NAME = "AnatoliaSoft_Database_name";
    private static final String KEY_USERNAME = "db_username";
    private static final String KEY_PASSWORD = "db_password";
    private static final String KEY_FIRM_NUMBER = "firm_number";
    private static final String KEY_PERIOD_NUMBER = "period_number";

    // Database configuration
    private String serverAddress, serverPort, tigerDatabaseName,
            anatoliaSoftDatabaseName, username, password,
            firmNumber, periodNumber;
    private final Handler handler;
    private final Context context;

    public DatabaseHelper(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        loadDatabaseConfig();
    }

    // Save database configuration to SharedPreferences
    public void saveConfiguration(String serverAddress, String serverPort,
                                  String tigerDatabaseName, String anatoliaSoftDatabaseName,
                                  String username, String password,
                                  String firmNumber, String periodNumber) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(KEY_SERVER_ADDRESS, serverAddress);
        editor.putString(KEY_SERVER_PORT, serverPort);
        editor.putString(KEY_TIGER_DB_NAME, tigerDatabaseName);
        editor.putString(KEY_ANATOLIASOFT_DB_NAME, anatoliaSoftDatabaseName);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_PASSWORD, password);
        editor.putString(KEY_FIRM_NUMBER, firmNumber);
        editor.putString(KEY_PERIOD_NUMBER, periodNumber);

        editor.commit(); // Changed from apply()

        // Update current instance variables
        loadDatabaseConfig();
    }
    // Load database configuration from SharedPreferences
    private void loadDatabaseConfig() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        serverAddress = prefs.getString(KEY_SERVER_ADDRESS, "192.168.1.113");
        serverPort = prefs.getString(KEY_SERVER_PORT, "1433");
        tigerDatabaseName = prefs.getString(KEY_TIGER_DB_NAME, "TIGERDB");
        anatoliaSoftDatabaseName = prefs.getString(KEY_ANATOLIASOFT_DB_NAME, "ANATOLIASOFT");
        username = prefs.getString(KEY_USERNAME, "sa");
        password = prefs.getString(KEY_PASSWORD, "sa");
        firmNumber = prefs.getString(KEY_FIRM_NUMBER, "001");
        periodNumber = prefs.getString(KEY_PERIOD_NUMBER, "01");
    }

    // Generate dynamic JDBC URL for Tiger Database
    public String getTigerJdbcUrl() {
        return String.format("jdbc:jtds:sqlserver://%s:%s/%s",
                serverAddress, serverPort, tigerDatabaseName);
    }

    // Generate dynamic JDBC URL for Anatoliasoft Database
    public String getAnatoliaSoftJdbcUrl() {
        return String.format("jdbc:jtds:sqlserver://%s:%s/%s",
                serverAddress, serverPort, anatoliaSoftDatabaseName);
    }

    // Get connection for Tiger Database
    public Connection getTigerConnection() throws SQLException {
        return DriverManager.getConnection(getTigerJdbcUrl(), username, password);
    }

    // Get connection for Anatoliasoft Database
    public Connection getAnatoliaSoftConnection() throws SQLException {
        return DriverManager.getConnection(getAnatoliaSoftJdbcUrl(), username, password);
    }

    // Dynamic table name generation for Tiger Database
    public String getTigerDbTableName(String baseTableName) {
        return String.format("%s.dbo.LG_%s_%s_%s", tigerDatabaseName, firmNumber, periodNumber, baseTableName);
    }

    public String getTigerDbItemsTableName(String baseTableName) {
        return String.format("%s.dbo.LG_%s_%s", tigerDatabaseName, firmNumber, baseTableName);
    }

    // Dynamic table name generation for Anatoliasoft Database
    public String getAnatoliaSoftTableName(String baseTableName) {
        return String.format("%s.dbo.%s", anatoliaSoftDatabaseName, baseTableName);
    }

    public int executeAnatoliaSoftUpdate(String query, String... params) throws SQLException {
        try (Connection connection = getAnatoliaSoftConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }

            return statement.executeUpdate();
        }
    }
    // User check method with dynamic connection (kept from previous implementation)
    public void checkUser(String username, String password, OnUserCheckListener listener) {
        new Thread(() -> {
            performCheckUser(username, password, listener);
        }).start();
    }

    // Perform user check with dynamic connection
    private void performCheckUser(String checkUsername, String checkPassword, OnUserCheckListener listener) {
        try (Connection connection = getAnatoliaSoftConnection()) {
            String databaseName = getAnatoliaSoftDatabaseName();

            String sql = String.format(
                    "SELECT DISTINCT m.MENUREF FROM %s.dbo.A_CAPIUSER u " +
                            "JOIN %s.dbo.A_CAPIRIGHT r ON u.NR = r.USERNR " +
                            "JOIN %s.dbo.A_MODULES m ON r.MENUREF = m.MENUREF " +
                            "WHERE u.LOGOUSERNAME = ? AND u.LOGOUSERPASS = ?",
                    databaseName, databaseName, databaseName);

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, checkUsername);
                statement.setString(2, checkPassword);

                try (ResultSet resultSet = statement.executeQuery()) {
                    List<Integer> moduleRights = new ArrayList<>();
                    boolean hasResults = false;

                    while (resultSet.next()) {
                        hasResults = true;
                        moduleRights.add(resultSet.getInt("MENUREF"));
                    }

                    boolean finalHasResults = hasResults;
                    handler.post(() -> listener.onUserCheck(finalHasResults, moduleRights));
                }
            }
        } catch (SQLException e) {
            Log.e("DatabaseHelper", "Error checking user", e);
            handler.post(() -> listener.onUserCheck(false, new ArrayList<>()));
        }
    }

    public interface OnUserCheckListener {
        void onUserCheck(boolean userExists, List<Integer> moduleRights);
    }
    public String getLastSlipNumber() {
        String lastSlipNumber = "00000";
        String query = "SELECT TOP 1 SLIPNR FROM AST_PRODUCTION_SLIPS ORDER BY CAST(SLIPNR AS bigint) DESC";

        try (Connection conn = getAnatoliaSoftConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    lastSlipNumber = rs.getString("SLIPNR");
                }
            }
        } catch (SQLException e) {
            Log.e("DatabaseHelper", "Error getting last slip number", e);
        }

        // Generate next number with leading zeros
        int nextNumber = Integer.parseInt(lastSlipNumber) + 1;
        return String.format("%05d", nextNumber);
    }

    // Getters for configuration values
    public String getServerAddress() { return serverAddress; }
    public String getServerPort() { return serverPort; }
    public String getTigerDatabaseName() { return tigerDatabaseName; }
    public String getAnatoliaSoftDatabaseName() { return anatoliaSoftDatabaseName; }
    public String getUsername() { return username; }
    public String getFirmNumber() { return firmNumber; }
    public String getPeriodNumber() { return periodNumber; }
    public String getPassword() { return password; }


}