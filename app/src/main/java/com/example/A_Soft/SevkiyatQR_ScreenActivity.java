package com.example.A_Soft;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import android.Manifest;

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

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SevkiyatQR_ScreenActivity extends AppCompatActivity {
    private static final String TAG = "SevkiyatQR_ScreenActivity";
    private static final Pattern QR_SERIAL_PATTERN = Pattern.compile("KAREKODNO_([^|]+)");

    private TableLayout tableLayout;
    private CameraSourcePreview cameraPreview;
    private TextView scanStatusTextView;
    private Button confirmReceiptButton;
    private DatabaseHelper databaseHelper;
    private String currentReceiptNo;
    private ReceiptItemManager itemManager;
    private ExecutorService executorService;

    private long lastScanTime = 0;
    private static final long SCAN_DEBOUNCE_INTERVAL = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sevkiyat_qr_screen);

        // Get the FICHENO from the intent
        currentReceiptNo = getIntent().getStringExtra("FICHENO");

        // Initialize components
        initializeComponents();
        setupBarcodeDetection();
        loadReceiptItems();
    }

    private void initializeComponents() {
        // Initialize views
        tableLayout = findViewById(R.id.tableLayout);
        cameraPreview = findViewById(R.id.camera_preview);
        scanStatusTextView = findViewById(R.id.scan_status);
        confirmReceiptButton = findViewById(R.id.confirm_receipt_button);

        // Initialize DatabaseHelper
        databaseHelper = new DatabaseHelper(this);

        // Setup executor and item manager
        executorService = Executors.newSingleThreadExecutor();
        itemManager = new ReceiptItemManager(currentReceiptNo, databaseHelper);

        // Start fetching data
        new FetchItemsTask(databaseHelper).execute(currentReceiptNo);

        // Setup confirm button
        confirmReceiptButton.setOnClickListener(v -> {
            if (itemManager.areAllItemsScanned()) {
                updateReceiptStatus();
            } else {
                Toast.makeText(this, "Tamamlanmadan önce tüm ürünler taratılmalıdır.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupBarcodeDetection() {
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.QR_CODE)
                .build();

        if (!barcodeDetector.isOperational()) {
            showToast("Barkod algılayıcısı başlatılamadı");
            return;
        }

        CameraSource cameraSource = new CameraSource.Builder(this, barcodeDetector)
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

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void processQRCode(String qrCodeData) {
        long currentTime = System.currentTimeMillis();

        // Debounce mechanism
        if (currentTime - lastScanTime < SCAN_DEBOUNCE_INTERVAL) {
            return;
        }

        try {
            if (!QR_SERIAL_PATTERN.matcher(qrCodeData).find()) {
                runOnUiThread(() -> Toast.makeText(this, "Geçersiz Kare kod", Toast.LENGTH_SHORT).show());
                return;
            }

            lastScanTime = currentTime; // Update last scan time

            executorService.submit(() -> {
                ReceiptItemManager.ScanResult result = itemManager.cacheScannedItem(qrCodeData);
                runOnUiThread(() -> {
                    switch (result) {
                        case SUCCESS:
                            showItemConfirmationDialog(qrCodeData);
                            break;
                        case ALREADY_SCANNED:
                        case ITEM_NOT_IN_RECEIPT:
                            Toast.makeText(this,
                                    result == ReceiptItemManager.ScanResult.ALREADY_SCANNED
                                            ? "Bu ürün zaten tarandı."
                                            : "Bu ürün sevkiyat planında yok.",
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                    updateScanStatus();
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "QR Code processing error", e);
            runOnUiThread(() -> Toast.makeText(this, "Karekod okunurken hata oluştu", Toast.LENGTH_SHORT).show());
        }
    }

    private void showItemConfirmationDialog(String serialNumber) {
        String itemCode = itemManager.extractItemCodeFromQR(serialNumber);

        new AlertDialog.Builder(this)
                .setTitle("Ürün okundu")
                .setMessage("Ürünler: " + itemCode)
                .setPositiveButton("Tamam", (dialog, which) -> {
                    updateScanStatus();
                    confirmReceiptButton.setEnabled(itemManager.areAllItemsScanned());
                })
                .setCancelable(false)
                .show();
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
            runOnUiThread(this::updateScanStatus);
        });
    }

    private void updateReceiptStatus() {
        executorService.submit(() -> {
            boolean itemsInserted = itemManager.bulkInsertScannedItems();

            try (Connection connection = databaseHelper.getAnatoliaSoftConnection()) {
                String updateQuery = "UPDATE " +
                        databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN") +
                        " SET STATUS = 1 WHERE SLIPNR = ?";

                int affected = databaseHelper.executeAnatoliaSoftUpdate(updateQuery, currentReceiptNo);

                runOnUiThread(() -> {
                    if (affected > 0 && itemsInserted) {
                        Toast.makeText(this, "Sevkiyat tamamlandı.", Toast.LENGTH_SHORT).show();
                        onBackPressed();
                    } else {
                        Toast.makeText(this, "Sevkiyat tamamlanamadı.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (SQLException e) {
                Log.e(TAG, "Error updating receipt status", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Sevkiyat tamamlanırken bir hata meydana geldi (SQL).", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private class FetchItemsTask extends AsyncTask<String, Void, List<DraftReceipt>> {

        private final DatabaseHelper databaseHelper;

        public FetchItemsTask(DatabaseHelper databaseHelper) {
            this.databaseHelper = databaseHelper;
        }

        @Override
        protected List<DraftReceipt> doInBackground(String... params) {
            List<DraftReceipt> detailsList = new ArrayList<>();
            String ficheNo = params[0]; // Received FICHENO

            // Prepare ficheNo for SQL IN clause
            String[] ficheNos = ficheNo.split("/");
            StringBuilder formattedFicheNo = new StringBuilder();
            for (String fiche : ficheNos) {
                if (formattedFicheNo.length() > 0) {
                    formattedFicheNo.append("','");
                }
                formattedFicheNo.append(fiche.trim());
            }
            ficheNo = "'" + formattedFicheNo.toString() + "'";

            try (Connection connection = databaseHelper.getTigerConnection()) {
                // Use dynamic table names from DatabaseHelper
                String tigerStFicheTable = databaseHelper.getTigerDbTableName("STFICHE");
                String tigerStLineTable = databaseHelper.getTigerDbTableName("STLINE");
                String tigerItemsTable = databaseHelper.getTigerDbItemsTableName("ITEMS");
                String anatoliaSoftItemsTable = databaseHelper.getAnatoliaSoftTableName("AST_ITEMS");
                String anatoliaSoftShipPlanTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN");

                String query = String.format(
                        "SELECT " +
                                "IT.NAME AS [Malzeme Adı], " +
                                "STRING_AGG(CAST(SL.AMOUNT AS VARCHAR), ', ') AS [Miktar] " +
                                "FROM %s ST " +
                                "INNER JOIN %s SL ON SL.STFICHEREF = ST.LOGICALREF " +
                                "INNER JOIN %s IT ON IT.LOGICALREF = SL.STOCKREF " +
                                "WHERE ST.TRCODE = 8 " +
                                "AND ST.BILLED = 0 " +
                                "AND ST.FICHENO IN (%s) " +
                                "GROUP BY IT.NAME",
                        tigerStFicheTable,
                        tigerStLineTable,
                        tigerItemsTable,
                        ficheNo
                );

                try (PreparedStatement statement = connection.prepareStatement(query);
                     ResultSet resultSet = statement.executeQuery()) {

                    while (resultSet.next()) {
                        detailsList.add(new DraftReceipt(
                                resultSet.getString("Malzeme Adı"),
                                resultSet.getString("Miktar")
                        ));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return detailsList;
        }

        @Override
        protected void onPostExecute(List<DraftReceipt> details) {
            // Remove existing rows (if any)
            tableLayout.removeAllViews();

            // Add table headers
            TableRow headerRow = new TableRow(SevkiyatQR_ScreenActivity.this);
            TextView headerMaterial = new TextView(SevkiyatQR_ScreenActivity.this);
            headerMaterial.setText("Malzemeler");
            headerRow.addView(headerMaterial);

            TextView headerExpectedQuantity = new TextView(SevkiyatQR_ScreenActivity.this);
            headerExpectedQuantity.setText("Okutulacak Miktar");
            headerRow.addView(headerExpectedQuantity);

            TextView headerScannedQuantity = new TextView(SevkiyatQR_ScreenActivity.this);
            headerScannedQuantity.setText("Okunan Miktar");
            headerRow.addView(headerScannedQuantity);

            tableLayout.addView(headerRow);

            // Add dynamic rows for each item
            for (DraftReceipt detail : details) {
                TableRow row = new TableRow(SevkiyatQR_ScreenActivity.this);

                // Material name
                TextView materialTextView = new TextView(SevkiyatQR_ScreenActivity.this);
                materialTextView.setText(detail.getMaterialName());
                row.addView(materialTextView);

                // Expected quantity (can be fetched from the database or set as 0)
                TextView expectedQuantityTextView = new TextView(SevkiyatQR_ScreenActivity.this);
                expectedQuantityTextView.setText(detail.getAmount());
                row.addView(expectedQuantityTextView);

                // Scanned quantity (this could be set dynamically when scanning items)
                TextView scannedQuantityTextView = new TextView(SevkiyatQR_ScreenActivity.this);
                scannedQuantityTextView.setText("0"); // Default scanned quantity, can be updated
                row.addView(scannedQuantityTextView);

                tableLayout.addView(row);
            }
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (cameraPreview != null) {
            cameraPreview.stopCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraPreview != null) {
            cameraPreview.startCamera();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}

class CameraSourcePreview extends ViewGroup {
    private SurfaceView surfaceView;
    private CameraSource cameraSource;
    private boolean isSurfaceReady = false;

    public CameraSourcePreview(Context context, AttributeSet attrs) {
        super(context, attrs);

        surfaceView = new SurfaceView(context);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                isSurfaceReady = true;
                if (cameraSource != null) {
                    try {
                        cameraSource.start(holder);
                        Log.d("CameraPreview", "Camera started on surface ready");
                    } catch (IOException e) {
                        Log.e("CameraPreview", "IOException during camera start", e);
                    }
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d("CameraPreview", "Surface changed: " + width + "x" + height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopCamera();
                isSurfaceReady = false;
            }
        });

        addView(surfaceView);
    }

    public void startCamera() {
        if (isSurfaceReady && cameraSource != null) {
            try {
                cameraSource.start(surfaceView.getHolder());
                Log.d("CameraPreview", "Camera started successfully");
            } catch (IOException e) {
                Log.e("CameraPreview", "IOException when starting camera", e);
            }
        } else {
            Log.w("CameraPreview", "Camera start deferred until surface is ready");
        }
    }

    public void setCameraSource(CameraSource cameraSource) {
        this.cameraSource = cameraSource;
    }

    public void stopCamera() {
        if (cameraSource != null) {
            cameraSource.stop();
            Log.d("CameraPreview", "Camera stopped");
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        surfaceView.layout(0, 0, width, height);
    }
}
class ReceiptItemManager {
    private static final String TAG = "ReceiptItemManager";
    private final String receiptNo;
    private final Map<String, Boolean> itemsToScan = new HashMap<>();
    private final Map<String, String> scannedItems = new HashMap<>();
    private final List<ScannedQRItem> qrCodeCache = new ArrayList<>();
    private final DatabaseHelper databaseHelper;
    // Single enum definition
    public enum ScanResult {
        SUCCESS, ALREADY_SCANNED, ITEM_NOT_IN_RECEIPT
    }

    public ReceiptItemManager(String receiptNo, DatabaseHelper databaseHelper) {
        this.receiptNo = receiptNo;
        this.databaseHelper = databaseHelper; // Initialize here
    }

    public void loadReceiptItems() {
        try (Connection conn = databaseHelper.getTigerConnection()) {
            String[] ficheNos = receiptNo.split("/");
            itemsToScan.clear();
            scannedItems.clear();

            String tigerStFicheTable = databaseHelper.getTigerDbTableName("STFICHE");
            String tigerStLineTable = databaseHelper.getTigerDbTableName("STLINE");
            String tigerItemsTable = databaseHelper.getTigerDbItemsTableName("ITEMS");

            String ficheNosList = Arrays.stream(ficheNos)
                    .map(no -> "'" + no.trim() + "'")
                    .collect(Collectors.joining(","));

            String query = String.format(
                    "SELECT DISTINCT " +
                            "IT.CODE AS ItemCode, " +
                            "SUM(SL.AMOUNT) AS TotalQuantity " +
                            "FROM %s ST " +
                            "INNER JOIN %s SL ON SL.STFICHEREF = ST.LOGICALREF " +
                            "INNER JOIN %s IT ON IT.LOGICALREF = SL.STOCKREF " +
                            "WHERE ST.FICHENO IN (%s) " +
                            "AND ST.TRCODE = 8 " +
                            "AND ST.BILLED = 0 " +
                            "GROUP BY IT.CODE",
                    tigerStFicheTable, tigerStLineTable, tigerItemsTable, ficheNosList
            );

            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String itemCode = rs.getString("ItemCode");
                    double totalQuantity = rs.getDouble("TotalQuantity");

                    // Initialize item as not scanned
                    itemsToScan.put(itemCode, false);
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error loading receipt items: " + e.getMessage(), e);
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

        try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {
            if (conn == null) {
                Log.e(TAG, "Database connection is null");
                return false;
            }

            // Dynamically resolve table names
            String shipPlanTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN");
            String shipPlanQRTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN_QR");

            // Check if ORGNR exists for this receipt
            String findOrgnrQuery = String.format(
                    "SELECT TOP 1 ORGNR FROM %s WHERE STATUS = 0 AND SLIPNR = ?",
                    shipPlanTable
            );
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

            // Prepare batch insert into dynamically resolved table
            String insertQuery = String.format(
                    "INSERT INTO %s (SHP_SERIALNO, SHP_ITEMCODE, SHP_ID) VALUES (?, ?, ?)",
                    shipPlanQRTable
            );
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
            Log.e(TAG, "Error bulk inserting scanned items: " + e.getMessage(), e);
            return false;
        }
    }


    // Modify to add to cache instead of immediate database check
    public ScanResult cacheScannedItem(String serialNumber) {
        try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {
            String extractedItemCode = extractItemCodeFromQR(serialNumber);
            String extractedSerialNo = extractSerialNumber(serialNumber);

            // Local cache check first
            if (scannedItems.containsKey(serialNumber)) {
                return ScanResult.ALREADY_SCANNED;
            }

            // Dynamically resolve table name
            String shipPlanQRTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN_QR");

            // Database check for already scanned items
            String checkScanQuery = String.format(
                    "SELECT COUNT(*) AS scan_count FROM %s WHERE SHP_SERIALNO = ? AND SHP_ITEMCODE = ?",
                    shipPlanQRTable
            );
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