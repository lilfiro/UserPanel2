package com.example.A_Soft;

import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import android.Manifest;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;


public class SevkiyatQR_ScreenActivity extends AppCompatActivity {
    private static final String TAG = "SevkiyatQR_ScreenActivity";
    private static final Pattern QR_SERIAL_PATTERN = Pattern.compile("KAREKODNO_([^|]+)");

    private TableLayout tableLayout;
    private CameraSourcePreview cameraPreview;
    private TextView scanStatusTextView;
    private ImageButton confirmReceiptButton;
    private DatabaseHelper databaseHelper;
    private String currentReceiptNo;
    private ReceiptItemManager itemManager;
    private ExecutorService executorService;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private long lastScanTime = 0;
    private static final long SCAN_DEBOUNCE_INTERVAL = 2000; // 2 seconds

    private ImageButton cameraStateButton;
    private boolean isCameraActive = false;
    private static final String PREF_NAME = "SevkiyatDrafts";
    private static final String KEY_DRAFT_DATA = "draft_data";
    private ImageButton saveAsDraftButton;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sevkiyat_qr_screen);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Get the FICHENO from the intent
        currentReceiptNo = getIntent().getStringExtra("FICHENO");

        // Initialize components
        initializeComponents();

        // Load any existing draft data
        loadDraftData();

        // Check for camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupBarcodeDetection();
        } else {
            requestCameraPermission();
        }

        loadReceiptItems();
    }
    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // Show explanation (if needed)
            new AlertDialog.Builder(this)
                    .setMessage("Kamera izni gerekiyor")
                    .setPositiveButton("Tamam", (dialog, which) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE))
                    .create().show();
        } else {
            // Directly request permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with setting up barcode detection
                setupBarcodeDetection();
            } else {
                // Permission denied, show a message or handle appropriately
                Toast.makeText(this, "Kamera izni verilmedi", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initializeComponents() {
        // Existing initializations...
        tableLayout = findViewById(R.id.tableLayout);
        cameraPreview = findViewById(R.id.camera_preview);
        scanStatusTextView = findViewById(R.id.scan_status);
        confirmReceiptButton = findViewById(R.id.confirm_receipt_button);
        ImageButton manualQrButton = findViewById(R.id.manual_qr_button);
        // Add Save as Draft button
        saveAsDraftButton = findViewById(R.id.save_draft_button);
        saveAsDraftButton.setOnClickListener(v -> saveDraft());
        ImageButton scanButton = findViewById(R.id.scanButton);
        scanButton.setOnClickListener(v -> showScannerInputDialog());
        cameraStateButton = findViewById(R.id.camera_state);
        cameraStateButton.setOnClickListener(v -> toggleCamera());

// Start with camera off
        if (cameraPreview != null) {
            cameraPreview.stopCamera();
            cameraPreview.setVisibility(View.GONE);
        }
        isCameraActive = false;
        cameraStateButton.setImageResource(R.drawable.camera_off);

        // Show scanner dialog on start
        showScannerInputDialog();
        // Style the table
        styleTableLayout();

        // Initialize DatabaseHelper
        databaseHelper = new DatabaseHelper(this);

        // Setup executor and item manager
        executorService = Executors.newSingleThreadExecutor();
        itemManager = new ReceiptItemManager(currentReceiptNo, databaseHelper);

        // Setup manual QR input button
        manualQrButton.setOnClickListener(v -> showManualQRInputDialog());

        // Start fetching data
        new FetchItemsTask(databaseHelper).execute(currentReceiptNo);

        // Update the confirmReceiptButton click listener
        confirmReceiptButton.setOnClickListener(v -> {
            if (itemManager.areAllItemsScanned()) {
                showConfirmationDialog();
            } else {
                showAlert("Uyarı", "Tamamlanmadan önce tüm malzemeler taratılmalıdır.");
            }
        });
    }
    private void toggleCamera() {
        isCameraActive = !isCameraActive;

        if (isCameraActive) {
            setupBarcodeDetection();
            cameraPreview.startCamera();
            cameraPreview.setVisibility(View.VISIBLE);
            findViewById(R.id.scan_area_indicator).setVisibility(View.VISIBLE);
            cameraStateButton.setImageResource(R.drawable.camera_on);
        } else {
            cameraPreview.stopCamera();
            cameraPreview.setVisibility(View.GONE);
            findViewById(R.id.scan_area_indicator).setVisibility(View.GONE);
            cameraStateButton.setImageResource(R.drawable.camera_off);
        }
    }
    private void showScannerInputDialog() {
        if (isCameraActive) {
            toggleCamera();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Barkod Tarayıcı");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        EditText scannerInput = new EditText(this);
        scannerInput.setHint("Barkod tarayıcı bekleniyor...");
        scannerInput.requestFocus();
        layout.addView(scannerInput);

        builder.setView(layout);
        builder.setPositiveButton("Onayla", null);
        builder.setNegativeButton("İptal", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        scannerInput.addTextChangedListener(new TextWatcher() {
            private boolean isProcessing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isProcessing) return;
                isProcessing = true;

                String scannedData = s.toString().trim();
                if (isValidQRFormat(scannedData)) {
                    executorService.submit(() -> {
                        ReceiptItemManager.ScanResult result = itemManager.cacheScannedItem(scannedData);
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            handleScanResult(result, scannedData);
                            isProcessing = false;
                        });
                    });
                } else {
                    isProcessing = false;
                }
            }
        });

        dialog.setOnShowListener(dialogInterface -> {
            scannerInput.requestFocus();
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            positiveButton.setVisibility(View.GONE);
            negativeButton.setVisibility(View.GONE);
        });

        dialog.show();
    }

    private boolean isValidQRFormat(String qrCode) {
        return qrCode != null && qrCode.contains("KAREKODNO_") && qrCode.contains("||");
    }

    private void handleScanResult(ReceiptItemManager.ScanResult result, String scannedData) {
        switch (result) {
            case SUCCESS:
                showToast("Ürün Okutuldu");
                updateScanStatus();
                showScannerInputDialog();
                break;
            case ALREADY_SCANNED:
                showAlert("Uyarı", "Bu ürün zaten tarandı", () -> showScannerInputDialog());
                break;
            case ITEM_NOT_IN_RECEIPT:
                showAlert("Hata", "Bu ürün sevkiyat planında yok", () -> showScannerInputDialog());
                break;
            case COMPLETE_ITEM:
                showAlert("Uyarı", "Bu ürünün okutulması tamamlanmıştır", () -> showScannerInputDialog());
                break;
        }
    }

    private void showAlert(String title, String message, Runnable onDismiss) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Tamam", (dialog, which) -> {
                    dialog.dismiss();
                    if (onDismiss != null) onDismiss.run();
                })
                .show();
    }
    private void saveDraft() {
        DraftData draftData = new DraftData(
                currentReceiptNo,
                itemManager.getScannedSerials(),
                itemManager.getScannedItemCounts(),
                itemManager.getItemQuantities(),
                itemManager.getItemNames()
        );

        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(draftData);
        editor.putString(KEY_DRAFT_DATA + "_" + currentReceiptNo, json);
        editor.apply();

        Toast.makeText(this, "Taslak kaydedildi", Toast.LENGTH_SHORT).show();
        //finish();
    }

    private void loadDraftData() {
        String json = sharedPreferences.getString(KEY_DRAFT_DATA + "_" + currentReceiptNo, null);
        if (json != null) {
            Gson gson = new Gson();
            DraftData draftData = gson.fromJson(json, DraftData.class);
            itemManager.loadDraftData(draftData);
            updateScanStatus();
        }
    }
    private void showConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Onay")
                .setMessage("Sevkiyatı tamamlamak istediğinizden emin misiniz?")
                .setPositiveButton("Evet", (dialog, which) -> updateReceiptStatus())
                .setNegativeButton("Hayır", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void styleTableLayout() {
        // Set table properties
        tableLayout.setBackgroundResource(android.R.color.white);
        tableLayout.setPadding(2, 2, 2, 2);
        tableLayout.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        tableLayout.setStretchAllColumns(true);
        tableLayout.setShrinkAllColumns(true);
    }

    private void styleHeaderCell(TextView cell) {
        // Set layout parameters for full width and height
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.MATCH_PARENT,
                1.0f
        );
        params.setMargins(1, 1, 1, 1);
        cell.setLayoutParams(params);

        // Style the cell
        cell.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
        cell.setTextColor(getResources().getColor(android.R.color.white));
        cell.setPadding(8, 16, 8, 16);
        cell.setTypeface(null, Typeface.BOLD);
        cell.setGravity(Gravity.CENTER);
        cell.setMaxLines(2);
        cell.setEllipsize(TextUtils.TruncateAt.END);
    }

    private void styleDataCell(TextView cell) {
        // Set layout parameters for full width and height
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.MATCH_PARENT,
                1.0f
        );
        params.setMargins(1, 1, 1, 1);
        cell.setLayoutParams(params);

        // Style the cell
        cell.setBackgroundColor(getResources().getColor(android.R.color.white));
        cell.setPadding(8, 12, 8, 12);
        cell.setTextColor(getResources().getColor(android.R.color.black));
        cell.setGravity(Gravity.CENTER);
        cell.setMaxLines(2);
        cell.setEllipsize(TextUtils.TruncateAt.END);

        // Add border with full coverage
        GradientDrawable border = new GradientDrawable();
        border.setColor(getResources().getColor(android.R.color.white));
        border.setStroke(1, getResources().getColor(android.R.color.darker_gray));
        cell.setBackground(border);
    }

    private TextView createTextView(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setGravity(Gravity.CENTER);

        // Set layout parameters for full width and height
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.MATCH_PARENT,
                1.0f
        );
        params.setMargins(1, 1, 1, 1);
        textView.setLayoutParams(params);

        return textView;
    }

    // Update the manual QR input validation
    private void showManualQRInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Manuel Ürün Girişi");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        // Create KAREKODNO input
        final EditText karekodInput = new EditText(this);
        karekodInput.setHint("Karekod No");
        layout.addView(karekodInput);

        // Add some spacing
        View spacing = new View(this);
        spacing.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                30)); // 30dp spacing
        layout.addView(spacing);

        // Create Barcode input
        final EditText barcodeInput = new EditText(this);
        barcodeInput.setHint("Ürün Kodu");
        layout.addView(barcodeInput);

        builder.setView(layout);

        builder.setPositiveButton("Onayla", null);
        builder.setNegativeButton("İptal", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String karekod = karekodInput.getText().toString().trim();
                String barcode = barcodeInput.getText().toString().trim();

                if (karekod.isEmpty() || barcode.isEmpty()) {
                    showAlert("Uyarı", "Lütfen tüm alanları doldurun");
                    return;
                }

                // Create the formatted QR string with both values
                String formattedQR = String.format("KAREKODNO_%s||%s", karekod, barcode);

                dialog.dismiss();
                processQRCode(formattedQR);
            });
        });

        dialog.show();
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
                .setRequestedPreviewSize(480, 480)
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
    // Add an overloaded version of showAlert that can finish the activity
    // Base method with 2 parameters (default version)
    private void showAlert(String title, String message) {
        showAlert(title, message, false); // Default to not finishing the activity
    }

    // Extended method with all 3 parameters
    private void showAlert(String title, String message, boolean finishAfter) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Tamam", (dialog, which) -> {
                        dialog.dismiss();
                        if (finishAfter) {
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
        });
    }

    private void processQRCode(String qrCodeData) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime < SCAN_DEBOUNCE_INTERVAL) {
            return;
        }

        try {
            if (!QR_SERIAL_PATTERN.matcher(qrCodeData).find()) {
                showAlert("Hata", "Geçersiz Kare kod formatı");
                return;
            }

            lastScanTime = currentTime;

            executorService.submit(() -> {
                // Log the QR code being processed
                Log.d(TAG, "Processing QR code: " + qrCodeData);

                ReceiptItemManager.ScanResult result = itemManager.cacheScannedItem(qrCodeData);
                runOnUiThread(() -> {
                    switch (result) {
                        case SUCCESS:
                            showToast("Ürün Okutuldu");
                            break;
                        case ALREADY_SCANNED:
                            showAlert("Uyarı", "Bu ürün zaten tarandı.");
                            break;
                        case ITEM_NOT_IN_RECEIPT:
                            showAlert("Hata", "Bu ürün sevkiyat planında yok.");
                            break;
                        case COMPLETE_ITEM:
                            showAlert("Uyarı", "Bu ürünün okutulması tamamlanmıştır.");
                            break;
                    }
                    updateScanStatus();
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "QR Code processing error", e);
            showAlert("Hata", "Karekod okunurken hata oluştu: " + e.getMessage());
        }
    }
    private void updateScanStatus() {
        runOnUiThread(() -> {
            // Debug log the current state
            Log.d(TAG, "Updating scan status");
            int scannedCount = itemManager.getScannedCount();
            int totalItems = itemManager.getTotalItems();
            Log.d(TAG, String.format("Counts - Scanned: %d, Total: %d", scannedCount, totalItems));

            // Update header or summary information
            String status = String.format("Okutulacak malzemeler: %d/%d",
                    scannedCount, totalItems);
            scanStatusTextView.setText(status);

            // Update table rows dynamically
            for (int i = 1; i < tableLayout.getChildCount(); i++) {
                TableRow row = (TableRow) tableLayout.getChildAt(i);
                TextView materialNameView = (TextView) row.getChildAt(0);
                TextView scannedQuantityView = (TextView) row.getChildAt(2);

                String materialName = materialNameView.getText().toString();
                int scannedItemCount = itemManager.getScannedCountForItem(materialName);
                Log.d(TAG, String.format("Row update - Material: %s, Count: %d",
                        materialName, scannedItemCount));

                scannedQuantityView.setText(String.valueOf(scannedItemCount));
            }

            // Enable/disable confirm button based on scanning status
            boolean allItemsScanned = itemManager.areAllItemsScanned();
            Log.d(TAG, "All items scanned: " + allItemsScanned);
            confirmReceiptButton.setEnabled(allItemsScanned);
            confirmReceiptButton.setAlpha(allItemsScanned ? 1.0f : 0.5f);
        });
    }

    private void loadReceiptItems() {
        executorService.submit(() -> {
            itemManager.loadReceiptItems();

            itemManager.logItemState();
            itemManager.printItemCounts();
            // Load draft data after receipt items are loaded
            runOnUiThread(() -> {
                loadDraftData();
                updateScanStatus();
            });
        });
    }

    // Update the updateReceiptStatus method
// In SevkiyatQR_ScreenActivity class, update the updateReceiptStatus method:

    private void updateReceiptStatus() {
        executorService.submit(() -> {
            try (Connection connection = databaseHelper.getAnatoliaSoftConnection()) {
                // First check stock levels
                String stockQuery = "SELECT " +
                        "t.CODE, " +
                        "t.TOTAL_QUANTITY AS PLANNED_QUANTITY, " +
                        "COALESCE(s.AVAILABLE_STOCK, 0) AS AVAILABLE_STOCK " +
                        "FROM (" +
                        "SELECT i.CODE, SUM(sl.QUANTITY) as TOTAL_QUANTITY " +
                        "FROM " + databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN") + " sp " +
                        "JOIN " + databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLANLINE") + " sl ON sp.ID = sl.SHIPPLANID " +
                        "JOIN " + databaseHelper.getTigerDbItemsTableName("ITEMS") + " ti ON ti.LOGICALREF = sl.ERPITEMID " +
                        "JOIN " + databaseHelper.getAnatoliaSoftTableName("AST_ITEMS") + " i ON i.CODE = ti.CODE " +
                        "WHERE sp.SLIPNR = ? " +
                        "GROUP BY i.CODE" +
                        ") t " +
                        "LEFT JOIN (" +
                        "SELECT CODE, SUM(MIKTAR) as AVAILABLE_STOCK FROM (" +
                        "SELECT ITMAS.CODE, SUM(SHPLN.QUANTITY) as MIKTAR " +
                        "FROM " + databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLANLINE") + " SHPLN " +
                        "INNER JOIN " + databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN") + " SHP ON SHPLN.SHIPPLANID=SHP.ID " +
                        "INNER JOIN " + databaseHelper.getTigerDbItemsTableName("ITEMS") + " ITMLOGO ON SHPLN.ERPITEMID=ITMLOGO.LOGICALREF " +
                        "INNER JOIN " + databaseHelper.getAnatoliaSoftTableName("AST_ITEMS") + " ITMAS ON ITMLOGO.CODE=ITMAS.CODE " +
                        "WHERE SHP.STATUS=1 " +
                        "GROUP BY ITMAS.CODE " +
                        "UNION ALL " +
                        "SELECT ITM.CODE, SUM((CASE PRDSLP.SLIPTYPE WHEN 1 THEN 1 WHEN 2 THEN -1 END) * PRDTRN.QUANTITY) as MIKTAR " +
                        "FROM " + databaseHelper.getAnatoliaSoftTableName("AST_PRODUCTION_ITEMS") + " PRDTRN " +
                        "INNER JOIN " + databaseHelper.getAnatoliaSoftTableName("AST_PRODUCTION_SLIPS") + " PRDSLP ON PRDTRN.SLIPID=PRDSLP.ID " +
                        "INNER JOIN " + databaseHelper.getAnatoliaSoftTableName("AST_ITEMS") + " ITM ON ITM.CODE=PRDTRN.MALZEME " +
                        "GROUP BY ITM.CODE" +
                        ") stock_query " +
                        "GROUP BY CODE" +
                        ") s ON t.CODE = s.CODE";

                List<String> insufficientStockItems = new ArrayList<>();

                try (PreparedStatement stmt = connection.prepareStatement(stockQuery)) {
                    stmt.setString(1, currentReceiptNo);

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String itemCode = rs.getString("CODE");
                            int plannedQuantity = rs.getInt("PLANNED_QUANTITY");
                            int availableStock = rs.getInt("AVAILABLE_STOCK");

                            if (availableStock < plannedQuantity) {
                                insufficientStockItems.add(itemCode);
                            }
                        }
                    }
                }

                if (!insufficientStockItems.isEmpty()) {
                    // Build error message for insufficient stock items
                    StringBuilder errorMessage = new StringBuilder();
                    for (String itemCode : insufficientStockItems) {
                        errorMessage.append("Stoktaki [").append(itemCode).append("] malzemesi, sevkiyat planındaki miktar ile uyuşmuyor\n");
                    }

                    String finalErrorMessage = errorMessage.toString();
                    runOnUiThread(() -> showAlert("Stok Hatası", finalErrorMessage));
                    return;
                }

                // If stock levels are sufficient, proceed with updating status
                boolean itemsInserted = itemManager.bulkInsertScannedItems();

                String updateQuery = "UPDATE " +
                        databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN") +
                        " SET STATUS = 1 WHERE SLIPNR = ?";

                int affected = databaseHelper.executeAnatoliaSoftUpdate(updateQuery, currentReceiptNo);

                runOnUiThread(() -> {
                    if (affected > 0 && itemsInserted) {
                        showAlert("Başarılı", "Sevkiyat başarıyla tamamlandı.", true);
                    } else {
                        showAlert("Hata", "Sevkiyat tamamlanamadı.");
                    }
                });

            } catch (SQLException e) {
                Log.e(TAG, "Error updating receipt status", e);
                runOnUiThread(() ->
                        showAlert("Hata", "Sevkiyat tamamlanırken bir hata meydana geldi (SQL).")
                );
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
            String slipNr = params[0];

            try (Connection connection = databaseHelper.getAnatoliaSoftConnection()) {
                String anatoliaSoftShipPlanTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN");
                String anatoliaSoftShipPlanLineTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLANLINE");
                String tigerItemsTable = databaseHelper.getTigerDbItemsTableName("ITEMS");
                String astItemsTable = databaseHelper.getAnatoliaSoftTableName("AST_ITEMS");

                String query = String.format(
                        "WITH FilteredItems AS (" +
                                "    SELECT " +
                                "        IT.NAME AS ItemName, " +
                                "        SHPL.QUANTITY AS ItemQuantity " +
                                "    FROM %s SHP " +
                                "    INNER JOIN %s SHPL ON SHP.ID = SHPL.SHIPPLANID " +
                                "    INNER JOIN %s IT ON IT.LOGICALREF = SHPL.ERPITEMID " +
                                "    LEFT JOIN %s AST_IT ON AST_IT.CODE = IT.CODE " +
                                "    WHERE SHP.SLIPNR = ? " +
                                "    AND SHP.STATUS = 0 " +
                                "    AND IT.STGRPCODE <> 'TRAVERS' " +
                                "    AND (AST_IT.GROUPCODE2 IS NULL OR AST_IT.GROUPCODE2 <> 'DIREKDEM') " +
                                ")" +
                                "SELECT " +
                                "    ItemName AS [Malzeme Adı], " +
                                "    CAST(SUM(ItemQuantity) AS VARCHAR) AS [Miktar] " +
                                "FROM FilteredItems " +
                                "GROUP BY ItemName",
                        anatoliaSoftShipPlanTable,
                        anatoliaSoftShipPlanLineTable,
                        tigerItemsTable,
                        astItemsTable
                );

                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, slipNr);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            detailsList.add(new DraftReceipt(
                                    resultSet.getString("Malzeme Adı"),
                                    resultSet.getString("Miktar")
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("FetchItemsTask", "Error fetching items", e);
                e.printStackTrace();
            }
            return detailsList;
        }

        @Override
        protected void onPostExecute(List<DraftReceipt> details) {
            tableLayout.removeAllViews();

            // Add table headers
            TableRow headerRow = new TableRow(SevkiyatQR_ScreenActivity.this);
            TextView headerMaterial = createTextView("Malzemeler");
            TextView headerExpected = createTextView("Okutulacak Miktar");
            TextView headerScanned = createTextView("Okunan Miktar");

            styleHeaderCell(headerMaterial);
            styleHeaderCell(headerExpected);
            styleHeaderCell(headerScanned);

            headerRow.addView(headerMaterial);
            headerRow.addView(headerExpected);
            headerRow.addView(headerScanned);
            tableLayout.addView(headerRow);

            // Add rows for non-TRAVERS items
            for (DraftReceipt detail : details) {
                TableRow row = new TableRow(SevkiyatQR_ScreenActivity.this);

                TextView materialView = createTextView(detail.getMaterialName());
                TextView amountView = createTextView(detail.getAmount().replace(".00", ""));
                TextView scannedView = createTextView(String.valueOf(itemManager.getScannedCountForItem(detail.getMaterialName())));

                styleDataCell(materialView);
                styleDataCell(amountView);
                styleDataCell(scannedView);

                row.addView(materialView);
                row.addView(amountView);
                row.addView(scannedView);

                tableLayout.addView(row);
            }

            updateScanStatus();
        }

        private TextView createTextView(String text) {
            TextView textView = new TextView(SevkiyatQR_ScreenActivity.this);
            textView.setText(text);
            textView.setGravity(Gravity.CENTER);
            return textView;
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

class ReceiptItemManager {
    private static final String TAG = "ReceiptItemManager";
    private final String receiptNo;
    private final DatabaseHelper databaseHelper;

    public Set<String> getScannedSerials() {
        return new HashSet<>(scannedSerials);
    }

    public Map<String, Integer> getScannedItemCounts() {
        return new HashMap<>(scannedItemCounts);
    }

    public Map<String, Integer> getItemQuantities() {
        return new HashMap<>(itemQuantities);
    }

    public Map<String, String> getItemNames() {
        return new HashMap<>(itemNames);
    }

    // Updated data structures
    private Map<String, Integer> itemQuantities = new HashMap<>(); // itemCode -> total quantity
    private Map<String, Integer> scannedItemCounts = new HashMap<>(); // itemCode -> scanned count
    private Map<String, String> itemNames = new HashMap<>(); // itemCode -> itemName
    private Set<String> scannedSerials = new HashSet<>(); // KAREKODNO serials
    private List<ScannedQRItem> qrCodeCache = new ArrayList<>();

    public enum ScanResult {
        SUCCESS, ALREADY_SCANNED, ITEM_NOT_IN_RECEIPT, COMPLETE_ITEM
    }

    public static class ScannedQRItem {
        String serialNumber;  // KAREKODNO serial
        String itemCode;      // Tiger item code
        String orgnr;

        public ScannedQRItem(String serialNumber, String itemCode, String orgnr) {
            this.serialNumber = serialNumber;
            this.itemCode = itemCode;
            this.orgnr = orgnr;
        }
    }
    // Add this method to load draft data
    public void loadDraftData(DraftData draftData) {
        Log.d(TAG, "=== Starting loadDraftData ===");

        // Only load data for items that exist in our filtered list
        Map<String, Integer> filteredCounts = new HashMap<>();
        for (Map.Entry<String, Integer> entry : draftData.scannedItemCounts.entrySet()) {
            if (itemQuantities.containsKey(entry.getKey())) {
                filteredCounts.put(entry.getKey(), entry.getValue());
                Log.d(TAG, String.format("Loading draft count for valid item: %s -> %d",
                        entry.getKey(), entry.getValue()));
            } else {
                Log.d(TAG, "Skipping draft count for invalid item: " + entry.getKey());
            }
        }

        this.scannedItemCounts = filteredCounts;
        this.scannedSerials = new HashSet<>(draftData.scannedSerials);

        Log.d(TAG, "=== End loadDraftData ===");
    }
    public ReceiptItemManager(String receiptNo, DatabaseHelper databaseHelper) {
        this.receiptNo = receiptNo;
        this.databaseHelper = databaseHelper;
    }

    // Debug method to help us understand what's in the maps
    void logItemState() {
        Log.d(TAG, "Current state of item tracking:");
        Log.d(TAG, "itemQuantities size: " + itemQuantities.size());
        for (Map.Entry<String, Integer> entry : itemQuantities.entrySet()) {
            Log.d(TAG, String.format("Item: %s, Quantity: %d", entry.getKey(), entry.getValue()));
        }
        Log.d(TAG, "scannedItemCounts size: " + scannedItemCounts.size());
        for (Map.Entry<String, Integer> entry : scannedItemCounts.entrySet()) {
            Log.d(TAG, String.format("Item: %s, Scanned: %d", entry.getKey(), entry.getValue()));
        }
    }

// In ReceiptItemManager class

    public void loadReceiptItems() {
        try (Connection conn = databaseHelper.getTigerConnection()) {
            itemQuantities.clear();
            scannedItemCounts.clear();
            scannedSerials.clear();
            itemNames.clear();

            String anatoliaSoftShipPlanTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN");
            String anatoliaSoftShipPlanLineTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLANLINE");
            String tigerItemsTable = databaseHelper.getTigerDbItemsTableName("ITEMS");
            String astItemsTable = databaseHelper.getAnatoliaSoftTableName("AST_ITEMS");

            String query = String.format(
                    "WITH FilteredItems AS (" +
                            "    SELECT " +
                            "        IT.CODE AS ItemCode, " +
                            "        IT.NAME AS ItemName, " +
                            "        SHPL.QUANTITY AS Quantity " +
                            "    FROM %s SHP " +
                            "    INNER JOIN %s SHPL ON SHP.ID = SHPL.SHIPPLANID " +
                            "    INNER JOIN %s IT ON IT.LOGICALREF = SHPL.ERPITEMID " +
                            "    LEFT JOIN %s AST_IT ON AST_IT.CODE = IT.CODE " +  // Join with AST_ITEMS
                            "    WHERE SHP.SLIPNR = ? " +
                            "    AND SHP.STATUS = 0 " +
                            "    AND IT.STGRPCODE <> 'TRAVERS' " +
                            "    AND (AST_IT.GROUPCODE2 IS NULL OR AST_IT.GROUPCODE2 <> 'DIREKDEM') " +  // Exclude DIREKDEM
                            ")" +
                            "SELECT " +
                            "    ItemCode, " +
                            "    ItemName, " +
                            "    SUM(Quantity) AS TotalQuantity " +
                            "FROM FilteredItems " +
                            "GROUP BY ItemCode, ItemName",
                    anatoliaSoftShipPlanTable,
                    anatoliaSoftShipPlanLineTable,
                    tigerItemsTable,
                    astItemsTable
            );

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, receiptNo);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String itemCode = rs.getString("ItemCode");
                        String itemName = rs.getString("ItemName");
                        int quantity = rs.getInt("TotalQuantity");

                        itemQuantities.put(itemCode, quantity);
                        itemNames.put(itemCode, itemName);
                        scannedItemCounts.put(itemCode, 0);
                    }
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error loading receipt items: " + e.getMessage(), e);
        }
    }
    // Add this method to help with debugging
    public void printItemCounts() {
        Log.d(TAG, "Total Items Count: " + getTotalItems());
        Log.d(TAG, "Scanned Items Count: " + getScannedCount());
    }
    private String extractSerialNumber(String qrCode) {
        Matcher matcher = Pattern.compile("KAREKODNO_([^|]+)").matcher(qrCode);
        return matcher.find() ? matcher.group(1) : "";
    }

    public String extractItemCodeFromQR(String qrCode) {
        int lastPipeIndex = qrCode.lastIndexOf("|");
        return lastPipeIndex != -1 && lastPipeIndex < qrCode.length() - 1
                ? qrCode.substring(lastPipeIndex + 1).trim()
                : "";
    }

    public ScanResult cacheScannedItem(String qrCode) {
        try {
            String serialNumber = extractSerialNumber(qrCode);
            String itemCode = extractItemCodeFromQR(qrCode);

            // Debug logs
            Log.d(TAG, "Attempting to scan item: " + itemCode);
            Log.d(TAG, "Current valid items in itemQuantities: " + itemQuantities.keySet());

            // Strict validation - only allow items that are in our filtered itemQuantities map
            if (!itemQuantities.containsKey(itemCode)) {
                Log.d(TAG, "Rejected: Item not in filtered list: " + itemCode);
                return ScanResult.ITEM_NOT_IN_RECEIPT;
            }

            // Check for duplicate serial
            if (scannedSerials.contains(serialNumber)) {
                Log.d(TAG, "Rejected: Serial already scanned: " + serialNumber);
                return ScanResult.ALREADY_SCANNED;
            }

            // Get the allowed quantity for this item
            int allowedQuantity = itemQuantities.get(itemCode);
            int currentCount = scannedItemCounts.getOrDefault(itemCode, 0);

            Log.d(TAG, String.format("Item: %s, Current Count: %d, Allowed: %d",
                    itemCode, currentCount, allowedQuantity));

            if (currentCount >= allowedQuantity) {
                Log.d(TAG, "Rejected: Item quantity exceeded");
                return ScanResult.COMPLETE_ITEM;
            }

            // If we get here, the item is valid and can be scanned
            scannedSerials.add(serialNumber);
            qrCodeCache.add(new ScannedQRItem(serialNumber, itemCode, null));
            scannedItemCounts.put(itemCode, currentCount + 1);

            Log.d(TAG, String.format("Successfully scanned item %s. New count: %d/%d",
                    itemCode, currentCount + 1, allowedQuantity));

            return ScanResult.SUCCESS;

        } catch (Exception e) {
            Log.e(TAG, "Error in cacheScannedItem: " + e.getMessage(), e);
            return ScanResult.ITEM_NOT_IN_RECEIPT;
        }
    }

    public boolean bulkInsertScannedItems() {
        if (qrCodeCache.isEmpty()) {
            return false;
        }

        try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {
            String shipPlanTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN");
            String shipPlanQRTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN_QR");

            // Get ORGNR and ID from AST_SHIPPLAN
            String findShipPlanQuery = String.format(
                    "SELECT ID, ORGNR FROM %s WHERE STATUS = 0 AND SLIPNR = ?",
                    shipPlanTable
            );

            String orgnr = null;
            int shipPlanId = -1;

            try (PreparedStatement stmt = conn.prepareStatement(findShipPlanQuery)) {
                stmt.setString(1, receiptNo);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        orgnr = rs.getString("ORGNR");
                        shipPlanId = rs.getInt("ID");
                    }
                }
            }

            if (orgnr == null || shipPlanId == -1) {
                Log.e(TAG, "Could not find ORGNR or ID for receipt: " + receiptNo);
                return false;
            }

            // Insert QR records
            String insertQuery = String.format(
                    "INSERT INTO %s (SHP_SERIALNO, SHP_ITEMCODE, SHP_ID, SHIPPLANID) VALUES (?, ?, ?, ?)",
                    shipPlanQRTable
            );

            try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                for (ScannedQRItem item : qrCodeCache) {
                    insertStmt.setString(1, item.serialNumber);
                    insertStmt.setString(2, item.itemCode);
                    insertStmt.setString(3, orgnr);
                    insertStmt.setInt(4, shipPlanId);  // Add SHIPPLANID
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }

            qrCodeCache.clear();
            return true;

        } catch (SQLException e) {
            Log.e(TAG, "Error bulk inserting scanned items", e);
            return false;
        }
    }

    // Helper methods for UI
    public boolean areAllItemsScanned() {
        return itemQuantities.entrySet().stream()
                .allMatch(entry ->
                        scannedItemCounts.getOrDefault(entry.getKey(), 0) >= entry.getValue()
                );
    }

    // Update getTotalItems to be more explicit
    public int getTotalItems() {
        // Use a local variable to track the total
        int total = 0;

        // Log what we're about to count
        Log.d(TAG, "=== Starting getTotalItems count ===");
        Log.d(TAG, "Current itemQuantities map contents:");
        for (Map.Entry<String, Integer> entry : itemQuantities.entrySet()) {
            Log.d(TAG, String.format("Item in map: %s -> %d", entry.getKey(), entry.getValue()));
        }

        // Only count items from our itemQuantities map
        total = itemQuantities.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        Log.d(TAG, "Final total items count: " + total);
        Log.d(TAG, "=== End getTotalItems count ===");
        return total;
    }

    // Update getScannedCount to match the strict validation
    public int getScannedCount() {
        // Use a local variable to track the total
        int total = 0;

        // Log what we're about to count
        Log.d(TAG, "=== Starting getScannedCount ===");
        Log.d(TAG, "Current scannedItemCounts map contents:");
        for (Map.Entry<String, Integer> entry : scannedItemCounts.entrySet()) {
            Log.d(TAG, String.format("Scanned item in map: %s -> %d", entry.getKey(), entry.getValue()));
        }

        // Only count scanned items that exist in our itemQuantities map
        for (String itemCode : itemQuantities.keySet()) {
            int scannedCount = scannedItemCounts.getOrDefault(itemCode, 0);
            total += scannedCount;
            Log.d(TAG, String.format("Adding to scanned total: Item %s, Count %d", itemCode, scannedCount));
        }

        Log.d(TAG, "Final scanned count: " + total);
        Log.d(TAG, "=== End getScannedCount ===");
        return total;
    }
    // Helper method to check if an item is valid
    private boolean isValidItem(String itemCode) {
        return itemQuantities.containsKey(itemCode);
    }
    public int getScannedCountForItem(String itemName) {
        // Find the item code by name
        String itemCode = itemNames.entrySet().stream()
                .filter(entry -> entry.getValue().equals(itemName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        return itemCode != null ? scannedItemCounts.getOrDefault(itemCode, 0) : 0;
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
    public void hidePreview() {
        if (surfaceView != null) {
            surfaceView.setVisibility(View.GONE);
            Log.d("CameraPreview", "SurfaceView hidden");
        }
    }

    public void showPreview() {
        if (surfaceView != null) {
            surfaceView.setVisibility(View.VISIBLE);
            Log.d("CameraPreview", "SurfaceView visible");
        }
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

        // Center the surface view within the available space
        int surfaceWidth = width;
        int surfaceHeight = height;

        // If you want to maintain a square aspect ratio
        int size = Math.min(width, height);
        surfaceWidth = size;
        surfaceHeight = size;

        // Calculate the position to center the surface view
        int leftOffset = (width - surfaceWidth) / 2;
        int topOffset = (height - surfaceHeight) / 2;

        surfaceView.layout(
                leftOffset,
                topOffset,
                leftOffset + surfaceWidth,
                topOffset + surfaceHeight
        );
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Get the width measurement
        int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);

        // Make the height match the width for a square preview
        setMeasuredDimension(width, width);

        // Measure the surface view with the same dimensions
        surfaceView.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        );
    }
}