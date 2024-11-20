package com.example.A_Soft;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class FragmentIslem extends Fragment {

    // Declare item as a member variable
    private Item item;

    // Method to update item
    public void updateItem(Item newItem) {
        this.item = newItem;
    }

    private static final String DB_URL = DatabaseHelper.DB_URL;
    private static final String DB_USER = DatabaseHelper.DB_USER;
    private static final String DB_PASSWORD = DatabaseHelper.DB_PASSWORD;

    private Button qr_button;
    private TextView qrResultTextView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_islem, container, false);

        qr_button = rootView.findViewById(R.id.qr_button);
        qrResultTextView = rootView.findViewById(R.id.qrResultTextView);

        qr_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkCameraPermissionAndStartScanner();
            }
        });

        return rootView;
    }

    private void checkCameraPermissionAndStartScanner() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startQRCodeScanner();
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startQRCodeScanner();
                } else {
                    qrResultTextView.setText("Camera permission is required to scan QR codes.");
                }
            });

    private void startQRCodeScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan a QR code");
        options.setCameraId(0);  // Use a specific camera of the device
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(true);
        barcodeLauncher.launch(options);
    }

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        if (result != null && result.getContents() != null) {
            qrResultTextView.setText(result.getContents());
            // Process the scanned QR code content
            processQRCodeContent(result.getContents());
        }
    });

    private void processQRCodeContent(String qrContent) {
        Log.d("FragmentIslem", "processQRCodeContent: QR code content processed: " + qrContent);
        Log.d("FragmentIslem", "processQRCodeContent: Calling showQRCodeResultDialog method");

        // Extract the actual item code from the QR content
        String actualItemCode = extractItemCode(qrContent);
        Log.d("FragmentIslem", "processQRCodeContent: Extracted item code: " + actualItemCode);

        // Initialize the item object using FetchItemDetailsTask
        new FetchItemDetailsTask().execute(actualItemCode);
    }

    private String extractItemCode(String qrContent) {
        // Assuming the actual item code is the last part of the QR content after spaces and metadata
        String[] parts = qrContent.split("\\s+");  // Split by whitespace
        return parts[parts.length - 1];  // Return the last part
    }

    private class FetchItemDetailsTask extends AsyncTask<String, Void, List<Item>> {

        @Override
        protected List<Item> doInBackground(String... params) {
            String barcode = params[0];
            List<Item> items = new ArrayList<>();

            try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                Log.d("FetchItemDetailsTask", "Database connection established to URL: " + DB_URL);

                String sql = "SELECT * FROM Items WHERE barcode = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, barcode);
                    Log.d("FetchItemDetailsTask", "PreparedStatement created with query: " + sql + " and barcode: " + barcode);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        Log.d("FetchItemDetailsTask", "Query executed, processing result set");
                        while (resultSet.next()) {
                            Item item = new Item();
                            item.setName(resultSet.getString("itemName"));
                            item.setQuantity(resultSet.getInt("yearOfManufacture"));
                            item.setDescription(resultSet.getString("barcode"));
                            // Add other fields as necessary
                            items.add(item);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            if (items.isEmpty()) {
                Log.e("FetchItemDetailsTask", "Item not found in the database.");
            }

            return items;
        }

        @Override
        protected void onPostExecute(List<Item> items) {
            if (!items.isEmpty()) {
                // Pass the first item or let the user choose
                showQRCodeResultDialog(items.get(0));
            } else {
                qrResultTextView.setText("Item not found in the database.");
            }
        }
    }

    private void showQRCodeResultDialog(Item item) {
        if (item != null) {
            Log.d("FragmentIslem", "showQRCodeResultDialog called with item: " +
                    "Name: " + item.getName() + ", Quantity: " + item.getQuantity() + ", Description: " + item.getDescription());

            QRCodeDialogFragment dialogFragment = QRCodeDialogFragment.newInstance(
                    item.getName(),
                    item.getQuantity(),
                    item.getDescription()
            );

            FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
            fragmentManager.beginTransaction()
                    .add(R.id.fragment_container_view, dialogFragment, "fragment_qr_code_dialog")
                    .addToBackStack(null)
                    .commit();
            Log.d("FragmentIslem", "showQRCodeResultDialog: Fragment transaction committed");
        } else {
            Log.e("FragmentIslem", "showQRCodeResultDialog: Item object is null");
        }
    }

    public void onQRCodeResultConfirmed(String itemName, int itemQuantity, String itemDescription) {
        Log.d("FragmentIslem", "onQRCodeResultConfirmed: Name: " + itemName + ", Quantity: " + itemQuantity + ", Description: " + itemDescription);
        // Save the details to the ReceiptDetails table
        saveToReceiptDetails(itemName, itemQuantity, itemDescription);
    }

    private void saveToReceiptDetails(String itemName, int itemQuantity, String itemDescription) {
        // Save the item details to the ReceiptDetails table
        // This would typically involve an AsyncTask or similar to perform the database operation
        new SaveReceiptDetailsTask().execute(itemName, itemQuantity, itemDescription);
    }

    private class SaveReceiptDetailsTask extends AsyncTask<Object, Void, Void> {
        @Override
        protected Void doInBackground(Object... params) {
            String itemName = (String) params[0];
            int itemQuantity = (Integer) params[1];
            String itemDescription = (String) params[2];

            try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "INSERT INTO ReceiptDetails (receiptID, itemName, itemQuantity, itemDescription) VALUES (?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    // Assuming receiptID is obtained from somewhere, e.g., from the main receipt entry
                    String receiptID = getReceiptID();
                    statement.setString(1, receiptID);
                    statement.setString(2, itemName);
                    statement.setInt(3, itemQuantity);
                    statement.setString(4, itemDescription);
                    statement.executeUpdate();
                    Log.d("FragmentIslem", "SaveReceiptDetailsTask: Details saved: Name: " + itemName + ", Quantity: " + itemQuantity + ", Description: " + itemDescription);
                }
            } catch (SQLException e) {
                Log.e("FragmentIslem", "Error saving receipt details", e);
            }

            return null;
        }

        private String getReceiptID() {
            // Return the current receipt ID
            // This method should retrieve the current receipt ID from the main receipt entry
            return "current_receipt_id"; // Replace with actual logic
        }
    }
}
