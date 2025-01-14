package com.example.A_Soft;

import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.text.LineBreaker;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import android.content.SharedPreferences;
import com.google.gson.Gson;


public class SevkiyatQR_ScreenActivity extends AppCompatActivity {
    private static final String TAG = "SevkiyatQR_ScreenActivity";
    private static final Pattern QR_SERIAL_PATTERN = Pattern.compile("KAREKODNO_([^|]+)");
    private boolean inspectMode = false;
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

        // Get the FICHENO and inspect mode from the intent
        currentReceiptNo = getIntent().getStringExtra("FICHENO");
        inspectMode = getIntent().getBooleanExtra("INSPECT_MODE", false);

        // Initialize components with inspect mode consideration
        initializeComponents();
        // Load any existing draft data (only if not in inspect mode)
        if (!inspectMode) {
            loadDraftData();
        }
        // Load any existing draft data
        loadDraftData();

        // Check for camera permission (only if not in inspect mode)
        if (!inspectMode && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            setupBarcodeDetection();
        } else if (!inspectMode) {
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
        // Initialize basic views
        tableLayout = findViewById(R.id.tableLayout);
        cameraPreview = findViewById(R.id.camera_preview);
        scanStatusTextView = findViewById(R.id.scan_status);
        confirmReceiptButton = findViewById(R.id.confirm_receipt_button);
        ImageButton manualQrButton = findViewById(R.id.manual_qr_button);
        saveAsDraftButton = findViewById(R.id.save_draft_button);
        ImageButton scanButton = findViewById(R.id.scanButton);
        cameraStateButton = findViewById(R.id.camera_state);

        // Make sure the table is visible regardless of mode
        tableLayout.setVisibility(View.VISIBLE);

        // Handle inspect mode UI adjustments
        if (inspectMode) {
            // Hide all interactive buttons
            LinearLayout mainButtonsGrid = findViewById(R.id.main_buttons_grid);
            mainButtonsGrid.setVisibility(View.GONE);
            LinearLayout bottomButtonsGrid = findViewById(R.id.bottom_buttons_grid);
            bottomButtonsGrid.setVisibility(View.GONE);
            LinearLayout cameraStateGrid = findViewById(R.id.camera_state_grid);
            cameraStateGrid.setVisibility(View.GONE);

            cameraPreview.setVisibility(View.GONE);

            // Add inspection mode indicator at the top
            TextView inspectModeIndicator = new TextView(this);
            inspectModeIndicator.setText("İNCELEME MODU");
            inspectModeIndicator.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            inspectModeIndicator.setTypeface(null, Typeface.BOLD);
            inspectModeIndicator.setGravity(Gravity.CENTER);
            inspectModeIndicator.setPadding(0, 20, 0, 20);
            inspectModeIndicator.setTextSize(18);

            // Add the indicator at the top of the layout
            ViewGroup rootLayout = findViewById(android.R.id.content);
            rootLayout.addView(inspectModeIndicator, 0);
        }

        // Style the table
        styleTableLayout();

        // Initialize DatabaseHelper
        databaseHelper = new DatabaseHelper(this);

        // Setup executor and item manager
        executorService = Executors.newSingleThreadExecutor();
        itemManager = new ReceiptItemManager(currentReceiptNo, databaseHelper, this);

        // First load receipt items - this will initialize our data structures
        loadReceiptItems();

        // Then populate the table with the loaded data, passing inspect mode flag
        new FetchItemsTask(databaseHelper, inspectMode).execute(currentReceiptNo);

        // Only set up interactive components if not in inspect mode
        if (!inspectMode) {
            // Setup manual QR input button
            manualQrButton.setOnClickListener(v -> showManualQRInputDialog());

            // Setup camera state button
            cameraStateButton.setOnClickListener(v -> toggleCamera());

            // Setup scan button
            scanButton.setOnClickListener(v -> showScannerInputDialog());

            // Setup confirm receipt button
            confirmReceiptButton.setOnClickListener(v -> {
                if (itemManager.areAllItemsScanned()) {
                    showConfirmationDialog();
                } else {
                    showAlert("Uyarı", "Tamamlanmadan önce tüm malzemeler taratılmalıdır.");
                }
            });

            // Setup save as draft button
            saveAsDraftButton.setOnClickListener(v -> saveDraft());
        }

        // Start with camera off
        if (cameraPreview != null) {
            cameraPreview.stopCamera();
            cameraPreview.setVisibility(View.GONE);
        }
        isCameraActive = false;
        if (cameraStateButton != null) {
            cameraStateButton.setImageResource(R.drawable.camera_off);
        }

        // Update scan status for initial display
        updateScanStatus();
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
        finish();
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
        tableLayout.setStretchAllColumns(true);
        tableLayout.setShrinkAllColumns(true);
        tableLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Remove any default margins or padding that might create extra space
        tableLayout.setPadding(0, 0, 0, 0);
        tableLayout.setClipToPadding(true);
    }

    private void styleHeaderCell(TextView cell) {
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                0,
                TableRow.LayoutParams.WRAP_CONTENT,
                1.0f
        );

        cell.setLayoutParams(params);
        cell.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
        cell.setTextColor(getResources().getColor(android.R.color.white));

        int padding = dpToPx(8);
        cell.setPadding(padding, padding, padding, padding);
    }

    private void styleDataCell(TextView cell) {
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                0,
                TableRow.LayoutParams.WRAP_CONTENT,
                1.0f
        );

        cell.setLayoutParams(params);

        // Add borders
        GradientDrawable border = new GradientDrawable();
        border.setColor(getResources().getColor(android.R.color.white));
        border.setStroke(1, getResources().getColor(android.R.color.darker_gray));
        cell.setBackground(border);

        // Minimal padding
        int padding = dpToPx(4);
        cell.setPadding(padding, padding, padding, padding);
    }

    // Helper method to convert dp to pixels
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
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
        karekodInput.setHint("Karekod No Ör:561007ENT22000401 ");
        layout.addView(karekodInput);

        // Add some spacing
        View spacing = new View(this);
        spacing.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                30)); // 30dp spacing
        layout.addView(spacing);

        // Create Barcode input
        final EditText barcodeInput = new EditText(this);
        barcodeInput.setHint("Malzeme Kodu");
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
            // Update this line if it exists
            runOnUiThread(() -> {
                loadDraftData();
                new FetchItemsTask(databaseHelper, inspectMode).execute(currentReceiptNo);
                updateScanStatus();
            });
        });
    }
    private void updateReceiptStatus() {
        executorService.submit(() -> {
            try (Connection connection = databaseHelper.getAnatoliaSoftConnection()) {
                // First check stock levels
                // In updateReceiptStatus() method, update the stockQuery:
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
                        "AND i.GROUPCODE = 'DIREK' " +  // Only include DIREK items
                        "AND (i.GROUPCODE2 IS NULL OR i.GROUPCODE2 <> 'DIREKDEM') " +  // Exclude DIREKDEM
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
                        "AND ITMAS.GROUPCODE = 'DIREK' " +  // Only include DIREK items
                        "AND (ITMAS.GROUPCODE2 IS NULL OR ITMAS.GROUPCODE2 <> 'DIREKDEM') " +  // Exclude DIREKDEM
                        "GROUP BY ITMAS.CODE " +
                        "UNION ALL " +
                        "SELECT ITM.CODE, SUM((CASE PRDSLP.SLIPTYPE WHEN 1 THEN 1 WHEN 2 THEN -1 END) * PRDTRN.QUANTITY) as MIKTAR " +
                        "FROM " + databaseHelper.getAnatoliaSoftTableName("AST_PRODUCTION_ITEMS") + " PRDTRN " +
                        "INNER JOIN " + databaseHelper.getAnatoliaSoftTableName("AST_PRODUCTION_SLIPS") + " PRDSLP ON PRDTRN.SLIPID=PRDSLP.ID " +
                        "INNER JOIN " + databaseHelper.getAnatoliaSoftTableName("AST_ITEMS") + " ITM ON ITM.CODE=PRDTRN.MALZEME " +
                        "WHERE ITM.GROUPCODE = 'DIREK' " +  // Only include DIREK items
                        "AND (ITM.GROUPCODE2 IS NULL OR ITM.GROUPCODE2 <> 'DIREKDEM') " +  // Exclude DIREKDEM
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
        private final boolean inspectMode;

        public FetchItemsTask(DatabaseHelper databaseHelper, boolean inspectMode) {
            this.databaseHelper = databaseHelper;
            this.inspectMode = inspectMode;
        }

        @Override
        protected List<DraftReceipt> doInBackground(String... params) {
            List<DraftReceipt> detailsList = new ArrayList<>();
            String slipNr = params[0];

            try (Connection connection = databaseHelper.getAnatoliaSoftConnection()) {
                // First, check if this is a completed receipt
                String statusQuery = String.format(
                        "SELECT STATUS FROM %s WHERE SLIPNR = ?",
                        databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN")
                );

                boolean isCompleted = false;
                try (PreparedStatement statusStmt = connection.prepareStatement(statusQuery)) {
                    statusStmt.setString(1, slipNr);
                    try (ResultSet rs = statusStmt.executeQuery()) {
                        if (rs.next()) {
                            isCompleted = rs.getInt("STATUS") == 1;
                        }
                    }
                }

                // Main query to get items
                String query = String.format(
                        "WITH FilteredItems AS (" +
                                "    SELECT " +
                                "        IT.NAME AS ItemName, " +
                                "        SHPL.QUANTITY AS ItemQuantity " +
                                "    FROM %s SHP " +
                                "    INNER JOIN %s SHPL ON SHP.ID = SHPL.SHIPPLANID " +
                                "    INNER JOIN %s IT ON IT.LOGICALREF = SHPL.ERPITEMID " +
                                "    INNER JOIN %s AST_IT ON AST_IT.CODE = IT.CODE " +
                                "    WHERE SHP.SLIPNR = ? " +
                                "    AND AST_IT.GROUPCODE = 'DIREK' " +
                                "    AND (AST_IT.GROUPCODE2 IS NULL OR AST_IT.GROUPCODE2 <> 'DIREKDEM') " +
                                ") " +
                                "SELECT " +
                                "    ItemName AS [Malzeme Adı], " +
                                "    CAST(SUM(ItemQuantity) AS VARCHAR) AS [Miktar] " +
                                "FROM FilteredItems " +
                                "GROUP BY ItemName",
                        databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN"),
                        databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLANLINE"),
                        databaseHelper.getTigerDbItemsTableName("ITEMS"),
                        databaseHelper.getAnatoliaSoftTableName("AST_ITEMS")
                );

                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, slipNr);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            String amount = resultSet.getString("Miktar");
                            DraftReceipt receipt = new DraftReceipt(
                                    resultSet.getString("Malzeme Adı"),
                                    amount
                            );

                            // If we're in inspect mode and the receipt is completed,
                            // set the scanned amount equal to the required amount
                            if (inspectMode && isCompleted) {
                                receipt.setAmount(amount);  // Set scanned amount equal to required amount
                            }

                            detailsList.add(receipt);
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

            // Add table headers with fixed text
            TableRow headerRow = new TableRow(SevkiyatQR_ScreenActivity.this);
            headerRow.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT));

            // Create header cells with forced line breaks
            TextView headerMaterial = createHeaderTextView("\nMalzemeler");  // Force break
            TextView headerExpected = createHeaderTextView("Okutulacak\nMiktar");  // Force break
            TextView headerScanned = createHeaderTextView("Okunan\nMiktar");  // Force break

            // Style headers
            styleHeaderCell(headerMaterial);
            styleHeaderCell(headerExpected);
            styleHeaderCell(headerScanned);

            headerRow.addView(headerMaterial);
            headerRow.addView(headerExpected);
            headerRow.addView(headerScanned);
            tableLayout.addView(headerRow);

            // Add data rows
            for (DraftReceipt detail : details) {
                TableRow row = new TableRow(SevkiyatQR_ScreenActivity.this);
                row.setLayoutParams(new TableRow.LayoutParams(
                        TableRow.LayoutParams.MATCH_PARENT,
                        TableRow.LayoutParams.WRAP_CONTENT));

                TextView materialView = createTextView(detail.getMaterialName());
                TextView amountView = createTextView(detail.getAmount().replace(".00", ""));
                TextView scannedView = createTextView(detail.getAmount().replace(".00", ""));

                styleDataCell(materialView);
                styleDataCell(amountView);
                styleDataCell(scannedView);

                row.addView(materialView);
                row.addView(amountView);
                row.addView(scannedView);

                if (!inspectMode) {
                    row.setOnLongClickListener(v -> {
                        showItemOptionsDialog(detail.getMaterialName());
                        return true;
                    });
                }

                tableLayout.addView(row);
            }

            // Remove any extra spacing after the last row
            tableLayout.setClipChildren(true);
            tableLayout.setClipToPadding(true);


            // Update scan status with total items if in inspect mode
            if (inspectMode) {
                int totalItems = details.stream()
                        .mapToInt(d -> Integer.parseInt(d.getAmount().replace(".00", "")))
                        .sum();
                scanStatusTextView.setText(String.format("Okutulacak malzemeler: %d/%d", totalItems, totalItems));
            }
            else updateScanStatus();
        }
        private TextView createHeaderTextView(String text) {
            TextView textView = new TextView(SevkiyatQR_ScreenActivity.this);
            textView.setText(text);
            textView.setMaxLines(2); // Allow 2 lines for headers
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            textView.setTypeface(null, Typeface.BOLD);
            textView.setGravity(Gravity.CENTER);

            // Force text to break at the end of first word if possible
            textView.setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
            textView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);

            TableRow.LayoutParams params = new TableRow.LayoutParams(
                    0,
                    TableRow.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            textView.setLayoutParams(params);

            return textView;
        }
        private void showItemOptionsDialog(String itemName) {
            AlertDialog.Builder builder = new AlertDialog.Builder(SevkiyatQR_ScreenActivity.this);
            builder.setTitle("İşlem Seçin")
                    .setItems(new CharSequence[]{"Malzemeyi Kaldır", "Okutulan Ürünü Kaldır"}, (dialog, which) -> {
                        switch (which) {
                            case 0: // Remove Item
                                showRemoveItemDialog(itemName);
                                break;
                            case 1: // Remove Scanned Item
                                showRemoveScannedItemDialog(itemName);
                                break;
                        }
                    })
                    .setNegativeButton("İptal", (dialog, which) -> dialog.dismiss())
                    .show();
        }
        private TextView createTextView(String text) {
            TextView textView = new TextView(SevkiyatQR_ScreenActivity.this);
            textView.setText(text);

            // Set text properties
            textView.setMaxLines(1); // Changed to 1 line to prevent wrapping
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

            // Center text
            textView.setGravity(Gravity.CENTER);

            // Set layout parameters
            TableRow.LayoutParams params = new TableRow.LayoutParams(
                    0,
                    TableRow.LayoutParams.WRAP_CONTENT,
                    1.0f
            );

            // Add minimal padding
            int padding = dpToPx(4);
            textView.setPadding(padding, padding, padding, padding);

            textView.setLayoutParams(params);
            return textView;
        }
    }
    private void showRemoveItemDialog(String itemName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Malzeme Kaldır")
                .setMessage("Bu malzemeyi kaldırmak istediğinize emin misiniz?")
                .setPositiveButton("Tamamen Kaldır", (dialog, which) -> {
                    // Show loading indicator
                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage("İşlem yapılıyor...");
                    progressDialog.show();

                    // Execute removal operation on background thread
                    executorService.submit(() -> {
                        boolean success = itemManager.removeItemFromShipment(itemName);

                        if (success) {
                            // Load receipt items in background
                            itemManager.loadReceiptItems();

                            // Update UI on main thread
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                showToast("Malzeme sevkiyattan kaldırıldı");
                                // Pass inspectMode to FetchItemsTask
                                new FetchItemsTask(databaseHelper, inspectMode).execute(currentReceiptNo);
                            });
                        } else {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                showAlert("Hata", "Malzeme kaldırılamadı");
                            });
                        }
                    });
                })
                .setNeutralButton("İptal", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showRemoveScannedItemDialog(String itemName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        EditText serialInput = new EditText(this);
        serialInput.setHint("Seri Numarası");
        layout.addView(serialInput);

        builder.setTitle("Okutulan Ürünü Kaldır")
                .setView(layout)
                .setPositiveButton("Kaldır", (dialog, which) -> {
                    String serialNumber = serialInput.getText().toString().trim();
                    if (!serialNumber.isEmpty()) {
                        // Show loading indicator
                        ProgressDialog progressDialog = new ProgressDialog(this);
                        progressDialog.setMessage("İşlem yapılıyor...");
                        progressDialog.show();

                        // Execute removal operation on background thread
                        executorService.submit(() -> {
                            boolean success = itemManager.removeScannedItem(itemName, serialNumber);
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                if (success) {
                                    showToast("Ürün kaldırıldı");
                                    updateScanStatus();
                                } else {
                                    showAlert("Hata", "Ürün bulunamadı");
                                }
                            });
                        });
                    }
                })
                .setNegativeButton("İptal", (dialog, which) -> dialog.dismiss())
                .show();
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
        // Update this line if it exists
        new FetchItemsTask(databaseHelper, inspectMode).execute(currentReceiptNo);
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
    private final Context context;  // Add this line
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
    public ReceiptItemManager(String receiptNo, DatabaseHelper databaseHelper, Context context) {
        this.receiptNo = receiptNo;
        this.databaseHelper = databaseHelper;
        this.context = context;
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
    public boolean removeScannedItem(String itemName, String serialNumber) {
        String itemCode = getItemCodeByName(itemName);
        if (itemCode != null && scannedSerials.contains(serialNumber)) {
            scannedSerials.remove(serialNumber);
            int currentCount = scannedItemCounts.getOrDefault(itemCode, 0);
            if (currentCount > 0) {
                scannedItemCounts.put(itemCode, currentCount - 1);

                // Remove from QR cache
                qrCodeCache.removeIf(item -> item.serialNumber.equals(serialNumber));
                return true;
            }
        }
        return false;
    }

    public boolean removeItemFromShipment(String itemName) {
        String itemCode = getItemCodeByName(itemName);
        if (itemCode == null) return false;

        try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {
            // First, get the SHIPPLANID
            String findShipPlanQuery = String.format(
                    "SELECT ID FROM %s WHERE SLIPNR = ?",
                    databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN")
            );

            int shipPlanId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(findShipPlanQuery)) {
                stmt.setString(1, receiptNo);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        shipPlanId = rs.getInt("ID");
                    }
                }
            }

            if (shipPlanId != -1) {
                // Delete from AST_SHIPPLANLINE and AST_SHIPPLAN_QR
                String deleteLineQuery = String.format(
                        "DELETE FROM %s WHERE SHIPPLANID = ? AND ERPITEMID IN " +
                                "(SELECT LOGICALREF FROM %s WHERE CODE = ?)",
                        databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLANLINE"),
                        databaseHelper.getTigerDbItemsTableName("ITEMS")
                );

                String deleteQRQuery = String.format(
                        "DELETE FROM %s WHERE SHIPPLANID = ? AND SHP_ITEMCODE = ?",
                        databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN_QR")
                );

                // Execute deletions
                try (PreparedStatement deleteLineStmt = conn.prepareStatement(deleteLineQuery);
                     PreparedStatement deleteQRStmt = conn.prepareStatement(deleteQRQuery)) {

                    // Delete from AST_SHIPPLANLINE
                    deleteLineStmt.setInt(1, shipPlanId);
                    deleteLineStmt.setString(2, itemCode);
                    int affectedLines = deleteLineStmt.executeUpdate();

                    // Delete from AST_SHIPPLAN_QR
                    deleteQRStmt.setInt(1, shipPlanId);
                    deleteQRStmt.setString(2, itemCode);
                    deleteQRStmt.executeUpdate();

                    if (affectedLines > 0) {
                        // Clear all local tracking data
                        itemQuantities.remove(itemCode);
                        scannedItemCounts.remove(itemCode);
                        itemNames.remove(itemCode);
                        qrCodeCache.removeIf(item -> item.itemCode.equals(itemCode));

                        // Remove from draft data in SharedPreferences
                        clearItemFromDraftData(itemCode);

                        // Remove all serials for this item
                        scannedSerials.removeAll(
                                qrCodeCache.stream()
                                        .filter(item -> item.itemCode.equals(itemCode))
                                        .map(item -> item.serialNumber)
                                        .collect(Collectors.toSet())
                        );

                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error removing item from shipment", e);
        }
        return false;
    }

    private void clearItemFromDraftData(String itemCode) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("SevkiyatDrafts", Context.MODE_PRIVATE);
        String draftKey = "draft_data_" + receiptNo;
        String json = sharedPreferences.getString(draftKey, null);

        if (json != null) {
            Gson gson = new Gson();
            DraftData draftData = gson.fromJson(json, DraftData.class);

            // Remove item data from draft
            draftData.scannedItemCounts.remove(itemCode);
            draftData.itemQuantities.remove(itemCode);
            draftData.itemNames.remove(itemCode);

            // Save updated draft data
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(draftKey, gson.toJson(draftData));
            editor.apply();
        }
    }

    private String getItemCodeByName(String itemName) {
        return itemNames.entrySet().stream()
                .filter(entry -> entry.getValue().equals(itemName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    // In ReceiptItemManager.java, modify loadReceiptItems():

    public void loadReceiptItems() {
        Log.d(TAG, "Loading receipt items for receipt: " + receiptNo);

        try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {  // Changed to AnatoliaSoft connection
            itemQuantities.clear();
            scannedItemCounts.clear();
            itemNames.clear();

            String query = String.format(
                    "WITH FilteredItems AS (" +
                            "    SELECT " +
                            "        IT.CODE AS ItemCode, " +
                            "        IT.NAME AS ItemName, " +
                            "        SHPL.QUANTITY AS ItemQuantity " +
                            "    FROM %s SHP " +
                            "    INNER JOIN %s SHPL ON SHP.ID = SHPL.SHIPPLANID " +
                            "    INNER JOIN %s IT ON IT.LOGICALREF = SHPL.ERPITEMID " +
                            "    INNER JOIN %s AST_IT ON AST_IT.CODE = IT.CODE " +
                            "    WHERE SHP.SLIPNR = ? " +
                            "    AND AST_IT.GROUPCODE = 'DIREK' " +  // Only include DIREK items
                            "    AND (AST_IT.GROUPCODE2 IS NULL OR AST_IT.GROUPCODE2 <> 'DIREKDEM') " +  // Exclude DIREKDEM
                            ")" +
                            "SELECT " +
                            "    ItemCode, " +
                            "    ItemName, " +
                            "    SUM(ItemQuantity) AS TotalQuantity " +
                            "FROM FilteredItems " +
                            "GROUP BY ItemCode, ItemName",
                    databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN"),
                    databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLANLINE"),
                    databaseHelper.getTigerDbItemsTableName("ITEMS"),
                    databaseHelper.getAnatoliaSoftTableName("AST_ITEMS")
            );

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, receiptNo);

                Log.d(TAG, "Executing query for receipt: " + receiptNo);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String itemCode = rs.getString("ItemCode");
                        String itemName = rs.getString("ItemName");
                        int quantity = rs.getInt("TotalQuantity");

                        Log.d(TAG, String.format("Found item: Code=%s, Name=%s, Quantity=%d",
                                itemCode, itemName, quantity));

                        itemQuantities.put(itemCode, quantity);
                        itemNames.put(itemCode, itemName);
                        scannedItemCounts.put(itemCode, 0);
                    }
                }
            }

            // Log the final state
            Log.d(TAG, "Finished loading items. Total unique items: " + itemQuantities.size());
            for (Map.Entry<String, Integer> entry : itemQuantities.entrySet()) {
                Log.d(TAG, String.format("Loaded: %s -> %d", entry.getKey(), entry.getValue()));
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

            // First check if item exists in our filtered list
            if (!itemQuantities.containsKey(itemCode)) {
                Log.d(TAG, "Rejected: Item not in filtered list: " + itemCode);
                return ScanResult.ITEM_NOT_IN_RECEIPT;
            }

            // Check if serial exists in AST_SHIPPLAN_QR table
            try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {
                String query = String.format(
                        "SELECT COUNT(*) as count FROM %s WHERE SHP_SERIALNO = ?",
                        databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN_QR")
                );

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, serialNumber);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt("count") > 0) {
                            Log.d(TAG, "Rejected: Serial already exists in database: " + serialNumber);
                            return ScanResult.ALREADY_SCANNED;
                        }
                    }
                }
            } catch (SQLException e) {
                Log.e(TAG, "Error checking serial in database", e);
            }

            // Check for duplicate serial in current session
            if (scannedSerials.contains(serialNumber)) {
                Log.d(TAG, "Rejected: Serial already scanned in current session: " + serialNumber);
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