package com.example.A_Soft;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.gson.Gson;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class ProductionScanActivity extends AppCompatActivity {
    private static final String TAG = ProductionScanActivity.class.getSimpleName();
    private static final Map<String, Pattern> PATTERNS = new HashMap<>();
    private static final long SCAN_DEBOUNCE_INTERVAL = 2000;
    private static final String PREF_NAME = "ProductionDrafts";
    private static final String KEY_DRAFT_DATA = "draft_data";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());

    static {
        PATTERNS.put("KAREKODNO", Pattern.compile("KAREKODNO_([^|]+)"));
        PATTERNS.put("TEDASKIRILIM", Pattern.compile("TEDASKIRILIM_([^|]+)"));
        PATTERNS.put("MARKA", Pattern.compile("MARKA_([^|]+)"));
        PATTERNS.put("MALZEME", Pattern.compile("MALZEME_([^|]+)"));
        PATTERNS.put("TIPI", Pattern.compile("TIPI_([^|]+)"));
        PATTERNS.put("IMALYILI", Pattern.compile("IMALYILI_(\\d+)"));
    }

    private final Set<String> scannedKareKodNos = new HashSet<>();
    private final Map<String, Integer> materialCounts = new HashMap<>();
    private final List<ScannedItem> scannedItems = new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private SharedPreferences sharedPreferences;
    private DatabaseHelper databaseHelper;
    private TableLayout tableLayout;
    private CameraSourcePreview cameraPreview;
    private TextView scanStatusTextView;
    private Button saveButton, confirmButton, manualQrButton, scannedItemsButton;
    private String currentReceiptNo;
    private long lastScanTime = 0;
    private Toast currentToast;
    private String lastScannedQR = "";
    private final boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_production_scan);

        initializeBasicComponents();
        loadDraftData();
        requestCameraPermissionIfNeeded();
    }

    private void initializeBasicComponents() {
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        currentReceiptNo = getIntent().getStringExtra("RECEIPT_NO");
        databaseHelper = new DatabaseHelper(this);

        tableLayout = findViewById(R.id.tableLayout);
        cameraPreview = findViewById(R.id.camera_preview);
        scanStatusTextView = findViewById(R.id.scan_status);

        initializeButtons();
        styleTableLayout();
        loadInitialData();
    }

    private void initializeButtons() {
        saveButton = findViewById(R.id.saveButton);
        confirmButton = findViewById(R.id.confirmButton);
        manualQrButton = findViewById(R.id.manual_qr_button);
        scannedItemsButton = findViewById(R.id.scanned_items_button);

        saveButton.setOnClickListener(v -> saveDraft());
        confirmButton.setOnClickListener(v -> confirmProduction());
        manualQrButton.setOnClickListener(v -> showManualQRInputDialog());
        scannedItemsButton.setOnClickListener(v -> showScannedItemsList());
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
            public void release() {
            }

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

    private void processQRCode(String qrCodeData) {
        if (qrCodeData == null || qrCodeData.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime < SCAN_DEBOUNCE_INTERVAL) {
            return;
        }

        // Prevent processing if it's the same QR code within debounce interval
        if (qrCodeData.equals(lastScannedQR) &&
                (System.currentTimeMillis() - lastScanTime) < SCAN_DEBOUNCE_INTERVAL) {
            return;
        }

        lastScannedQR = qrCodeData;
        lastScanTime = currentTime;

        executorService.submit(() -> {
            try {
                QRCodeData parsedData = parseQRCode(qrCodeData);
                if (parsedData == null) {
                    runOnUiThread(() -> showToast("QR kod formatı geçersiz"));
                    return;
                }

                // Extract TEDAS code from KAREKODNO
                String kareKodNoTedas = "";
                if (parsedData.kareKodNo.contains("ENT")) {
                    kareKodNoTedas = parsedData.kareKodNo.split("ENT")[0];
                }

                // Validate both TEDAS codes match with barcode
                if (!validateTedasCode(kareKodNoTedas, parsedData.tedasKirilim, parsedData.barcode)) {
                    runOnUiThread(() -> showToast("TEDAŞ kodu ve barkod eşleşmesi bulunamadı"));
                    return;
                }

                String itemInfo = getItemInfoFromDatabase(parsedData.tedasKirilim, parsedData.barcode);
                if (itemInfo == null) {
                    runOnUiThread(() -> showToast("Ürün veya TEDAŞ eşleşmesi bulunamadı"));
                    return;
                }

                processScannedItem(parsedData, itemInfo, "KAMERA");

            } catch (Exception e) {
                Log.e(TAG, "QR Code processing error", e);
                runOnUiThread(() -> showToast("Karekod okunurken hata oluştu"));
            }
        });
    }

    private void processScannedItem(QRCodeData parsedData, String itemInfo, String entryMethod) {
        executorService.submit(() -> {
            try {
                if (isAlreadyScanned(parsedData.kareKodNo)) {
                    showToast("Bu ürün zaten tarandı");
                    return;
                }

                String[] itemParts = itemInfo.split("\\|");
                String materialName = itemParts[0] + " " + itemParts[1];

                scannedKareKodNos.add(parsedData.kareKodNo);
                materialCounts.merge(materialName, 1, Integer::sum);

                ScannedItem newItem = new ScannedItem(
                        parsedData.rawData,
                        materialName,
                        DATE_FORMAT.format(new Date()),
                        entryMethod
                );
                scannedItems.add(newItem);

                runOnUiThread(() -> {
                    updateTable();
                    updateScanStatus();
                    showToast("Ürün Okutuldu - Barkod: " + parsedData.barcode);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error processing scanned item", e);
                showToast("İşlem sırasında hata oluştu");
            }
        });
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

    private QRCodeData parseQRCode(String qrCodeData) {
        try {
            // Check for delimiter
            if (!qrCodeData.contains("||")) {
                return null;
            }

            // Extract KAREKODNO
            String kareKodNo = extractPattern(qrCodeData, "KAREKODNO");
            if (kareKodNo.isEmpty()) {
                return null;
            }

            // Extract TEDASKIRILIM
            String tedasKirilim = extractPattern(qrCodeData, "TEDASKIRILIM");
            if (tedasKirilim.isEmpty()) {
                return null;
            }

            // Extract barcode (everything after the last ||)
            String barcode = extractBarcode(qrCodeData);
            if (barcode.isEmpty()) {
                return null;
            }

            Log.d(TAG, "Parsed QR: KAREKODNO='" + kareKodNo +
                    "', TEDASKIRILIM='" + tedasKirilim +
                    "', Barcode='" + barcode + "'");

            return new QRCodeData(qrCodeData, kareKodNo, tedasKirilim, barcode);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing QR code", e);
            return null;
        }
    }

    private String extractBarcode(String qrCode) {
        if (qrCode == null || qrCode.isEmpty()) {
            return "";
        }
        String[] parts = qrCode.split("\\|\\|");
        return parts.length > 1 ? parts[parts.length - 1].trim() : "";
    }

    private String extractPattern(String qrCode, String patternKey) {
        if (qrCode == null || qrCode.isEmpty()) {
            return "";
        }

        Pattern pattern = PATTERNS.get(patternKey);
        if (pattern == null) {
            return "";
        }

        var matcher = pattern.matcher(qrCode);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private void saveToProductionScanned() throws SQLException {
        try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {
            conn.setAutoCommit(false);
            try {
                batchInsertProductionItems(conn);
                batchInsertProductionScanned(conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void batchInsertProductionItems(Connection conn) throws SQLException {
        String insertItemsQuery = "INSERT INTO AST_PRODUCTION_ITEMS " +
                "(KAREKODNO, TEDASKIRILIM, MARKA, MALZEME, TIPI, IMALYILI, BARKOD, CREATE_DATE, STATUS) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement itemsStmt = conn.prepareStatement(insertItemsQuery)) {
            for (ScannedItem item : scannedItems) {
                String qrCode = item.kareKodNo;
                String fullKareKodNo = extractPattern(qrCode, "KAREKODNO");

                if (fullKareKodNo.isEmpty()) {
                    fullKareKodNo = extractPattern(qrCode, "TEDASKIRILIM");
                }

                String kareKodNoForItems = extractKareKodNoAfterENT(fullKareKodNo);
                String tedasKirilim = extractTedasKirilimBeforeENT(fullKareKodNo);

                itemsStmt.setString(1, kareKodNoForItems);
                itemsStmt.setString(2, tedasKirilim);
                itemsStmt.setString(3, extractPattern(qrCode, "MARKA"));
                itemsStmt.setString(4, extractPattern(qrCode, "MALZEME"));
                itemsStmt.setString(5, extractPattern(qrCode, "TIPI")); // Use QR code TIPI value
                itemsStmt.setString(6, extractPattern(qrCode, "IMALYILI"));
                itemsStmt.setString(7, extractBarcode(qrCode));
                itemsStmt.setTimestamp(8, convertToSqlTimestamp(item.timestamp));
                itemsStmt.setString(9, item.entryMethod); // Add entry method

                itemsStmt.addBatch();
            }

            executeBatchWithValidation(itemsStmt, "AST_PRODUCTION_ITEMS");
        }
    }

    private void batchInsertProductionScanned(Connection conn) throws SQLException {
        String insertScannedQuery = "INSERT INTO AST_PRODUCTION_SCANNED " +
                "(KAREKODNO, MATERIAL_NAME, SCAN_DATE) VALUES (?, ?, ?)";

        try (PreparedStatement scannedStmt = conn.prepareStatement(insertScannedQuery)) {
            for (ScannedItem item : scannedItems) {
                String fullKareKodNo = extractPattern(item.kareKodNo, "KAREKODNO");
                if (fullKareKodNo.isEmpty()) {
                    fullKareKodNo = extractPattern(item.kareKodNo, "TEDASKIRILIM");
                }

                scannedStmt.setString(1, fullKareKodNo);
                scannedStmt.setString(2, item.materialName);
                scannedStmt.setTimestamp(3, convertToSqlTimestamp(item.timestamp));

                scannedStmt.addBatch();
            }

            executeBatchWithValidation(scannedStmt, "AST_PRODUCTION_SCANNED");
        }
    }

    private void executeBatchWithValidation(PreparedStatement stmt, String tableName) throws SQLException {
        int[] results = stmt.executeBatch();
        for (int i = 0; i < results.length; i++) {
            if (results[i] <= 0) {
                String debugInfo = getDebugInfo(scannedItems.get(i),
                        new Exception("Batch insert failed for " + tableName + " item " + (i + 1)), stmt);
                showDebugDialog("Batch İşlem Hatası", debugInfo);
                throw new SQLException("Batch insert failed for " + tableName + " item " + (i + 1));
            }
        }
    }

    private String getMaterialNameFromDatabase(String barcode) {
        try (Connection conn = databaseHelper.getAnatoliaSoftConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT DESCRIPTION, GROUPCODE FROM AST_ITEMS WHERE CODE = ?")) {

            stmt.setString(1, barcode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String dbTipi = rs.getString("DESCRIPTION");
                    String dbMalzeme = rs.getString("GROUPCODE");
                    return dbMalzeme + " " + dbTipi;
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Database error while checking item", e);
        }
        return "";
    }

    private void showManualQRInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Manuel Ürün Girişi");

        LinearLayout layout = createManualInputLayout();
        builder.setView(layout);
        builder.setPositiveButton("Onayla", null);
        builder.setNegativeButton("İptal", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        setupManualInputDialog(dialog, layout);
        dialog.show();
    }

    private LinearLayout createManualInputLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        EditText tedasKirilimInput = createEditText("Seri No (örn: 561007ENT22000401)");
        EditText barcodeInput = createEditText("Barkod");
        EditText imalYiliInput = createEditText("Yıl");

        layout.addView(tedasKirilimInput);
        layout.addView(createSpacingView());
        layout.addView(barcodeInput);
        layout.addView(createSpacingView());
        layout.addView(imalYiliInput);

        return layout;
    }

    private void setupManualInputDialog(AlertDialog dialog, LinearLayout layout) {
        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                EditText tedasKirilimInput = (EditText) layout.getChildAt(0);
                EditText barcodeInput = (EditText) layout.getChildAt(2);
                EditText imalYiliInput = (EditText) layout.getChildAt(4);

                handleManualInput(
                        tedasKirilimInput.getText().toString().trim(),
                        barcodeInput.getText().toString().trim(),
                        imalYiliInput.getText().toString().trim(),
                        dialog
                );
            });
        });
    }

    private void handleManualInput(String fullKareKodNo, String barcode, String imalYili, AlertDialog dialog) {
        if (fullKareKodNo.isEmpty() || barcode.isEmpty() || imalYili.isEmpty()) {
            showToast("Lütfen tüm alanları doldurun");
            return;
        }

        if (!fullKareKodNo.contains("ENT")) {
            showToast("Seri No 'ENT' içermelidir (örn: 561007ENT22000401)");
            return;
        }

        // Extract TEDAS part for validation
        String tedasPart = fullKareKodNo.split("ENT")[0];

        executorService.submit(() -> {
            try {
                if (!validateTedasCode(tedasPart, tedasPart, barcode)) {
                    runOnUiThread(() -> showToast("TEDAŞ kodu ve barkod eşleşmesi bulunamadı"));
                    return;
                }

                runOnUiThread(() -> processManualInput(fullKareKodNo, barcode, imalYili, dialog));
            } catch (Exception e) {
                Log.e(TAG, "Error in manual input validation", e);
                runOnUiThread(() -> showToast("Doğrulama sırasında hata oluştu"));
            }
        });
    }


    private void processManualInput(String fullKareKodNo, String barcode, String imalYili, AlertDialog dialog) {
        executorService.submit(() -> {
            try {
                Log.d(TAG, "Processing manual input: " + fullKareKodNo);
                String itemInfo = getItemInfoFromDatabase(fullKareKodNo, barcode);
                if (itemInfo == null) {
                    runOnUiThread(() -> showToast("Ürün veya TEDAŞ eşleşmesi bulunamadı"));
                    return;
                }

                String[] itemParts = itemInfo.split("\\|");
                if (itemParts.length != 3) {
                    runOnUiThread(() -> showToast("Veritabanı hatası"));
                    return;
                }

                String malzeme = itemParts[0];
                String tipi = itemParts[1];
                String tedasPart = fullKareKodNo.split("ENT")[0];  // Extract TEDAS part

                String formattedQR = formatManualQRCode(fullKareKodNo, tedasPart, malzeme, tipi, imalYili, barcode);

                runOnUiThread(() -> {
                    dialog.dismiss();
                    processScannedItem(new QRCodeData(formattedQR, fullKareKodNo, tedasPart, barcode),
                            itemInfo, "MANUAL");
                });

            } catch (Exception e) {
                Log.e(TAG, "Error processing manual entry", e);
                runOnUiThread(() -> showToast("İşlem sırasında hata oluştu: " + e.getMessage()));
            }
        });
    }

    private String getItemInfoFromDatabase(String tedasKirilim, String barcode) {
        String tedasCode = tedasKirilim;
        if (tedasKirilim.contains("ENT")) {
            tedasCode = tedasKirilim.split("ENT")[0];
        }

        Log.d(TAG, "GetItemInfo: TEDAS code = '" + tedasCode + "', barcode = '" + barcode + "'");

        try (Connection conn = databaseHelper.getAnatoliaSoftConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT i.DESCRIPTION, i.GROUPCODE, t.ITM_TEDAS " +
                             "FROM AST_ITEMS i " +
                             "JOIN AST_TEMS_TEDAS t ON i.CODE = t.CODE " +
                             "WHERE i.CODE = ? AND t.ITM_TEDAS = ?")) {

            stmt.setString(1, barcode.trim());
            stmt.setString(2, tedasCode.trim());

            Log.d(TAG, "GetItemInfo Query: " + stmt.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String dbMalzeme = rs.getString("GROUPCODE");
                    String dbTipi = rs.getString("DESCRIPTION");
                    String dbTedasKirilim = rs.getString("ITM_TEDAS");

                    Log.d(TAG, String.format("GetItemInfo Results: malzeme='%s', tipi='%s', tedas='%s'",
                            dbMalzeme, dbTipi, dbTedasKirilim));

                    return String.format("%s|%s|%s", dbMalzeme, dbTipi, dbTedasKirilim);
                } else {
                    Log.d(TAG, "GetItemInfo: No results found");
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Database error while checking item", e);
            Log.e(TAG, "SQL Error: " + e.getMessage());
        }
        return null;
    }
    private boolean validateTedasCode(String kareKodNoTedas, String tedasKirilim, String barcode) {
        Log.d(TAG, "Validating TEDAS: KareKodNo part = '" + kareKodNoTedas +
                "', TedasKirilim = '" + tedasKirilim + "', Barcode = '" + barcode + "'");

        // First check if KAREKODNO TEDAS part matches TEDASKIRILIM
        if (!kareKodNoTedas.equals(tedasKirilim)) {
            Log.d(TAG, "TEDAS codes don't match between KAREKODNO and TEDASKIRILIM");
            return false;
        }

        try (Connection conn = databaseHelper.getAnatoliaSoftConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM AST_TEMS_TEDAS WHERE CODE = ? AND ITM_TEDAS = ?")) {

            stmt.setString(1, barcode.trim());
            stmt.setString(2, tedasKirilim.trim());

            Log.d(TAG, "TEDAS Validation Query: " + stmt.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                int count = rs.getInt(1);
                Log.d(TAG, "TEDAS Validation Result: count = " + count);
                return count > 0;
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error validating TEDAS code", e);
            return false;
        }
    }

    private String formatManualQRCode(String fullKareKodNo, String tedasPart, String malzeme,
                                      String tipi, String imalYili, String barcode) {
        // Extract TEDAS code (part before ENT) for TEDASKIRILIM
        String tedasCode = fullKareKodNo.split("ENT")[0];

        return String.format("KAREKODNO_%s|TEDASKIRILIM_%s|MARKA_%s|MALZEME_%s|TIPI_%s|IMALYILI_%s||%s",
                fullKareKodNo,     // Keep full number for KAREKODNO
                tedasCode,         // Use only TEDAS part for TEDASKIRILIM
                "ENT",
                malzeme,
                tipi,
                imalYili,
                barcode);
    }

    private void showScannedItemsList() {
        if (scannedItems.isEmpty()) {
            showToast("Henüz okutulan ürün bulunmamaktadır");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Okutulan Ürünler");

        LinearLayout layout = createScannedItemsLayout();
        builder.setView(layout);
        builder.setPositiveButton("Kapat", null);
        builder.show();
    }

    private LinearLayout createScannedItemsLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        for (ScannedItem item : scannedItems) {
            addScannedItemView(layout, item);
            addSeparator(layout);
        }

        return layout;
    }

    private void addScannedItemView(LinearLayout layout, ScannedItem item) {
        TextView itemView = new TextView(this);
        String itemText = formatScannedItemText(item.kareKodNo, item.timestamp);
        itemView.setText(itemText);
        itemView.setPadding(0, 10, 0, 10);
        layout.addView(itemView);
    }

    private String formatScannedItemText(String qrCode, String timestamp) {
        return String.format(
                "KAREKODNO: %s\nTEDAŞ Kırılım: %s\nMarka: %s\nMalzeme: %s\nTip: %s\nİmal Yılı: %s\nBarkod: %s\nTarih: %s\n",
                extractPattern(qrCode, "KAREKODNO"),
                extractPattern(qrCode, "TEDASKIRILIM"),
                extractPattern(qrCode, "MARKA"),
                extractPattern(qrCode, "MALZEME"),
                extractPattern(qrCode, "TIPI"),
                extractPattern(qrCode, "IMALYILI"),
                extractBarcode(qrCode),
                timestamp
        );
    }

    private void addSeparator(LinearLayout layout) {
        View separator = new View(this);
        separator.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        separator.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        layout.addView(separator);
    }

    private void saveDraft() {
        if (scannedItems.isEmpty()) {
            showToast("Kaydedilecek ürün bulunmamaktadır");
            return;
        }

        DraftData draftData = new DraftData(
                new HashSet<>(scannedKareKodNos),
                new HashMap<>(materialCounts),
                new ArrayList<>(scannedItems)
        );

        SharedPreferences.Editor editor = sharedPreferences.edit();
        String json = new Gson().toJson(draftData);
        editor.putString(KEY_DRAFT_DATA + "_" + currentReceiptNo, json);
        editor.apply();

        showToast("Taslak kaydedildi");
        finish();
    }

    private void loadDraftData() {
        String json = sharedPreferences.getString(KEY_DRAFT_DATA + "_" + currentReceiptNo, null);
        if (json != null) {
            try {
                DraftData draftData = new Gson().fromJson(json, DraftData.class);
                restoreDraftData(draftData);
            } catch (Exception e) {
                Log.e(TAG, "Error loading draft data", e);
                showToast("Taslak yükleme hatası");
            }
        }
    }

    private void restoreDraftData(DraftData draftData) {
        scannedKareKodNos.clear();
        scannedKareKodNos.addAll(draftData.scannedKareKodNos);

        materialCounts.clear();
        materialCounts.putAll(draftData.materialCounts);

        scannedItems.clear();
        scannedItems.addAll(draftData.scannedItems);

        updateTable();
        updateScanStatus();
    }

    private void clearDraft() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_DRAFT_DATA + "_" + currentReceiptNo);
        editor.apply();
    }



    private String extractKareKodNoAfterENT(String fullKareKodNo) {
        if (fullKareKodNo.contains("ENT")) {
            String[] parts = fullKareKodNo.split("ENT");
            return parts.length > 1 ? parts[1].trim() : "";
        }
        return fullKareKodNo;
    }

    private String extractTedasKirilimBeforeENT(String value) {
        if (value.contains("ENT")) {
            return value.split("ENT")[0];
        }
        return value;
    }

    private Timestamp convertToSqlTimestamp(String dateStr) throws SQLException {
        try {
            Date parsedDate = DATE_FORMAT.parse(dateStr);
            return new Timestamp(parsedDate.getTime());
        } catch (Exception e) {
            throw new SQLException("Error converting date: " + dateStr, e);
        }
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

    private EditText createEditText(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        editText.setLayoutParams(params);
        return editText;
    }

    private View createSpacingView() {
        View spacing = new View(this);
        spacing.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30));
        return spacing;
    }

    private void styleTableLayout() {
        tableLayout.setBackgroundColor(getResources().getColor(android.R.color.white));
        tableLayout.setPadding(2, 2, 2, 2);

        TableRow headerRow = new TableRow(this);
        headerRow.addView(createHeaderTextView("Malzeme"));
        headerRow.addView(createHeaderTextView("Miktar"));
        tableLayout.addView(headerRow);
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

    private TextView createDataTextView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(16, 12, 16, 12);
        tv.setBackgroundColor(getResources().getColor(android.R.color.white));

        GradientDrawable border = new GradientDrawable();
        border.setColor(getResources().getColor(android.R.color.white));
        border.setStroke(1, getResources().getColor(android.R.color.darker_gray));
        tv.setBackground(border);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        return tv;
    }

    private void updateTable() {
        runOnUiThread(() -> {
            int childCount = tableLayout.getChildCount();
            if (childCount > 1) {
                tableLayout.removeViews(1, childCount - 1);
            }

            materialCounts.forEach((material, count) -> {
                if (count > 0) {
                    TableRow row = new TableRow(this);
                    TextView materialView = createDataTextView(material);
                    TextView countView = createDataTextView(String.valueOf(count));
                    countView.setGravity(Gravity.CENTER);

                    row.addView(materialView);
                    row.addView(countView);
                    tableLayout.addView(row);
                }
            });
        });
    }

    private void updateScanStatus() {
        runOnUiThread(() -> {
            String status = String.format("Toplam Okutulan: %d", scannedKareKodNos.size());
            scanStatusTextView.setText(status);

            boolean hasScannedItems = !scannedKareKodNos.isEmpty();
            saveButton.setEnabled(hasScannedItems);
            confirmButton.setEnabled(hasScannedItems);
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
            builder.create().show();
        });
    }

    private String getDebugInfo(ScannedItem item, Exception e, PreparedStatement stmt) {
        StringBuilder debug = new StringBuilder()
                .append("Hata Zamanı: ").append(DATE_FORMAT.format(new Date())).append("\n\n")
                .append("SQL Sorgusu:\n").append(buildQueryWithValues(stmt)).append("\n\n")
                .append("QR Kod: ").append(item.kareKodNo).append("\n\n")
                .append("Çıkarılan Değerler:\n");

        for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
            debug.append(entry.getKey()).append(": ")
                    .append(extractPattern(item.kareKodNo, entry.getKey()))
                    .append("\n");
        }

        debug.append("BARKOD: ").append(extractBarcode(item.kareKodNo)).append("\n")
                .append("TIMESTAMP: ").append(item.timestamp).append("\n\n")
                .append("Hata Mesajı: ").append(e.getMessage()).append("\n");

        if (e.getCause() != null) {
            debug.append("Hata Nedeni: ").append(e.getCause().getMessage()).append("\n");
        }

        return debug.toString();
    }

    private String buildQueryWithValues(PreparedStatement stmt) {
        try {
            return stmt != null ? stmt.toString() : "NULL STATEMENT";
        } catch (Exception e) {
            return "Error getting query: " + e.getMessage();
        }
    }

    // Lifecycle methods
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
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private static class QRCodeData {
        final String rawData;
        final String kareKodNo;
        final String tedasKirilim;
        final String barcode;

        QRCodeData(String rawData, String kareKodNo, String tedasKirilim, String barcode) {
            this.rawData = rawData;
            this.kareKodNo = kareKodNo;
            this.tedasKirilim = tedasKirilim;
            this.barcode = barcode;
        }
    }

    // Static inner classes
// Update ScannedItem class to include entry method
    private static class ScannedItem {
        final String kareKodNo;
        final String materialName;
        final String timestamp;
        final String entryMethod; // New field

        ScannedItem(String kareKodNo, String materialName, String timestamp, String entryMethod) {
            this.kareKodNo = kareKodNo;
            this.materialName = materialName;
            this.timestamp = timestamp;
            this.entryMethod = entryMethod;
        }
    }

    private static class DraftData {
        final Set<String> scannedKareKodNos;
        final Map<String, Integer> materialCounts;
        final List<ScannedItem> scannedItems;

        DraftData(Set<String> scannedKareKodNos,
                  Map<String, Integer> materialCounts,
                  List<ScannedItem> scannedItems) {
            this.scannedKareKodNos = scannedKareKodNos;
            this.materialCounts = materialCounts;
            this.scannedItems = scannedItems;
        }
    }
}