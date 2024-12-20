package com.example.A_Soft;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.View;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import android.content.SharedPreferences;
import com.google.gson.Gson;

public class ProductionScanActivity extends AppCompatActivity {
    private static final String TAG = "ProductionScanActivity";
    private static final Pattern KAREKODNO_PATTERN = Pattern.compile("KAREKODNO_([^|]+)");
    private static final Pattern TEDASKIRILIM_PATTERN = Pattern.compile("TEDASKIRILIM_([^|]+)");
    private static final Pattern MARKA_PATTERN = Pattern.compile("MARKA_([^|]+)");
    private static final Pattern MALZEME_PATTERN = Pattern.compile("MALZEME_([^|]+)");
    private static final Pattern TIPI_PATTERN = Pattern.compile("TIPI_([^|]+)");
    private static final Pattern IMALYILI_PATTERN = Pattern.compile("IMALYILI_(\\d+)");
    private static final long SCAN_DEBOUNCE_INTERVAL = 2000; // 2 seconds
    private static final String PREF_NAME = "ProductionDrafts";
    private static final String KEY_DRAFT_DATA = "draft_data";
    private SharedPreferences sharedPreferences;
    private boolean isProcessing = false; // Add this as a class field

    private TableLayout tableLayout;
    private CameraSourcePreview cameraPreview;
    private TextView scanStatusTextView;
    private Button saveButton, confirmButton, manualQrButton, scannedItemsButton;
    private DatabaseHelper databaseHelper;
    private String currentReceiptNo;
    private ProductionReceiptManager receiptManager;
    private ExecutorService executorService;
    private long lastScanTime = 0;
    private Toast currentToast;
    private ProductionReceipt currentReceipt;
    private Set<String> scannedKareKodNos = new HashSet<>();
    private Map<String, Integer> materialCounts = new HashMap<>();
    private List<ScannedItem> scannedItems = new ArrayList<>();
    private String lastScannedQR = ""; // Add this to track last scanned QR


    private static class ScannedItem {
        String kareKodNo;
        String materialName;
        String timestamp;

        ScannedItem(String kareKodNo, String materialName, String timestamp) {
            this.kareKodNo = kareKodNo;
            this.materialName = materialName;
            this.timestamp = timestamp;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_production_scan);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        currentReceiptNo = getIntent().getStringExtra("RECEIPT_NO");
        databaseHelper = new DatabaseHelper(this);
        executorService = Executors.newSingleThreadExecutor();

        initializeComponents();
        loadDraftData(); // Load any existing draft
        requestCameraPermissionIfNeeded();
    }
    private void requestCameraPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            setupBarcodeDetection();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    100);
        }
    }
    private void setupBarcodeDetection() {
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.QR_CODE)
                .build();

        if (!barcodeDetector.isOperational()) {
            showAlert("Hata", "Barkod okuyucu başlatılamadı", false);
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
                if (!isProcessing && detections != null && detections.getDetectedItems().size() > 0) {
                    Barcode barcode = detections.getDetectedItems().valueAt(0);
                    String qrCode = barcode.displayValue;

                    if (!qrCode.equals(lastScannedQR) ||
                            (System.currentTimeMillis() - lastScanTime) >= SCAN_DEBOUNCE_INTERVAL) {
                        lastScannedQR = qrCode;
                        processQRCode(qrCode);
                    }
                }
            }
        });
    }
    private void showAlert(String title, String message, boolean b) {
        showAlert(title, message, false);
    }
    private void initializeComponents() {
        tableLayout = findViewById(R.id.tableLayout);
        cameraPreview = findViewById(R.id.camera_preview);
        scanStatusTextView = findViewById(R.id.scan_status);

        // Initialize all buttons
        saveButton = findViewById(R.id.saveButton);           // This is for drafts
        confirmButton = findViewById(R.id.confirmButton);     // This is for production save
        manualQrButton = findViewById(R.id.manual_qr_button);
        scannedItemsButton = findViewById(R.id.scanned_items_button);

        styleTableLayout();
        setupButtons();
        loadInitialData();
    }

    private void styleTableLayout() {
        tableLayout.setBackgroundColor(getResources().getColor(android.R.color.white));
        tableLayout.setPadding(2, 2, 2, 2);

        // Add header row with just two columns
        TableRow headerRow = new TableRow(this);
        headerRow.addView(createHeaderTextView("Malzeme"));
        headerRow.addView(createHeaderTextView("Miktar"));
        tableLayout.addView(headerRow);
    }
    private void setupButtons() {
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> saveDraft());  // Save as draft
        }

        if (confirmButton != null) {
            confirmButton.setOnClickListener(v -> confirmProduction());  // Save to production
        }

        if (manualQrButton != null) {
            manualQrButton.setOnClickListener(v -> showManualQRInputDialog());
        }

        if (scannedItemsButton != null) {
            scannedItemsButton.setOnClickListener(v -> showScannedItemsList());
        }
    }
    private void saveDraft() {
        if (scannedItems.isEmpty()) {
            showToast("Kaydedilecek ürün bulunmamaktadır");
            return;
        }

        DraftData draftData = new DraftData(
                scannedKareKodNos,
                materialCounts,
                scannedItems
        );

        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(draftData);
        editor.putString(KEY_DRAFT_DATA + "_" + currentReceiptNo, json);
        editor.apply();

        showToast("Taslak kaydedildi");
        finish(); // Close the activity after saving draft
    }
    private void loadDraftData() {
        String json = sharedPreferences.getString(KEY_DRAFT_DATA + "_" + currentReceiptNo, null);
        if (json != null) {
            try {
                Gson gson = new Gson();
                DraftData draftData = gson.fromJson(json, DraftData.class);

                // Restore saved data
                scannedKareKodNos = new HashSet<>(draftData.scannedKareKodNos);
                materialCounts = new HashMap<>(draftData.materialCounts);
                scannedItems = new ArrayList<>(draftData.scannedItems);

                // Update UI
                updateTable();
                updateScanStatus();
            } catch (Exception e) {
                Log.e(TAG, "Error loading draft data", e);
                showToast("Taslak yükleme hatası");
            }
        }
    }
    private void clearDraft() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_DRAFT_DATA + "_" + currentReceiptNo);
        editor.apply();
    }

    // Draft data class
    private static class DraftData {
        Set<String> scannedKareKodNos;
        Map<String, Integer> materialCounts;
        List<ScannedItem> scannedItems;

        DraftData(Set<String> scannedKareKodNos,
                  Map<String, Integer> materialCounts,
                  List<ScannedItem> scannedItems) {
            this.scannedKareKodNos = scannedKareKodNos;
            this.materialCounts = materialCounts;
            this.scannedItems = scannedItems;
        }
    }
    private void confirmProduction() {
        if (scannedItems.isEmpty()) {
            showToast("Onaylanacak ürün bulunmamaktadır");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Onay")
                .setMessage("Üretimi onaylamak istediğinizden emin misiniz?")
                .setPositiveButton("Evet", (dialog, which) -> {
                    executorService.submit(() -> {
                        try {
                            saveToProductionScanned();
                            runOnUiThread(() -> {
                                showToast("Üretim onaylandı");
                                clearDraft(); // Clear the draft data
                                finish();     // Close the activity
                            });
                        } catch (SQLException e) {
                            Log.e(TAG, "Error saving production", e);
                            runOnUiThread(() -> showToast("Kayıt sırasında hata oluştu: " + e.getMessage()));
                        }
                    });
                })
                .setNegativeButton("Hayır", null)
                .show();
    }

    private TextView createHeaderTextView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(16, 16, 16, 16);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
        tv.setTextColor(getResources().getColor(android.R.color.white));
        return tv;
    }

    private void showScannedItemsList() {
        if (scannedItems.isEmpty()) {
            showToast("Henüz okutulan ürün bulunmamaktadır");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Okutulan Ürünler");

        // Create a ScrollView with LinearLayout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        for (ScannedItem item : scannedItems) {
            String qrCode = item.kareKodNo;
            String kareKodNo = extractPattern(qrCode, KAREKODNO_PATTERN);
            String tedasKirilim = extractPattern(qrCode, TEDASKIRILIM_PATTERN);
            String marka = extractPattern(qrCode, MARKA_PATTERN);
            String malzeme = extractPattern(qrCode, MALZEME_PATTERN);
            String tipi = extractPattern(qrCode, TIPI_PATTERN);
            String imalYili = extractPattern(qrCode, IMALYILI_PATTERN);
            String barkod = extractBarcode(qrCode);

            TextView itemView = new TextView(this);
            itemView.setText(String.format(
                    "KAREKODNO: %s\nTEDAŞ Kırılım: %s\nMarka: %s\nMalzeme: %s\nTip: %s\nİmal Yılı: %s\nBarkod: %s\nTarih: %s\n",
                    kareKodNo, tedasKirilim, marka, malzeme, tipi, imalYili, barkod, item.timestamp));
            itemView.setPadding(0, 10, 0, 10);
            layout.addView(itemView);

            // Add a separator line
            View separator = new View(this);
            separator.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
            separator.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            layout.addView(separator);
        }

        builder.setView(layout);
        builder.setPositiveButton("Kapat", null);
        builder.show();
    }

    private void showManualQRInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Manuel Ürün Girişi");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText tedasKirilimInput = createEditText("Seri No (örn: 561007ENT22000401)");
        final EditText barcodeInput = createEditText("Barkod");
        final EditText imalYiliInput = createEditText("Yıl");

        layout.addView(tedasKirilimInput);
        layout.addView(createSpacingView());
        layout.addView(barcodeInput);
        layout.addView(createSpacingView());
        layout.addView(imalYiliInput);

        builder.setView(layout);
        builder.setPositiveButton("Onayla", null);
        builder.setNegativeButton("İptal", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String fullKareKodNo = tedasKirilimInput.getText().toString().trim();
                String barcode = barcodeInput.getText().toString().trim();
                String imalYili = imalYiliInput.getText().toString().trim();

                if (fullKareKodNo.isEmpty() || barcode.isEmpty() || imalYili.isEmpty()) {
                    showToast("Lütfen tüm alanları doldurun");
                    return;
                }

                if (!fullKareKodNo.contains("ENT")) {
                    showToast("Seri No 'ENT' içermelidir (örn: 561007ENT22000401)");
                    return;
                }

                executorService.submit(() -> {
                    try {
                        String itemInfo = getItemInfoFromDatabase(fullKareKodNo, barcode);
                        if (itemInfo == null) {
                            showToast("Ürün veya TEDAŞ eşleşmesi bulunamadı");
                            return;
                        }

                        String[] itemParts = itemInfo.split("\\|");
                        if (itemParts.length != 3) {
                            showToast("Veritabanı hatası");
                            return;
                        }

                        String malzeme = itemParts[0];
                        String tipi = itemParts[1];

                        // Get TEDAS part (before ENT)
                        String tedasPart = fullKareKodNo.split("ENT")[0];

                        // Format the QR code string with KAREKODNO at the beginning
                        String formattedQR = String.format(
                                "KAREKODNO_%s|TEDASKIRILIM_%s|MARKA_%s|MALZEME_%s|TIPI_%s|IMALYILI_%s||%s",
                                fullKareKodNo,  // Use the complete serial number as KAREKODNO
                                tedasPart,      // Use only the part before ENT for TEDASKIRILIM
                                "ENT",
                                malzeme,
                                tipi,
                                imalYili,
                                barcode);

                        runOnUiThread(() -> dialog.dismiss());

                        // Debug log to verify the formatted QR string
                        Log.d(TAG, "Formatted QR: " + formattedQR);

                        // Process the QR code
                        processQRCode(formattedQR);

                    } catch (Exception e) {
                        Log.e(TAG, "Error processing manual entry", e);
                        showToast("İşlem sırasında hata oluştu: " + e.getMessage());
                    }
                });
            });
        });

        dialog.show();
    }
    private void debugQRCode(String qrCode) {
        Log.d(TAG, "QR Code Debug:");
        Log.d(TAG, "Full QR: " + qrCode);
        String karekodno = extractPattern(qrCode, KAREKODNO_PATTERN);
        Log.d(TAG, "KAREKODNO_PATTERN: " + KAREKODNO_PATTERN.pattern());
        Log.d(TAG, "KAREKODNO: " + karekodno);
        Log.d(TAG, "TEDASKIRILIM: " + extractPattern(qrCode, TEDASKIRILIM_PATTERN));
        Log.d(TAG, "MARKA: " + extractPattern(qrCode, MARKA_PATTERN));
        Log.d(TAG, "MALZEME: " + extractPattern(qrCode, MALZEME_PATTERN));
        Log.d(TAG, "TIPI: " + extractPattern(qrCode, TIPI_PATTERN));
        Log.d(TAG, "IMALYILI: " + extractPattern(qrCode, IMALYILI_PATTERN));
        Log.d(TAG, "BARCODE: " + extractBarcode(qrCode));
    }
    private String getItemInfoFromDatabase(String tedasKirilim, String barcode) throws SQLException {
        try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {
            // First check AST_TEMS_TEDAS for matching ITM_TEDAS
            String tedasCheck = "SELECT ITM_CODE FROM AST_TEMS_TEDAS WHERE ITM_TEDAS = ?";

            // Extract TEDAS value (part before ENT if exists)
            String tedasValue = tedasKirilim;
            if (tedasKirilim.contains("ENT")) {
                tedasValue = tedasKirilim.split("ENT")[0];
            }

            try (PreparedStatement tedasStmt = conn.prepareStatement(tedasCheck)) {
                tedasStmt.setString(1, tedasValue);
                try (ResultSet tedasRs = tedasStmt.executeQuery()) {
                    if (!tedasRs.next() || !tedasRs.getString("ITM_CODE").equals(barcode)) {
                        return null; // TEDAS validation failed
                    }
                }
            }

            // If TEDAS validation passed, proceed with item info query
            String itemQuery = "SELECT CODE, DESCRIPTION, GROUPCODE FROM AST_ITEMS WHERE CODE = ?";
            try (PreparedStatement stmt = conn.prepareStatement(itemQuery)) {
                stmt.setString(1, barcode);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String tipi = rs.getString("DESCRIPTION");
                        String malzeme = rs.getString("GROUPCODE");

                        // Include the full KAREKODNO in the return value
                        String kareKodNo = tedasKirilim; // Keep the full value including ENT part

                        return String.format("%s|%s|%s", malzeme, tipi, kareKodNo);
                    }
                }
            }
        }
        return null;
    }

    private String[] splitTedasKirilim(String input) {
        String[] result = new String[2];
        if (input != null && input.contains("ENT")) {
            String[] parts = input.split("ENT");
            result[0] = parts[0]; // TEDASKIRILIM
            result[1] = parts.length > 1 ? parts[1] : ""; // KAREKODNO
        } else {
            result[0] = input;
            result[1] = "";
        }
        return result;
    }
    private void processQRCode(String qrCodeData) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime < SCAN_DEBOUNCE_INTERVAL) {
            return;
        }

        try {
            debugQRCode(qrCodeData);
            String kareKodNo = "";
            String tedasKirilim = "";
            String marka = "";
            String malzeme = "";
            String tipi = "";
            String imalYili = "";
            String barcode;

            if (qrCodeData.contains("|")) {
                // First try to get KAREKODNO directly
                kareKodNo = extractPattern(qrCodeData, KAREKODNO_PATTERN);

                // If KAREKODNO is empty, try to get it from TEDASKIRILIM
                if (kareKodNo.isEmpty()) {
                    tedasKirilim = extractPattern(qrCodeData, TEDASKIRILIM_PATTERN);
                    if (tedasKirilim.contains("ENT")) {
                        kareKodNo = tedasKirilim; // Use the full TEDASKIRILIM value as KAREKODNO
                    }
                }

                // Extract other fields
                if (tedasKirilim.isEmpty()) {
                    tedasKirilim = extractPattern(qrCodeData, TEDASKIRILIM_PATTERN);
                }
                marka = extractPattern(qrCodeData, MARKA_PATTERN);
                malzeme = extractPattern(qrCodeData, MALZEME_PATTERN);
                tipi = extractPattern(qrCodeData, TIPI_PATTERN);
                imalYili = extractPattern(qrCodeData, IMALYILI_PATTERN);
                barcode = extractBarcode(qrCodeData);
            } else {
                barcode = "";
            }

            // Check database for item info
            String materialName = "";
            try (Connection conn = databaseHelper.getAnatoliaSoftConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT CODE, DESCRIPTION, GROUPCODE FROM AST_ITEMS WHERE CODE = ?")) {

                stmt.setString(1, barcode);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String dbTipi = rs.getString("DESCRIPTION");
                        String dbMalzeme = rs.getString("GROUPCODE");
                        materialName = dbMalzeme + " " + dbTipi;
                    } else {
                        showToast("Ürün veritabanında bulunamadı");
                        return;
                    }
                }
            } catch (SQLException e) {
                Log.e(TAG, "Database error while checking item", e);
                showToast("Veritabanı hatası");
                return;
            }

            if (materialName.trim().isEmpty()) {
                showToast("Malzeme bilgisi okunamadı");
                return;
            }

            lastScanTime = currentTime;
            String finalKareKodNo = kareKodNo;
            String finalMaterialName = materialName;

            executorService.submit(() -> {
                try {
                    // Check if already scanned
                    if (isAlreadyScanned(finalKareKodNo)) {
                        showToast("Bu ürün zaten tarandı");
                        return;
                    }

                    // Add to scanned items
                    scannedKareKodNos.add(finalKareKodNo);
                    materialCounts.merge(finalMaterialName, 1, Integer::sum);

                    ScannedItem newItem = new ScannedItem(
                            qrCodeData, // Store the full QR code data
                            finalMaterialName,
                            new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date())
                    );
                    scannedItems.add(newItem);

                    runOnUiThread(() -> {
                        updateTable();
                        updateScanStatus();
                        showToast("Ürün Okutuldu - Barkod: " + barcode);
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Error processing QR code", e);
                    showToast("İşlem sırasında hata oluştu");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "QR Code processing error", e);
            showToast("Karekod okunurken hata oluştu");
        }
    }

    private void saveToProductionScanned() throws SQLException {
        try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {
            String insertItemsQuery = "INSERT INTO AST_PRODUCTION_ITEMS " +
                    "(KAREKODNO, TEDASKIRILIM, MARKA, MALZEME, TIPI, IMALYILI, BARKOD, CREATE_DATE) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            String insertScannedQuery = "INSERT INTO AST_PRODUCTION_SCANNED " +
                    "(KAREKODNO, MATERIAL_NAME, SCAN_DATE) " +
                    "VALUES (?, ?, ?)";

            conn.setAutoCommit(false);
            try {
                // Insert into AST_PRODUCTION_ITEMS
                try (PreparedStatement itemsStmt = conn.prepareStatement(insertItemsQuery)) {
                    for (ScannedItem item : scannedItems) {
                        try {
                            String qrCode = item.kareKodNo;

                            // First try to get KAREKODNO directly
                            String kareKodNo = extractPattern(qrCode, KAREKODNO_PATTERN);

                            // If KAREKODNO is empty, try to get it from TEDASKIRILIM
                            if (kareKodNo.isEmpty()) {
                                String tedasKirilimValue = extractPattern(qrCode, TEDASKIRILIM_PATTERN);
                                if (tedasKirilimValue.contains("ENT")) {
                                    kareKodNo = tedasKirilimValue; // Use the full value including ENT
                                }
                            }

                            // Extract TEDAS part (before ENT) for TEDASKIRILIM field
                            String tedasKirilimValue = extractPattern(qrCode, TEDASKIRILIM_PATTERN);
                            String actualTedasKirilim = tedasKirilimValue;
                            if (tedasKirilimValue.contains("ENT")) {
                                actualTedasKirilim = tedasKirilimValue.split("ENT")[0];
                            }

                            itemsStmt.setString(1, kareKodNo);  // Full KAREKODNO including ENT part
                            itemsStmt.setString(2, actualTedasKirilim);  // Only the TEDAS part
                            itemsStmt.setString(3, extractPattern(qrCode, MARKA_PATTERN));
                            itemsStmt.setString(4, extractPattern(qrCode, MALZEME_PATTERN));
                            itemsStmt.setString(5, extractPattern(qrCode, TIPI_PATTERN));
                            itemsStmt.setString(6, extractPattern(qrCode, IMALYILI_PATTERN));
                            itemsStmt.setString(7, extractBarcode(qrCode));

                            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                            java.util.Date parsedDate = dateFormat.parse(item.timestamp);
                            java.sql.Timestamp sqlTimestamp = new java.sql.Timestamp(parsedDate.getTime());
                            itemsStmt.setTimestamp(8, sqlTimestamp);

                            itemsStmt.addBatch();
                        } catch (Exception e) {
                            conn.rollback();
                            String debugInfo = getDebugInfo(item, e, itemsStmt);
                            showDebugDialog("Veri Kaydetme Hatası", debugInfo);
                            throw new SQLException("AST_PRODUCTION_ITEMS tablosuna veri eklenirken hata oluştu", e);
                        }
                    }

                    int[] itemResults = itemsStmt.executeBatch();
                    for (int i = 0; i < itemResults.length; i++) {
                        if (itemResults[i] <= 0) {
                            String debugInfo = getDebugInfo(scannedItems.get(i),
                                    new Exception("Batch insert failed for item " + (i + 1)), itemsStmt);
                            showDebugDialog("Batch İşlem Hatası", debugInfo);
                            throw new SQLException("Batch insert failed for item " + (i + 1));
                        }
                    }
                }

                // Insert into AST_PRODUCTION_SCANNED
                try (PreparedStatement scannedStmt = conn.prepareStatement(insertScannedQuery)) {
                    for (ScannedItem item : scannedItems) {
                        try {
                            String qrCode = item.kareKodNo;

                            // First try to get KAREKODNO directly
                            String kareKodNo = extractPattern(qrCode, KAREKODNO_PATTERN);

                            // If empty, try to extract from TEDASKIRILIM
                            if (kareKodNo.isEmpty()) {
                                String tedasKirilimValue = extractPattern(qrCode, TEDASKIRILIM_PATTERN);
                                if (tedasKirilimValue.contains("ENT")) {
                                    kareKodNo = tedasKirilimValue; // Use full value including ENT part
                                }
                            }

                            scannedStmt.setString(1, kareKodNo);  // Full KAREKODNO including ENT part
                            scannedStmt.setString(2, item.materialName);

                            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                            java.util.Date parsedDate = dateFormat.parse(item.timestamp);
                            java.sql.Timestamp sqlTimestamp = new java.sql.Timestamp(parsedDate.getTime());
                            scannedStmt.setTimestamp(3, sqlTimestamp);

                            scannedStmt.addBatch();
                        } catch (Exception e) {
                            conn.rollback();
                            String debugInfo = getDebugInfo(item, e, scannedStmt);
                            showDebugDialog("Veri Kaydetme Hatası", debugInfo);
                            throw new SQLException("AST_PRODUCTION_SCANNED tablosuna veri eklenirken hata oluştu", e);
                        }
                    }

                    int[] scannedResults = scannedStmt.executeBatch();
                    for (int i = 0; i < scannedResults.length; i++) {
                        if (scannedResults[i] <= 0) {
                            String debugInfo = getDebugInfo(scannedItems.get(i),
                                    new Exception("Batch insert failed for scanned item " + (i + 1)), scannedStmt);
                            showDebugDialog("Batch İşlem Hatası", debugInfo);
                            throw new SQLException("Batch insert failed for scanned item " + (i + 1));
                        }
                    }
                }

                Log.d("SQL_DEBUG", "All operations successful, committing transaction");
                conn.commit();
            } catch (SQLException e) {
                Log.e("SQL_DEBUG", "Error during database operations: " + e.getMessage(), e);
                conn.rollback();
                String debugInfo = getDebugInfo(scannedItems.get(0), e, null);
                showDebugDialog("Veritabanı Hatası", debugInfo);
                throw new SQLException("Veriler kaydedilirken hata oluştu", e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    private String extractKareKodNoForItems(String qrCode) {
        // For AST_PRODUCTION_ITEMS table - only get the number after ENT
        String fullKareKodNo = extractPattern(qrCode, KAREKODNO_PATTERN);
        if (fullKareKodNo.contains("ENT")) {
            String[] parts = fullKareKodNo.split("ENT");
            return parts.length > 1 ? parts[1] : "";
        }
        return fullKareKodNo;
    }

    private String extractPattern(String qrCode, Pattern pattern) {
        if (qrCode == null || qrCode.isEmpty()) {
            return "";
        }

        java.util.regex.Matcher matcher = pattern.matcher(qrCode);
        if (matcher.find()) {
            String extracted = matcher.group(1);
            Log.d(TAG, "Pattern " + pattern.pattern() + " extracted: " + extracted); // Debug log
            return extracted.trim();
        }
        Log.d(TAG, "Pattern " + pattern.pattern() + " not found in: " + qrCode); // Debug log for failed matches
        return "";
    }
    private String extractBarcode(String qrCode) {
        if (qrCode == null || qrCode.isEmpty()) {
            return "";
        }
        String[] parts = qrCode.split("\\|\\|");
        return parts.length > 1 ? parts[parts.length - 1].trim() : "";
    }

    private EditText createEditText(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        return editText;
    }

    private View createSpacingView() {
        View spacing = new View(this);
        spacing.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30));
        return spacing;
    }

    private String extractValue(String part, String prefix) {
        if (part == null || part.isEmpty() || !part.startsWith(prefix)) {
            return "";
        }
        return part.substring(prefix.length());
    }

    private boolean isAlreadyScanned(String kareKodNo) {
        // First check if it's already in our current session
        if (scannedKareKodNos.contains(kareKodNo)) {
            return true;
        }

        // Then check the database
        try (Connection conn = databaseHelper.getAnatoliaSoftConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM AST_PRODUCTION_SCANNED WHERE KAREKODNO = ?")) {

            stmt.setString(1, kareKodNo);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error checking if item is already scanned", e);
            showToast("Duplicate check error: " + e.getMessage());
            return true; // Err on the side of caution
        }
    }
    private void loadInitialData() {
        executorService.submit(() -> {
            try {
                // No need to pre-populate with empty placeholders
                // Just ensure table is ready for data
                runOnUiThread(() -> {
                    updateTable();
                    updateScanStatus();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading initial data", e);
                runOnUiThread(() -> showToast("Veri yükleme hatası"));
            }
        });
    }

    private void updateScanStatus() {
        runOnUiThread(() -> {
            // Update scanning status
            String status = String.format("Toplam Okutulan: %d", scannedKareKodNos.size());
            scanStatusTextView.setText(status);

            // Enable/disable buttons based on scanned items
            boolean hasScannedItems = !scannedKareKodNos.isEmpty();
            saveButton.setEnabled(hasScannedItems);
            confirmButton.setEnabled(hasScannedItems);
        });
    }
    private void updateTable() {
        // Clear existing rows except header
        int childCount = tableLayout.getChildCount();
        if (childCount > 1) {
            tableLayout.removeViews(1, childCount - 1);
        }

        // Only add rows for materials that have been scanned
        for (Map.Entry<String, Integer> entry : materialCounts.entrySet()) {
            if (entry.getValue() > 0) { // Only show items with count > 0
                TableRow row = new TableRow(this);

                TextView materialView = createDataTextView(entry.getKey());
                TextView countView = createDataTextView(String.valueOf(entry.getValue()));

                // Center align the count
                countView.setGravity(Gravity.CENTER);

                row.addView(materialView);
                row.addView(countView);
                tableLayout.addView(row);
            }
        }
    }
    private TextView createDataTextView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(16, 12, 16, 12);
        tv.setBackgroundColor(getResources().getColor(android.R.color.white));

        // Add border and make text larger
        GradientDrawable border = new GradientDrawable();
        border.setColor(getResources().getColor(android.R.color.white));
        border.setStroke(1, getResources().getColor(android.R.color.darker_gray));
        tv.setBackground(border);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        return tv;
    }

    private void showToast(String message) {
        runOnUiThread(() -> {
            if (currentToast != null) {
                currentToast.cancel();
            }
            currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
            currentToast.show();
        });
    }

    private void showDebugDialog(String title, String message) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Kopyala", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Hata Detayları", message);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, "Kopyalandı", Toast.LENGTH_SHORT).show();
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }


    private String buildQueryWithValues(PreparedStatement stmt) {
        try {
            if (stmt == null) return "NULL STATEMENT";

            String query = stmt.toString();
            // Most JDBC drivers include the actual query in toString()
            // If not, we'll at least get the prepared statement object info
            return "SQL Query: " + query;
        } catch (Exception e) {
            return "Error getting query: " + e.getMessage();
        }
    }

    private String getDebugInfo(ScannedItem item, Exception e, PreparedStatement stmt) {
        StringBuilder debug = new StringBuilder();
        debug.append("Hata Zamanı: ").append(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date())).append("\n\n");

        // Log the SQL Query
        debug.append("SQL Sorgusu:\n").append(buildQueryWithValues(stmt)).append("\n\n");

        debug.append("QR Kod: ").append(item.kareKodNo).append("\n\n");
        debug.append("Çıkarılan Değerler:\n");
        debug.append("KAREKODNO: ").append(extractPattern(item.kareKodNo, KAREKODNO_PATTERN)).append("\n");
        debug.append("TEDASKIRILIM: ").append(extractPattern(item.kareKodNo, TEDASKIRILIM_PATTERN)).append("\n");
        debug.append("MARKA: ").append(extractPattern(item.kareKodNo, MARKA_PATTERN)).append("\n");
        debug.append("MALZEME: ").append(extractPattern(item.kareKodNo, MALZEME_PATTERN)).append("\n");
        debug.append("TIPI: ").append(extractPattern(item.kareKodNo, TIPI_PATTERN)).append("\n");
        debug.append("IMALYILI: ").append(extractPattern(item.kareKodNo, IMALYILI_PATTERN)).append("\n");
        debug.append("BARKOD: ").append(extractBarcode(item.kareKodNo)).append("\n");
        debug.append("TIMESTAMP: ").append(item.timestamp).append("\n\n");
        debug.append("Hata Mesajı: ").append(e.getMessage()).append("\n");
        if (e.getCause() != null) {
            debug.append("Hata Nedeni: ").append(e.getCause().getMessage()).append("\n");
        }
        return debug.toString();
    }


    // Camera and permission handling methods remain the same
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