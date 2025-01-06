package com.example.A_Soft;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.SharedPreferences;

public class LoginActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "LoginPrefs";
    public static final String SAVED_USERNAME_KEY = "saved_username";

    private static final String LAST_LOGIN_DAY_KEY = "lastLoginDay"; // Store day instead of timestamp

    private EditText usernameEditText, passwordEditText;
    private Button loginButton, settingsButton;
    private DatabaseHelper databaseHelper;
    private ImageView imageView;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.login_activity);

        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);
        usernameEditText = findViewById(R.id.usernamelb);
        passwordEditText = findViewById(R.id.passwordlb);
        loginButton = findViewById(R.id.loginbt);
        settingsButton = findViewById(R.id.settingsbt);

        databaseHelper = new DatabaseHelper(this);

        // Check if the user is already logged in
        if (isUserLoggedIn()) {
            navigateToHome();
            return;
        }
        // Load saved username if exists
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUsername = prefs.getString(SAVED_USERNAME_KEY, "");
        if (!savedUsername.isEmpty()) {
            usernameEditText.setText(savedUsername);
            usernameEditText.setEnabled(false); // Optional: prevent editing
            passwordEditText.requestFocus(); // Focus on password field
        }
        // Set greeting based on time of day
        updateGreetingBasedOnTime();

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = usernameEditText.getText().toString();
                String password = passwordEditText.getText().toString();

                databaseHelper.checkUser(username, password, new DatabaseHelper.OnUserCheckListener() {
                    @Override
                    public void onUserCheck(boolean userExists, List<Integer> moduleRights) {
                        if (userExists) {
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(SAVED_USERNAME_KEY, username);

                            // Also store as logged_in_username for consistency
                            SharedPreferences loginPrefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
                            SharedPreferences.Editor loginEditor = loginPrefs.edit();
                            loginEditor.putString("logged_in_username", username);
                            loginEditor.apply();

                            // Convert module rights to Set<String> for storage
                            Set<String> moduleRightsSet = new HashSet<>();
                            for (Integer moduleRef : moduleRights) {
                                moduleRightsSet.add(String.valueOf(moduleRef));
                            }
                            editor.putStringSet("module_rights", moduleRightsSet);

                            editor.apply();

                            navigateToHome();
                        } else {
                            Toast.makeText(LoginActivity.this, "Geçersiz Giriş", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });


        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, DatabaseConfigActivity.class);
                startActivity(intent);
            }
        });
    }

    private void updateGreetingBasedOnTime() {
        // Get current hour
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (hour >= 6 && hour < 18) {
            // Morning: Between 6 AM and 6 PM
            imageView.setImageResource(R.drawable.good_morning_img);
            textView.setText("Günler");
        } else {
            // Night: Between 6 PM and 6 AM
            imageView.setImageResource(R.drawable.good_night_img);
            textView.setText("Akşamlar");
        }
    }

    private boolean isUserLoggedIn() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int lastLoginDay = prefs.getInt(LAST_LOGIN_DAY_KEY, -1); // Retrieve last login day
        int currentDay = getCurrentDay();

        // Check if the login day matches the current day
        return lastLoginDay == currentDay;
    }

    private void saveLoginDay() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(LAST_LOGIN_DAY_KEY, getCurrentDay()); // Save the current day
        editor.apply();
    }
    // Add a method to clear saved username (call this when logging out)
    public static void clearSavedUsername(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(SAVED_USERNAME_KEY);
        editor.apply();
    }

    private int getCurrentDay() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.DAY_OF_YEAR); // Get the day of the year
    }

    private void navigateToHome() {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
