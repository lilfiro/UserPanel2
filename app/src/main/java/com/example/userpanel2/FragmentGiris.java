package com.example.userpanel2;

import android.os.AsyncTask;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FragmentGiris extends Fragment {

    // DatabaseHelper classindan, database girisini saglayan bilgileri ceker
    private static final String DB_URL = DatabaseHelper.DB_URL;
    private static final String DB_USER = DatabaseHelper.DB_USER;
    private static final String DB_PASSWORD = DatabaseHelper.DB_PASSWORD;

    private Spinner receiptTypeSpinner, receiptWarehouseSpinner;
    private TextView receiptDateText;
    private EditText receiptIDText, desc1Text, desc2Text;
    private Button saveButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.receipt_giris, container, false);

        // Initialize UI elements
        receiptTypeSpinner = rootView.findViewById(R.id.receiptTypeSpinner);
        receiptDateText = rootView.findViewById(R.id.receiptDateText);
        receiptWarehouseSpinner = rootView.findViewById(R.id.receiptWarehouseSpinner);
        receiptIDText = rootView.findViewById(R.id.receiptIDText);
        desc1Text = rootView.findViewById(R.id.desc1Text);
        desc2Text = rootView.findViewById(R.id.desc2Text);
        saveButton = rootView.findViewById(R.id.saveButton);

        // spinner elementinin kullanacagi secenekleri arrays.xml degerler dosyasindan çeker
        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.receipt_types, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        receiptTypeSpinner.setAdapter(typeAdapter);

        ArrayAdapter<CharSequence> warehouseAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.receipt_warehouses, android.R.layout.simple_spinner_item);
        warehouseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        receiptWarehouseSpinner.setAdapter(warehouseAdapter);

        // fis id olusturma fonks
        generateAndFillReceiptID();

        // Set the current date and time to receiptDateText
        updateReceiptDate();

        // click listener
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveReceipt();
            }
        });

        return rootView;
    }

    private void updateReceiptDate() {
        //Bu tarih, sadece görüntüdeki tarihtir, sql tarafına giden tarih: KAYDET tuşuna basıldığında aktif olur.
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentDateAndTime = sdf.format(new Date());
        receiptDateText.setText(currentDateAndTime);
    }


    private void generateAndFillReceiptID() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
        String receiptID = sdf.format(new Date());
        receiptIDText.setText(receiptID);
    }

    private void saveReceipt() {
        // Update the receipt date with the current date and time
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentDateAndTime = sdf.format(new Date());
        receiptDateText.setText(currentDateAndTime);

        // UI elementlerinden verileri alır
        String receiptType = receiptTypeSpinner.getSelectedItem().toString();
        String receiptDate = receiptDateText.getText().toString();
        String receiptWarehouse = receiptWarehouseSpinner.getSelectedItem().toString();
        String receiptID = receiptIDText.getText().toString();
        String desc1 = desc1Text.getText().toString();
        String desc2 = desc2Text.getText().toString();

        // verilerini db kaydeder
        saveToDatabase(receiptType, receiptDate, receiptWarehouse, receiptID, desc1, desc2);
    }

    private void saveToDatabase(String receiptType, String receiptDate, String receiptWarehouse, String receiptID, String desc1, String desc2) {
        new SaveReceiptTask().execute(receiptType, receiptDate, receiptWarehouse, receiptID, desc1, desc2);
    }

    private class SaveReceiptTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String receiptType = params[0];
            String receiptDate = params[1];
            String receiptWarehouse = params[2];
            String receiptID = params[3];
            String desc1 = params[4];
            String desc2 = params[5];

            try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "INSERT INTO Receipts (ReceiptType, ReceiptDate, ReceiptWarehouse, ReceiptID, Description1, Description2) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, receiptType);
                    statement.setString(2, receiptDate);
                    statement.setString(3, receiptWarehouse);
                    statement.setString(4, receiptID);
                    statement.setString(5, desc1);
                    statement.setString(6, desc2);
                    statement.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
