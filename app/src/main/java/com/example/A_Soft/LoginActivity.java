package com.example.A_Soft;

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

public class LoginActivity extends AppCompatActivity {

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

        // Set greeting based on time of day
        updateGreetingBasedOnTime();

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                databaseHelper = new DatabaseHelper(LoginActivity.this);

                String username = usernameEditText.getText().toString();
                String password = passwordEditText.getText().toString();

                databaseHelper.checkUser(username, password, new DatabaseHelper.OnUserCheckListener() {
                    @Override
                    public void onUserCheck(boolean userExists) {
                        if (userExists) {
                            Toast.makeText(LoginActivity.this, "Giriş Başarılı", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                            startActivity(intent);
                            finish();
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
}
