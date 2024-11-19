package com.example.userpanel2;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameEditText, passwordEditText;
    private Button loginButton;
    private DatabaseHelper databaseHelper;
    ImageView imageView;
    TextView textView;
    int count = 0;

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

        databaseHelper = new DatabaseHelper();

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = usernameEditText.getText().toString();
                String password = passwordEditText.getText().toString();

                databaseHelper.checkUser(username, password, new DatabaseHelper.OnUserCheckListener() {
                    @Override
                    public void onUserCheck(boolean userExists) {
                        if (userExists) {
                            // basarili giriste HomePage yonlendirir
                            Toast.makeText(LoginActivity.this, "Giriş Başarılı", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                            startActivity(intent);
                            finish(); // Optional: LoginActivity'ye donmemek icin kapatilir
                        } else {
                            Toast.makeText(LoginActivity.this, "Geçersiz Giriş", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }


        });

        imageView.setOnTouchListener(new OnSwipeTouchListener(getApplicationContext()) {
            public void onSwipeRight() {
                if (count == 0) {
                    imageView.setImageResource(R.drawable.good_night_img);
                    textView.setText("Akşamlar");
                    count = 1;
                } else {
                    imageView.setImageResource(R.drawable.good_morning_img);
                    textView.setText("Sabahlar");
                    count = 0;
                }
            }

            public void onSwipeLeft() {
                if (count == 0) {
                    imageView.setImageResource(R.drawable.good_night_img);
                    textView.setText("Akşamlar");
                    count = 1;
                } else {
                    imageView.setImageResource(R.drawable.good_morning_img);
                    textView.setText("Sabahlar");
                    count = 0;
                }
            }


        });
    }
}
