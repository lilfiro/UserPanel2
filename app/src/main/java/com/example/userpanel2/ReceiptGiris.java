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

public class ReceiptGiris extends Fragment {

    // DatabaseHelper classindan, database girisini saglayan bilgileri ceker
    private static final String DB_URL = DatabaseHelper.DB_URL;
    private static final String DB_USER = DatabaseHelper.DB_USER;
    private static final String DB_PASSWORD = DatabaseHelper.DB_PASSWORD;

    private Spinner receiptTypeSpinner, receiptWarehouseSpinner;
    private EditText receiptDateText, receiptIDText, receiptAddressText, desc1Text, desc2Text, desc3Text, desc4Text;
    private Button saveButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.receipt_giris, container, false);

        // Initialize UI elements
        receiptTypeSpinner = rootView.findViewById(R.id.receiptTypeSpinner);
        receiptDateText = rootView.findViewById(R.id.receiptDateText);
        receiptWarehouseSpinner = rootView.findViewById(R.id.receiptWarehouseSpinner);
        receiptIDText = rootView.findViewById(R.id.receiptIDText);
        receiptAddressText = rootView.findViewById(R.id.receiptAddressText);
        desc1Text = rootView.findViewById(R.id.desc1Text);
        desc2Text = rootView.findViewById(R.id.desc2Text);
        desc3Text = rootView.findViewById(R.id.desc3Text);
        desc4Text = rootView.findViewById(R.id.desc4Text);
        saveButton = rootView.findViewById(R.id.saveButton);

        // spinner elementinin kullanacagi secenekleri arrays.xml ismindeki degerler dosyasindan cekiyoruz
        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.receipt_types, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        receiptTypeSpinner.setAdapter(typeAdapter);

        ArrayAdapter<CharSequence> warehouseAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.receipt_warehouses, android.R.layout.simple_spinner_item);
        warehouseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        receiptWarehouseSpinner.setAdapter(warehouseAdapter);

        receiptDateText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePickerDialog();
            }
        });

        // fis id olusturma fonks
        generateAndFillReceiptID();

        // click listener
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveReceipt();
            }
        });

        return rootView;
    }


    private void showDatePickerDialog() {
        // MaterialDatePicker ile tarih secme popupu cikar
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker();
        builder.setTitleText("Tarih Seçin");

        final MaterialDatePicker<Long> picker = builder.build();

        // tarih secme diyalogu
        picker.show(getParentFragmentManager(), picker.toString());

        // tarih secilmesi icin listener
        picker.addOnPositiveButtonClickListener(new MaterialPickerOnPositiveButtonClickListener<Long>() {
            @Override
            public void onPositiveButtonClick(Long selection) {
                // secilen tarihi uygun formata cevirir
                // receiptDateText spinner elementini gunceller
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(selection);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String selectedDate = dateFormat.format(calendar.getTime());
                receiptDateText.setText(selectedDate);
            }
        });
    }

    private void generateAndFillReceiptID() {
        // ID olusturma fonks
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
        String receiptID = sdf.format(new Date());
        receiptIDText.setText(receiptID);
    }

    private void saveReceipt() {
        // UI elementlerinden verileri alma fonks
        String receiptType = receiptTypeSpinner.getSelectedItem().toString();
        String receiptDate = receiptDateText.getText().toString();
        String receiptWarehouse = receiptWarehouseSpinner.getSelectedItem().toString();
        String receiptID = receiptIDText.getText().toString();
        String receiptAddress = receiptAddressText.getText().toString();
        String desc1 = desc1Text.getText().toString();
        String desc2 = desc2Text.getText().toString();
        String desc3 = desc3Text.getText().toString();
        String desc4 = desc4Text.getText().toString();

        // Giris dogrulama methodu eklenebilir

        // verilerini db kaydeder
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