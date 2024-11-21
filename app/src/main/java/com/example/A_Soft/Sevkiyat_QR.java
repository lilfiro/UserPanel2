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
import java.sql.ResultSetMetaData;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import java.util.stream.Collectors;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sevkiyat_QR extends Fragment {
    private static final String TAG = "Sevkiyat_QR";
    private static final String ARG_FICHENO = "FICHENO";

    private CameraSourcePreview cameraPreview;
    private CameraSource cameraSource;
    private ExecutorService executorService;
    private ReceiptItemManager itemManager;
    private TextView scanStatusTextView;
    private Button confirmReceiptButton;
    private String currentReceiptNo;

    // Static factory method (optional, but can be helpful)
    public static Sevkiyat_QR newInstance(String ficheNo) {
        Sevkiyat_QR fragment = new Sevkiyat_QR();
        Bundle args = new Bundle();
        args.putString(ARG_FICHENO, ficheNo);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sevkiyat_receipt_qr, container, false);

        // Initialize views
        cameraPreview = view.findViewById(R.id.camera_preview);
        scanStatusTextView = view.findViewById(R.id.scan_status);
        confirmReceiptButton = view.findViewById(R.id.confirm_receipt_button);

        // Get receipt number from arguments
        if (getArguments() != null) {
            currentReceiptNo = getArguments().getString(ARG_FICHENO);

            // Add logging and validation
            Log.d(TAG, "Received FICHENO: " + currentReceiptNo);

            if (currentReceiptNo == null || currentReceiptNo.trim().isEmpty()) {
                showToast("Invalid receipt number");
            }
        } else {
            // Fallback or error handling
            showToast("No receipt number provided");
            currentReceiptNo = ""; // Prevent null pointer exceptions
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
    private static final Pattern QR_SERIAL_PATTERN = Pattern.compile("KAREKODNO_([^|]+)");
    private void processQRCode(String qrCodeData) {
        try {
            Matcher serialMatcher = QR_SERIAL_PATTERN.matcher(qrCodeData);
            if (!serialMatcher.find()) {
                showToast("Invalid QR code format");
                return;
            }

            String itemSerialNumber = qrCodeData; // Use full QR code for processing

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
        String itemCode = itemManager.extractItemCodeFromQR(serialNumber);
        switch (result) {
            case ALREADY_SCANNED:
                showToast("Item already scanned: " + itemCode);
                break;
            case ITEM_NOT_IN_RECEIPT:
                showToast("Item not in receipt: " + itemCode +
                        "\nAvailable codes: " + itemManager.getAvailableItemCodes());
                break;
            case SUCCESS:
                showToast("Item scanned successfully: " + itemCode);
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
    private static final String TAG = "ReceiptItemManager";
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
            // Verify connection is working
            if (conn == null) {
                Log.e(TAG, "Database connection is null");
                return;
            }

            // Split by '/' and trim each fiche number
            String[] ficheNos = receiptNo.split("/");
            Log.d(TAG, "Attempting to load items for receipt numbers: " + Arrays.toString(ficheNos));

            // Clear previous items before loading
            itemsToScan.clear();
            scannedItems.clear();

            // Dynamically build table names using the table suffix
            String tableSuffix = "001"; // Consider making this dynamic
            String tablePrefix = String.format("LG_%s", tableSuffix);
            String tableName = tablePrefix + "_01_STFICHE";
            String stLineTable = tablePrefix + "_01_STLINE";
            String itemsTable = tablePrefix + "_ITEMS";

            // Prepare the IN clause with trimmed fiche numbers
            String ficheNosList = Arrays.stream(ficheNos)
                    .map(String::trim)
                    .map(no -> "'" + no + "'")
                    .collect(Collectors.joining(","));

            // Log detailed database connection information
            Log.d(TAG, "Database Tables:");
            Log.d(TAG, "Table Name: " + tableName);
            Log.d(TAG, "StLine Table: " + stLineTable);
            Log.d(TAG, "Items Table: " + itemsTable);
            Log.d(TAG, "Fiche Numbers List: " + ficheNosList);

            String query = String.format(
                    "SELECT DISTINCT " +
                            "ST.FICHENO AS FicheNo, " +
                            "IT.CODE AS ItemCode, " +
                            "SL.AMOUNT AS Quantity, " +
                            "IT.NAME AS ItemName, " +
                            "ST.TRCODE, " +
                            "ST.BILLED " +
                            "FROM TIGERDB.dbo.%s ST " +
                            "INNER JOIN TIGERDB.dbo.%s SL ON SL.STFICHEREF = ST.LOGICALREF " +
                            "INNER JOIN TIGERDB.dbo.%s IT ON IT.LOGICALREF = SL.STOCKREF " +
                            "WHERE ST.FICHENO IN (%s) " +
                            "AND ST.TRCODE = 8 " +
                            "AND ST.BILLED = 0",
                    tableName, stLineTable, itemsTable, ficheNosList
            );

            Log.d(TAG, "Full SQL Query: " + query);

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    // Check if ResultSet is empty
                    if (!rs.isBeforeFirst()) {
                        Log.e(TAG, "No results returned from query");

                        // Additional diagnostic query to check table and data existence
                        String diagnosticQuery = String.format(
                                "SELECT COUNT(*) AS RecordCount " +
                                        "FROM TIGERDB.dbo.%s ST " +
                                        "WHERE ST.FICHENO IN (%s)",
                                tableName, ficheNosList
                        );

                        try (PreparedStatement diagStmt = conn.prepareStatement(diagnosticQuery);
                             ResultSet diagRs = diagStmt.executeQuery()) {

                            if (diagRs.next()) {
                                int recordCount = diagRs.getInt("RecordCount");
                                Log.d(TAG, "Diagnostic Record Count: " + recordCount);
                            }
                        }
                    }

                    while (rs.next()) {
                        String itemCode = rs.getString("ItemCode");
                        String currentFicheNo = rs.getString("FicheNo");
                        String itemName = rs.getString("ItemName");
                        double quantity = rs.getDouble("Quantity");
                        int trCode = rs.getInt("TRCODE");
                        int billed = rs.getInt("BILLED");

                        Log.d(TAG, String.format(
                                "Found Item - Fiche: %s, Code: %s, Name: %s, Quantity: %.2f, TrCode: %d, Billed: %d",
                                currentFicheNo, itemCode, itemName, quantity, trCode, billed
                        ));

                        // Use item code as the key to track scanning
                        itemsToScan.put(itemCode, false);
                    }

                    // Log the final items found
                    Log.d(TAG, "Total unique items loaded: " + itemsToScan.size());
                    for (String itemCode : itemsToScan.keySet()) {
                        Log.d(TAG, "Loaded item code: " + itemCode);
                    }

                    if (itemsToScan.isEmpty()) {
                        Log.e(TAG, "No items found for receipt: " + receiptNo);
                    }
                }
            } catch (SQLException e) {
                Log.e(TAG, "Error executing query for Fiche Numbers: " + receiptNo, e);
                e.printStackTrace();
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error connecting to database", e);
            e.printStackTrace();
        }
    }

    public ScanResult processScannedItem(String serialNumber) {
        Log.d(TAG, "Processing scanned item: " + serialNumber);
        Log.d(TAG, "Full available items to scan: " + itemsToScan.keySet());

        // Check if the scanned item is already scanned
        if (scannedItems.containsKey(serialNumber)) {
            Log.d(TAG, "Item already scanned");
            return ScanResult.ALREADY_SCANNED;
        }

        // Extract item code from QR code
        String extractedItemCode = extractItemCodeFromQR(serialNumber);
        Log.d(TAG, "Extracted Item Code: " + extractedItemCode);

        boolean itemFound = itemsToScan.containsKey(extractedItemCode);

        if (itemFound) {
            itemsToScan.put(extractedItemCode, true);
            scannedItems.put(serialNumber, receiptNo);
            Log.d(TAG, "Item matched: " + extractedItemCode);
            return ScanResult.SUCCESS;
        }

        Log.d(TAG, "Item not found in receipt. Extracted Code: " + extractedItemCode);

        // Additional debugging: print out all item codes to help understand what's happening
        Log.d(TAG, "All possible item codes: " + itemsToScan.keySet());

        return ScanResult.ITEM_NOT_IN_RECEIPT;
    }
    public String extractItemCodeFromQR(String qrCode) {
        // Extract item code from QR code
        // Assumes format like ||KAREKODNO_561007ENT22000401|...
        int startIndex = qrCode.indexOf("KAREKODNO_") + 10;
        int endIndex = qrCode.indexOf("|", startIndex);
        if (startIndex >= 10 && endIndex > startIndex) {
            return qrCode.substring(startIndex, endIndex);
        }
        return qrCode;
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

    public String getAvailableItemCodes() {
        return itemsToScan.keySet().toString();
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

class DatabaseConnectionManager {
    private static final String DB_URL = DatabaseHelper.DB_URL;
    private static final String DB_USER = DatabaseHelper.DB_USER;
    private static final String DB_PASSWORD = DatabaseHelper.DB_PASSWORD;

    // Static method that returns a Connection
    public static Connection getSafeConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // Optional: If you want to include error handling within the method
    public static Connection getConnection() {
        try {
            Log.d("DatabaseConnectionManager", "Attempting to connect with URL: " + DB_URL);
            Log.d("DatabaseConnectionManager", "Attempting to connect with User: " + DB_USER);
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            Log.d("DatabaseConnectionManager", "Connection successful");
            return conn;
        } catch (SQLException e) {
            Log.e("DatabaseConnectionManager", "Connection error details:", e);
            Log.e("DatabaseConnectionManager", "Error message: " + e.getMessage());
            Log.e("DatabaseConnectionManager", "SQL State: " + e.getSQLState());
            Log.e("DatabaseConnectionManager", "Error Code: " + e.getErrorCode());
            return null;
        }
    }
}
