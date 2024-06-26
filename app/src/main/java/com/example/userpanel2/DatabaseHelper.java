package com.example.userpanel2;

import android.os.Handler;
import android.os.Looper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseHelper {

    // DB Connection settings
    protected static final String DB_URL = "jdbc:jtds:sqlserver://10.0.2.2/AndroidTest";
    protected static final String DB_USER = "androidemu";
    protected static final String DB_PASSWORD = "AndroidEmu123";

    private final Handler handler;

    public DatabaseHelper() {
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void checkUser(String username, String password, OnUserCheckListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean userExists = performCheckUser(username, password);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onUserCheck(userExists);
                        }
                    }
                });
            }
        }).start();
    }

    private boolean performCheckUser(String username, String password) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username);
                statement.setString(2, password);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next(); // Return true if user exists, false otherwise
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public interface OnUserCheckListener {
        void onUserCheck(boolean userExists);
    }
}
