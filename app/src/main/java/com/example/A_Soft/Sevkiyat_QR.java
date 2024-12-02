package com.example.A_Soft;

import android.app.AlertDialog;
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
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Collectors;
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
    private static final Pattern QR_SERIAL_PATTERN = Pattern.compile("KAREKODNO_([^|]+)");

    private String currentReceiptNo;
    private CameraSourcePreview cameraPreview;
    private ReceiptItemManager itemManager;
    private TextView scanStatusTextView;
    private Button confirmReceiptButton;
    private boolean isPausedForConfirmation = false;
    private ExecutorService executorService;

    public static Sevkiyat_QR newInstance(String ficheNo) {
        Sevkiyat_QR fragment = new Sevkiyat_QR();
        Bundle args = new Bundle();
        args.putString(ARG_FICHENO, ficheNo);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentReceiptNo = getArguments() != null ?
                getArguments().getString(ARG_FICHENO) : null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sevkiyat_receipt_qr, container, false);

        // Validate receipt number
        if (isInvalidReceiptNumber()) return view;

        initializeComponents(view);
        setupBarcodeDetection();
        loadReceiptItems();

        return view;
    }

    private boolean isInvalidReceiptNumber() {
        if (currentReceiptNo == null || currentReceiptNo.trim().isEmpty()) {
            showToast("Geçersiz fiş numarası");
            Log.e(TAG, "No valid receipt number provided");
            return true;
        }
        return false;
    }

    private void initializeComponents(View view) {
        // Initialize views
        cameraPreview = view.findViewById(R.id.camera_preview);
        scanStatusTextView = view.findViewById(R.id.scan_status);
        confirmReceiptButton = view.findViewById(R.id.confirm_receipt_button);

        // Setup executor and item manager
        executorService = Executors.newSingleThreadExecutor();
        itemManager = new ReceiptItemManager(currentReceiptNo);

        // Setup confirm button
        confirmReceiptButton.setOnClickListener(v -> {
            if (itemManager.areAllItemsScanned()) {
                updateReceiptStatus();
            } else {
                showToast("Tamamlanmadan önce tüm ürünler taratılmalıdır.");
            }
        });
    }

    private void setupBarcodeDetection() {
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(requireContext())
                .setBarcodeFormats(Barcode.QR_CODE)
                .build();

        if (!barcodeDetector.isOperational()) {
            showToast("Barkod algılayıcısı başlatılamadı");
            return;
        }

        CameraSource cameraSource = new CameraSource.Builder(requireContext(), barcodeDetector)
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

    private long lastScanTime = 0;
    private static final long SCAN_DEBOUNCE_INTERVAL = 2000; // 2 seconds

    private void processQRCode(String qrCodeData) {
        long currentTime = System.currentTimeMillis();

        // Debounce mechanism
        if (currentTime - lastScanTime < SCAN_DEBOUNCE_INTERVAL) {
            return;
        }

        if (isPausedForConfirmation)
            return;

        try {
            if (!QR_SERIAL_PATTERN.matcher(qrCodeData).find()) {
                showToast("Geçersiz Kare kod");
                return;
            }

            lastScanTime = currentTime; // Update last scan time

            executorService.submit(() -> {
                ReceiptItemManager.ScanResult result = itemManager.cacheScannedItem(qrCodeData);
                requireActivity().runOnUiThread(() -> {
                    switch (result) {
                        case SUCCESS:
                            showItemConfirmationDialog(qrCodeData);
                            break;
                        case ALREADY_SCANNED:
                        case ITEM_NOT_IN_RECEIPT:
                            showToast(result == ReceiptItemManager.ScanResult.ALREADY_SCANNED
                                    ? "Bu ürün zaten tarandı."
                                    : "Bu ürün sevkiyat planında yok.");
                            break;
                    }
                    updateScanStatus();
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "QR Code processing error", e);
            showToast("Karekod okunurken hata oluştu");
        }
    }

    private void showItemConfirmationDialog(String serialNumber) {
        isPausedForConfirmation = true;
        String itemCode = itemManager.extractItemCodeFromQR(serialNumber);

        new AlertDialog.Builder(requireContext())
                .setTitle("Ürün okundu")
                .setMessage("Ürünler: " + itemCode)
                .setPositiveButton("Tamam", (dialog, which) -> {
                    isPausedForConfirmation = false;
                    updateScanStatus();
                    confirmReceiptButton.setEnabled(itemManager.areAllItemsScanned());
                })
                .setCancelable(false)
                .show();
    }

    private void handleScanResult(ReceiptItemManager.ScanResult result, String serialNumber) {
        String itemCode = itemManager.extractItemCodeFromQR(serialNumber);
        switch (result) {
            case ALREADY_SCANNED:
                showToast("Bu ürün zaten tarandı: " + itemCode);
                break;
            case ITEM_NOT_IN_RECEIPT:
                showToast("Bu ürün sevkiyat planında bulunmuyor: " + itemCode);
                break;
            case SUCCESS:
                showToast("Ürün başarılı bir şekilde okundu: " + itemCode);
                updateScanStatus();
                confirmReceiptButton.setEnabled(itemManager.areAllItemsScanned());
                break;
        }
    }



    private void updateScanStatus() {
        String status = String.format("Okunacak ürünler: %d/%d",
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
            boolean itemsInserted = itemManager.bulkInsertScannedItems();

            try (Connection conn = DatabaseConnectionManager.getConnection()) {
                String updateQuery = "UPDATE ANATOLIASOFT.dbo.AST_SHIPPLAN SET STATUS = 1 WHERE SLIPNR = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                    stmt.setString(1, currentReceiptNo);
                    int affected = stmt.executeUpdate();

                    requireActivity().runOnUiThread(() -> {
                        if (affected > 0 && itemsInserted) {
                            showToast("Sevkiyat tamamlandı.");
                            requireActivity().onBackPressed();
                        } else {
                            showToast("Sevkiyat tamamlanamadı.");
                        }
                    });
                }
                } catch (SQLException e) {
                    Log.e(TAG, "Error updating receipt status", e);
                    requireActivity().runOnUiThread(() ->
                            showToast("Sevkiyat tamamlanırken bir hata meydana geldi (SQL)."));
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
    private final List<ScannedQRItem> qrCodeCache = new ArrayList<>();

    // Single enum definition
    public enum ScanResult {
        SUCCESS, ALREADY_SCANNED, ITEM_NOT_IN_RECEIPT
    }

    public ReceiptItemManager(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public void loadReceiptItems() {
        try (Connection conn = DatabaseConnectionManager.getConnection()) {
            if (conn == null) {
                Log.e(TAG, "Database connection is null");
                return;
            }

            String[] ficheNos = receiptNo.split("/");
            itemsToScan.clear();
            scannedItems.clear();

            String tableSuffix = "001";
            String tablePrefix = String.format("LG_%s", tableSuffix);
            String tableName = tablePrefix + "_01_STFICHE";
            String stLineTable = tablePrefix + "_01_STLINE";
            String itemsTable = tablePrefix + "_ITEMS";

            String ficheNosList = Arrays.stream(ficheNos)
                    .map(no -> "'" + no.trim() + "'")
                    .collect(Collectors.joining(","));

            String query = String.format(
                    "SELECT DISTINCT " +
                            "IT.CODE AS ItemCode, " +
                            "IT.NAME AS ItemName " +
                            "FROM TIGERDB.dbo.%s ST " +
                            "INNER JOIN TIGERDB.dbo.%s SL ON SL.STFICHEREF = ST.LOGICALREF " +
                            "INNER JOIN TIGERDB.dbo.%s IT ON IT.LOGICALREF = SL.STOCKREF " +
                            "WHERE ST.FICHENO IN (%s) " +
                            "AND ST.TRCODE = 8 " +
                            "AND ST.BILLED = 0",
                    tableName, stLineTable, itemsTable, ficheNosList
            );

            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String itemCode = rs.getString("ItemCode");
                    itemsToScan.put(itemCode, false);
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error loading receipt items", e);
        }
    }

    private String extractSerialNumber(String qrCode) {
        int startIndex = qrCode.indexOf("KAREKODNO_");
        if (startIndex != -1) {
            int endIndex = qrCode.indexOf("|", startIndex);
            return endIndex == -1 ? qrCode.substring(startIndex) : qrCode.substring(startIndex, endIndex);
        }
        return qrCode;
    }

    public String extractItemCodeFromQR(String qrCode) {
        int lastPipeIndex = qrCode.lastIndexOf("|");
        return lastPipeIndex != -1 && lastPipeIndex < qrCode.length() - 1
                ? qrCode.substring(lastPipeIndex + 1).trim()
                : qrCode.trim();
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

    // New class to represent cached QR items
    public static class ScannedQRItem {
        String serialNumber;
        String itemCode;
        String orgnr;

        public ScannedQRItem(String serialNumber, String itemCode, String orgnr) {
            this.serialNumber = serialNumber;
            this.itemCode = itemCode;
            this.orgnr = orgnr;
        }
    }

    // New method to bulk insert cached QR codes
    public boolean bulkInsertScannedItems() {
        if (qrCodeCache.isEmpty()) {
            return false;
        }

        try (Connection conn = DatabaseConnectionManager.getConnection()) {
            // Check if ORGNR exists for this receipt
            String findOrgnrQuery = "SELECT TOP 1 ORGNR FROM ANATOLIASOFT.dbo.AST_SHIPPLAN " +
                    "WHERE STATUS = 0 AND SLIPNR = ?";
            String orgnr = null;
            try (PreparedStatement orgnrStmt = conn.prepareStatement(findOrgnrQuery)) {
                orgnrStmt.setString(1, receiptNo);
                try (ResultSet orgnrRs = orgnrStmt.executeQuery()) {
                    if (orgnrRs.next()) {
                        orgnr = orgnrRs.getString("ORGNR");
                    }
                }
            }

            if (orgnr == null) {
                Log.e(TAG, "No ORGNR found for receipt");
                return false;
            }

            // Prepare batch insert
            String insertQuery = "INSERT INTO ANATOLIASOFT.dbo.AST_SHIPPLAN_QR " +
                    "(SHP_SERIALNO, SHP_ITEMCODE, SHP_ID) VALUES (?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                for (ScannedQRItem item : qrCodeCache) {
                    insertStmt.setString(1, item.serialNumber);
                    insertStmt.setString(2, item.itemCode);
                    insertStmt.setString(3, orgnr);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }

            // Clear cache after successful insertion
            qrCodeCache.clear();
            return true;

        } catch (SQLException e) {
            Log.e(TAG, "Error bulk inserting scanned items", e);
            return false;
        }
    }

    // Modify to add to cache instead of immediate database check
    public ScanResult cacheScannedItem(String serialNumber) {
        try (Connection conn = DatabaseConnectionManager.getConnection()) {
            String extractedItemCode = extractItemCodeFromQR(serialNumber);
            String extractedSerialNo = extractSerialNumber(serialNumber);

            // Local cache check first
            if (scannedItems.containsKey(serialNumber)) {
                return ScanResult.ALREADY_SCANNED;
            }

            // Database check for already scanned items
            String checkScanQuery = "SELECT COUNT(*) AS scan_count FROM ANATOLIASOFT.dbo.AST_SHIPPLAN_QR " +
                    "WHERE SHP_SERIALNO = ? AND SHP_ITEMCODE = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkScanQuery)) {
                checkStmt.setString(1, extractedSerialNo);
                checkStmt.setString(2, extractedItemCode);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt("scan_count") > 0) {
                        return ScanResult.ALREADY_SCANNED;
                    }
                }
            }

            // Check if item is in receipt
            if (!itemsToScan.containsKey(extractedItemCode)) {
                return ScanResult.ITEM_NOT_IN_RECEIPT;
            }

            // Prevent duplicate scans of the same item code
            if (itemsToScan.get(extractedItemCode)) {
                return ScanResult.ALREADY_SCANNED;
            }

            // Add to cache
            qrCodeCache.add(new ScannedQRItem(extractedSerialNo, extractedItemCode, null));

            // Mark item as scanned locally
            itemsToScan.put(extractedItemCode, true);
            scannedItems.put(serialNumber, receiptNo);

            return ScanResult.SUCCESS;

        } catch (SQLException e) {
            Log.e(TAG, "Error checking scanned item", e);
            return ScanResult.ITEM_NOT_IN_RECEIPT;
        }
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
                // Kamera açmayı reddederse program patlıyor, düzeltilecek
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
    protected static final String DB_URL = "jdbc:jtds:sqlserver://192.168.1.113:1433/AndroidTest";
    protected static final String DB_USER = "androidemu";
    protected static final String DB_PASSWORD = "AndroidEmu123";

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
