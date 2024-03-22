package com.example.userpanel2;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ReceiptActivity extends AppCompatActivity {

    // Database connection details
    private static final String DB_URL = DatabaseHelper.DB_URL;
    private static final String DB_USER = DatabaseHelper.DB_USER;
    private static final String DB_PASSWORD = DatabaseHelper.DB_PASSWORD;

    private Spinner receiptTypeSpinner;
    private EditText receiptDateText;
    private Spinner receiptWarehouseSpinner;
    private EditText receiptIDText;
    private EditText receiptAddressText;
    private EditText desc1Text;
    private EditText desc2Text;
    private EditText desc3Text;
    private EditText desc4Text;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.receipt_create);

        // Initialize UI elements
        receiptTypeSpinner = findViewById(R.id.receiptTypeSpinner);
        receiptDateText = findViewById(R.id.receiptDateText);
        receiptWarehouseSpinner = findViewById(R.id.receiptWarehouseSpinner);
        receiptIDText = findViewById(R.id.receiptIDText);
        receiptAddressText = findViewById(R.id.receiptAddressText);
        desc1Text = findViewById(R.id.desc1Text);
        desc2Text = findViewById(R.id.desc2Text);
        desc3Text = findViewById(R.id.desc3Text);
        desc4Text = findViewById(R.id.desc4Text);
        saveButton = findViewById(R.id.saveButton);

        // Populate spinners with data from array resources
        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(this,
                R.array.receipt_types, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        receiptTypeSpinner.setAdapter(typeAdapter);

        ArrayAdapter<CharSequence> warehouseAdapter = ArrayAdapter.createFromResource(this,
                R.array.receipt_warehouses, android.R.layout.simple_spinner_item);
        warehouseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        receiptWarehouseSpinner.setAdapter(warehouseAdapter);

        // Set up date picker dialog for receiptDateText spinner
        receiptDateText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePickerDialog();
            }
        });

        // Generate and fill receipt ID
        generateAndFillReceiptID();

        // Set click listener for the save button
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveReceipt();
            }
        });
    }

    private void showDatePickerDialog() {
        // Set up MaterialDatePicker for date selection
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker();
        builder.setTitleText("Select Date");

        final MaterialDatePicker<Long> picker = builder.build();

        // Show date picker dialog
        picker.show(getSupportFragmentManager(), picker.toString());

        // Set listener to handle date selection
        picker.addOnPositiveButtonClickListener(new MaterialPickerOnPositiveButtonClickListener<Long>() {
            @Override
            public void onPositiveButtonClick(Long selection) {
                // Convert selection to date format if needed
                // Update the receiptDateText spinner with the selected date
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(selection);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String selectedDate = dateFormat.format(calendar.getTime());
                receiptDateText.setText(selectedDate);
            }
        });
    }

    private void generateAndFillReceiptID() {
        // Generate receipt ID
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
        String receiptID = sdf.format(new Date());
        receiptIDText.setText(receiptID);
    }

    private void saveReceipt() {
        // Retrieve data from UI elements
        String receiptType = receiptTypeSpinner.getSelectedItem().toString();
        String receiptDate = receiptDateText.getText().toString();
        String receiptWarehouse = receiptWarehouseSpinner.getSelectedItem().toString();
        String receiptID = receiptIDText.getText().toString();
        String receiptAddress = receiptAddressText.getText().toString();
        String desc1 = desc1Text.getText().toString();
        String desc2 = desc2Text.getText().toString();
        String desc3 = desc3Text.getText().toString();
        String desc4 = desc4Text.getText().toString();

        // Validate input (perform any necessary validation here)

        // Save receipt data to the database
        saveToDatabase(receiptType, receiptDate, receiptWarehouse, receiptID, receiptAddress, desc1, desc2, desc3, desc4);
    }

    private void saveToDatabase(String receiptType, String receiptDate, String receiptWarehouse,
                                String receiptID, String receiptAddress, String desc1, String desc2,
                                String desc3, String desc4) {
        new SaveReceiptTask().execute(receiptType, receiptDate, receiptWarehouse, receiptID, receiptAddress, desc1, desc2, desc3, desc4);
    }

    private class SaveReceiptTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String receiptType = params[0];
            String receiptDate = params[1];
            String receiptWarehouse = params[2];
            String receiptID = params[3];
            String receiptAddress = params[4];
            String desc1 = params[5];
            String desc2 = params[6];
            String desc3 = params[7];
            String desc4 = params[8];

            try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "INSERT INTO Receipts (ReceiptType, ReceiptDate, ReceiptWarehouse, ReceiptID, ReceiptAddress, Description1, Description2, Description3, Description4) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, receiptType);
                    statement.setString(2, receiptDate);
                    statement.setString(3, receiptWarehouse);
                    statement.setString(4, receiptID);
                    statement.setString(5, receiptAddress);
                    statement.setString(6, desc1);
                    statement.setString(7, desc2);
                    statement.setString(8, desc3);
                    statement.setString(9, desc4);
                    statement.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}
