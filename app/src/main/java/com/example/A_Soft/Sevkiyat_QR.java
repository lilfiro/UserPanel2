package com.example.A_Soft;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Sevkiyat_QR extends Fragment {
    private static final String TAG = "Sevkiyat_QR";
    private static final Pattern QR_SERIAL_PATTERN = Pattern.compile("KAREKODNO_([^|]+)");

    private CameraSourcePreview cameraPreview;
    private CameraSource cameraSource;
    private ExecutorService executorService;
    private ReceiptItemManager itemManager;
    private TextView scanStatusTextView;
    private Button confirmReceiptButton;
    private String currentReceiptNo;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sevkiyat_receipt_qr, container, false);

        // Initialize views
        cameraPreview = view.findViewById(R.id.camera_preview);
        scanStatusTextView = view.findViewById(R.id.scan_status);
        confirmReceiptButton = view.findViewById(R.id.confirm_receipt_button);

        // Get receipt number from arguments
        if (getArguments() != null) {
            currentReceiptNo = getArguments().getString("FICHENO");
        }

        executorService = Executors.newSingleThreadExecutor();
        itemManager = new ReceiptItemManager(currentReceiptNo);

        setupBarcodeDetector();
        setupConfirmButton();
        loadReceiptItems();

        return view;
    }

    private void setupConfirmButton() {
        confirmReceiptButton.setOnClickListener(v -> {
            if (itemManager.areAllItemsScanned()) {
                updateReceiptStatus();
            } else {
                showToast("All items must be scanned before confirming");
            }
        });
    }

    private void setupBarcodeDetector() {
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(requireContext())
                .setBarcodeFormats(Barcode.QR_CODE)
                .build();

        if (!barcodeDetector.isOperational()) {
            showToast("Barcode detection is not operational");
            return;
        }

        cameraSource = new CameraSource.Builder(requireContext(), barcodeDetector)
                .setRequestedPreviewSize(640, 480)
                .setAutoFocusEnabled(true)
                .build();

        cameraPreview.setCameraSource(cameraSource);

        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {}

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {
                if (detections != null && detections.getDetectedItems().size() > 0) {
                    Barcode barcode = detections.getDetectedItems().valueAt(0);
                    processQRCode(barcode.displayValue);
                }
            }
        });
    }

    private void processQRCode(String qrCodeData) {
        try {
            Matcher serialMatcher = QR_SERIAL_PATTERN.matcher(qrCodeData);
            if (!serialMatcher.find()) {
                showToast("Invalid QR code format");
                return;
            }

            String itemSerialNumber = serialMatcher.group(1);

            executorService.submit(() -> {
                ReceiptItemManager.ScanResult result = itemManager.processScannedItem(itemSerialNumber);
                requireActivity().runOnUiThread(() -> handleScanResult(result, itemSerialNumber));
            });

        } catch (Exception e) {
            Log.e(TAG, "QR Code processing error", e);
            showToast("Error processing QR code");
        }
    }

    private void handleScanResult(ReceiptItemManager.ScanResult result, String serialNumber) {
        switch (result) {
            case ALREADY_SCANNED:
                showToast("Item already scanned: " + serialNumber);
                break;
            case ITEM_NOT_IN_RECEIPT:
                showToast("Item not in receipt: " + serialNumber);
                break;
            case SUCCESS:
                showToast("Item scanned successfully: " + serialNumber);
                updateScanStatus();
                break;
        }
    }

    private void updateScanStatus() {
        String status = String.format("Scanned: %d/%d items",
                itemManager.getScannedCount(),
                itemManager.getTotalItems());
        scanStatusTextView.setText(status);

        confirmReceiptButton.setEnabled(itemManager.areAllItemsScanned());
    }

    private void loadReceiptItems() {
        executorService.submit(() -> {
            itemManager.loadReceiptItems();
            requireActivity().runOnUiThread(this::updateScanStatus);
        });
    }

    private void updateReceiptStatus() {
        executorService.submit(() -> {
            try (Connection conn = DatabaseConnectionManager.getConnection()) {
                String updateQuery = "UPDATE ANATOLIASOFT.dbo.AST_OPERATION SET STATUS = 1 WHERE OPR_FICHENO = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                    stmt.setString(1, currentReceiptNo);
                    int affected = stmt.executeUpdate();

                    requireActivity().runOnUiThread(() -> {
                        if (affected > 0) {
                            showToast("Receipt confirmed successfully");
                            requireActivity().onBackPressed(); // Return to previous screen
                        } else {
                            showToast("Failed to confirm receipt");
                        }
                    });
                }
            } catch (SQLException e) {
                Log.e(TAG, "Error updating receipt status", e);
                requireActivity().runOnUiThread(() ->
                        showToast("Error confirming receipt"));
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        cameraPreview.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        cameraPreview.stopCamera();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}

class ReceiptItemManager {
    private final String receiptNo;
    private final Map<String, Boolean> itemsToScan = new HashMap<>();
    private final Map<String, String> scannedItems = new HashMap<>();

    public enum ScanResult {
        SUCCESS,
        ALREADY_SCANNED,
        ITEM_NOT_IN_RECEIPT
    }

    public ReceiptItemManager(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public void loadReceiptItems() {
        try (Connection conn = DatabaseConnectionManager.getConnection()) {
            // Split by '/' and then format it properly
            String[] ficheNos = receiptNo.split("/");
            StringBuilder formattedFicheNo = new StringBuilder();
            for (String fiche : ficheNos) {
                if (formattedFicheNo.length() > 0) {
                    formattedFicheNo.append("','"); // Add a separator between fiche numbers
                }
                formattedFicheNo.append(fiche.trim()); // Trim whitespace and append ficheNo
            }

            // Add quotes around each ficheNo
            String quotedFicheNo = "'" + formattedFicheNo.toString() + "'";

            // Dynamically build table names using the table suffix
            String tableSuffix = "001"; // You might want to make this dynamic
            String tablePrefix = String.format("LG_%s", tableSuffix);
            String tableName = tablePrefix + "_01_STFICHE";
            String stLineTable = tablePrefix + "_01_STLINE";
            String itemsTable = tablePrefix + "_ITEMS";

            String query = String.format(
                    "SELECT DISTINCT " +
                            "ST.FICHENO AS FicheNo, " +
                            "IT.CODE AS ItemCode, " +
                            "SL.AMOUNT AS Quantity, " +
                            "IT.NAME AS ItemName " +
                            "FROM TIGERDB.dbo.%s ST " +
                            "INNER JOIN TIGERDB.dbo.%s SL ON SL.STFICHEREF = ST.LOGICALREF " +
                            "INNER JOIN TIGERDB.dbo.%s IT ON IT.LOGICALREF = SL.STOCKREF " +
                            "WHERE ST.FICHENO IN (%s) AND ST.TRCODE = 8 AND ST.BILLED = 0",
                    tableName, stLineTable, itemsTable, quotedFicheNo
            );

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    StringBuilder logMessage = new StringBuilder("Loaded Items:\n");
                    while (rs.next()) {
                        String itemCode = rs.getString("ItemCode");
                        String ficheNo = rs.getString("FicheNo");
                        String itemName = rs.getString("ItemName");
                        double quantity = rs.getDouble("Quantity");

                        logMessage.append(String.format(
                                "Fiche No: %s, Item Code: %s, Item Name: %s, Quantity: %.2f\n",
                                ficheNo, itemCode, itemName, quantity
                        ));

                        // Use item code as the key to track scanning
                        itemsToScan.put(itemCode, false);
                    }

                    // Log detailed information about loaded items
                    Log.d("ReceiptItemManager", logMessage.toString());
                    Log.d("ReceiptItemManager", "Total unique items loaded: " + itemsToScan.size());

                    // If no items found, log additional debugging information
                    if (itemsToScan.isEmpty()) {
                        Log.e("ReceiptItemManager", "No items found for receipt: " + receiptNo);
                        Log.e("ReceiptItemManager", "Quoted Fiche No: " + quotedFicheNo);
                    }
                }
            }
        } catch (SQLException e) {
            Log.e("ReceiptItemManager", "Error loading receipt items", e);
            e.printStackTrace();
        }
    }
    public ScanResult processScannedItem(String serialNumber) {
        Log.d("ReceiptItemManager", "Processing scanned item: " + serialNumber);
        Log.d("ReceiptItemManager", "Available items to scan: " + itemsToScan.keySet());

        // Check if the scanned item is in the list of items to scan
        if (scannedItems.containsKey(serialNumber)) {
            Log.d("ReceiptItemManager", "Item already scanned");
            return ScanResult.ALREADY_SCANNED;
        }

        boolean itemFound = false;
        for (String itemCode : itemsToScan.keySet()) {
            if (serialNumber.contains(itemCode)) {
                itemFound = true;
                itemsToScan.put(itemCode, true);
                scannedItems.put(serialNumber, receiptNo);
                break;
            }
        }

        if (!itemFound) {
            Log.d("ReceiptItemManager", "Item not found in receipt");
            return ScanResult.ITEM_NOT_IN_RECEIPT;
        }

        return ScanResult.SUCCESS;
    }
    public boolean areAllItemsScanned() {
        return !itemsToScan.containsValue(false);
    }

    public int getScannedCount() {
        return (int) itemsToScan.values().stream().filter(v -> v).count();
    }

    public int getTotalItems() {
        return itemsToScan.size();
    }
}

class CameraSourcePreview extends ViewGroup {
    private SurfaceView surfaceView;
    private CameraSource cameraSource;

    public CameraSourcePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        surfaceView = new SurfaceView(context);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                startCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopCamera();
            }
        });
        addView(surfaceView);
    }

    public void setCameraSource(CameraSource cameraSource) {
        this.cameraSource = cameraSource;
    }

    public void startCamera() {
        if (cameraSource != null) {
            try {
                cameraSource.start(surfaceView.getHolder());
            } catch (IOException e) {
                Log.e("CameraPreview", "Error starting camera", e);
            }
        }
    }

    public void stopCamera() {
        if (cameraSource != null) {
            cameraSource.stop();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        surfaceView.layout(0, 0, width, height);
    }
}
// Centralized Database Connection Manager
class DatabaseConnectionManager {
    private static final String DB_URL = DatabaseHelper.DB_URL;
    private static final String DB_USER = DatabaseHelper.DB_USER;
    private static final String DB_PASSWORD = DatabaseHelper.DB_PASSWORD;

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}