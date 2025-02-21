package com.example.A_Soft;

import static com.example.A_Soft.LoginActivity.SAVED_USERNAME_KEY;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.Collections;
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
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());

    static {
        PATTERNS.put("KAREKODNO", Pattern.compile("KAREKODNO_([^|]+)"));
        PATTERNS.put("TEDASKIRILIM", Pattern.compile("TEDASKIRILIM_([^|]+)"));
        PATTERNS.put("TCDD", Pattern.compile("TCDD")); // New pattern for TCDD
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
    private ImageButton saveButton, confirmButton, scanButton;
    private String currentReceiptNo;
    private long lastScanTime = 0;
    private Toast currentToast;
    private String lastScannedQR = "";
    private boolean isProcessing = false;
    private ImageButton cameraStateButton;
    private boolean isCameraActive = false;
    private String creationTime;
    private String currentOperator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_production_scan);

        // Get creation time and operator from intent
        creationTime = getIntent().getStringExtra("CREATION_TIME");
        if (creationTime == null) {
            creationTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());
        }
        // Try both SharedPreferences to ensure we get the username
        SharedPreferences loginPrefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
        currentOperator = loginPrefs.getString("logged_in_username", "");

        if (currentOperator.isEmpty()) {
            // Fallback to PREFS_NAME if needed
            SharedPreferences mainPrefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
            currentOperator = mainPrefs.getString(SAVED_USERNAME_KEY, "");
        }

        if (currentOperator.isEmpty()) {
            // Log error and possibly show message to user
            Log.e(TAG, "Could not retrieve logged in username");
            showToast("Kullanıcı bilgisi alınamadı");
        }
        initializeBasicComponents();
        setupNetworkCallback();
        loadDraftData();
        requestCameraPermissionIfNeeded();

        if (cameraPreview != null) {
            cameraPreview.stopCamera();
            cameraPreview.setVisibility(View.GONE); // Hide preview on start
        }
        isCameraActive = false;
        cameraStateButton.setImageResource(R.drawable.camera_off);
    }
    private void initializeBasicComponents() {
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        currentReceiptNo = getIntent().getStringExtra("RECEIPT_NO");
        databaseHelper = new DatabaseHelper(this);
        receiptManager = new ProductionReceiptManager(this); // Initialize receipt manager

        tableLayout = findViewById(R.id.tableLayout);
        cameraPreview = findViewById(R.id.camera_preview);
        scanStatusTextView = findViewById(R.id.scan_status);

        cameraStateButton = findViewById(R.id.camera_state);
        cameraStateButton.setOnClickListener(v -> toggleCamera());

        initializeButtons();
        styleTableLayout();
        loadInitialData();
        showScannerInputDialog();
    }
    private void setupNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> showToast("İnternet bağlantısı kesildi"));
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
    }
    private boolean isNetworkAvailable() {
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(
                connectivityManager.getActiveNetwork());
        return capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    private void initializeButtons() {
        // Initialize buttons with correct types
        scanButton = (ImageButton) findViewById(R.id.scanButton);
        confirmButton = (ImageButton) findViewById(R.id.confirmButton);
        saveButton = (ImageButton) findViewById(R.id.draftButton);
        ImageButton manualQrButton = (ImageButton) findViewById(R.id.manualQrButton);
        ImageButton scannedItemsButton = (ImageButton) findViewById(R.id.scanned_items_button);

        // Set click listeners
        saveButton.setOnClickListener(v -> saveDraft());
        confirmButton.setOnClickListener(v -> confirmProduction());
        scanButton.setOnClickListener(v -> showScannerInputDialog());
        manualQrButton.setOnClickListener(v -> showManualQRInputDialog());
        scannedItemsButton.setOnClickListener(v -> showScannedItemsList());
    }
    private void requestCameraPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    100);
        }
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

    private void setupBarcodeDetection() {
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.QR_CODE)
                .build();

        if (!barcodeDetector.isOperational()) {
            showAlert("Hata", "Barkod okuyucu başlatılamadı", false, null);
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

    private void showAlert(String title, String message, boolean isError, Runnable onDismiss) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Tamam", (dialog, which) -> {
                        if (onDismiss != null) {
                            onDismiss.run();
                        }
                    });

            AlertDialog dialog = builder.create();
            dialog.show();
        });
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
        if (!isNetworkAvailable()) {
            showToast("İnternet bağlantısı yok. Lütfen bağlantınızı kontrol edin.");
            return;
        }

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
                                try {
                                    // Update receipt status before deleting
                                    receiptManager.updateReceiptStatus(currentReceiptNo, "TAMAMLANDI");
                                    // Delete the receipt
                                    receiptManager.deleteReceipt(currentReceiptNo);

                                    // Show success message and any duplicate warnings
                                    if (!duplicateMessages.isEmpty()) {
                                        String finalMessage = String.join("\n", duplicateMessages);
                                        showAlert("Uyarı", finalMessage, false, () -> {
                                            showToast("Üretim onaylandı");
                                            clearDraft();
                                            finish();
                                        });
                                    } else {
                                        showToast("Üretim onaylandı");
                                        clearDraft();
                                        finish();
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error updating/deleting receipt", e);
                                    showToast("Fiş silme hatası: " + e.getMessage());
                                }
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
        if (!isNetworkAvailable()) {
            runOnUiThread(() -> showToast("İnternet bağlantısı yok. Lütfen bağlantınızı kontrol edin."));
            return;
        }

        if (qrCodeData == null || qrCodeData.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime < SCAN_DEBOUNCE_INTERVAL) {
            return;
        }

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

                if (isAlreadyScanned(parsedData.kareKodNo)) {
                    runOnUiThread(() -> showToast("Bu ürün zaten tarandı"));
                    return;
                }

                String kareKodNoTedas = "";
                if (parsedData.kareKodNo.contains("ENT")) {
                    kareKodNoTedas = parsedData.kareKodNo.split("ENT")[0];
                }

                if (!validateTedasCode(kareKodNoTedas, parsedData.tedasKirilim, parsedData.barcode)) {
                    runOnUiThread(() -> showToast("TEDAŞ kodu ve barkod eşleşmesi bulunamadı"));
                    return;
                }

                String itemInfo = getItemInfoFromDatabase(parsedData.tedasKirilim, parsedData.barcode);
                if (itemInfo == null) {
                    runOnUiThread(() -> showToast("Ürün veya TEDAŞ eşleşmesi bulunamadı"));
                    return;
                }

                runOnUiThread(() -> showProcessedItemDialog(parsedData, itemInfo, () ->
                        processScannedItem(parsedData, itemInfo, "KAMERA")
                ));

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
        if (!isNetworkAvailable()) {
            showToast("İnternet bağlantısı yok. Lütfen bağlantınızı kontrol edin.");
            return false;
        }

        String codeAfterENT = "";
        if (kareKodNo.contains("ENT")) {
            codeAfterENT = kareKodNo.substring(kareKodNo.indexOf("ENT") + 3);
        }

        // Check current session
        for (String scanned : scannedKareKodNos) {
            if (scanned.contains("ENT")) {
                String existingCode = scanned.substring(scanned.indexOf("ENT") + 3);
                if (existingCode.equals(codeAfterENT)) {
                    return true;
                }
            }
        }

        return false;
    }

    private QRCodeData parseQRCode(String qrCode) {
        try {
            // Check for delimiter
            if (!qrCode.contains("||")) {
                return null;
            }

            // Extract KAREKODNO
            String kareKodNo = extractPattern(qrCode, "KAREKODNO");
            if (kareKodNo.isEmpty()) {
                return null;
            }

            // Handle both TEDASKIRILIM and TCDD formats
            String tedasKirilim;
            if (qrCode.contains("|TCDD|")) {
                tedasKirilim = "TCDD";
            } else {
                tedasKirilim = extractPattern(qrCode, "TEDASKIRILIM");
                if (tedasKirilim.isEmpty()) {
                    return null;
                }
            }

            // Extract barcode (everything after the last ||)
            String barcode = extractBarcode(qrCode);
            if (barcode.isEmpty()) {
                return null;
            }

            Log.d(TAG, "Parsed QR: KAREKODNO='" + kareKodNo +
                    "', TEDASKIRILIM/TCDD='" + tedasKirilim +
                    "', Barcode='" + barcode + "'");

            return new QRCodeData(qrCode, kareKodNo, tedasKirilim, barcode);
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
                long slipId = insertProductionSlip(conn);
                checkAndInsertProductionItems(conn, slipId);

                Log.d(TAG, "Attempting to commit transaction...");
                conn.commit();
                Log.d(TAG, "Transaction committed successfully");

                // Verify data was inserted
                verifyDataInserted(conn, slipId);

            } catch (SQLException e) {
                Log.e(TAG, "Error occurred, rolling back", e);
                conn.rollback();
                throw e;
            }
        }
    }
    private List<String> duplicateMessages = new ArrayList<>();
    private void checkAndInsertProductionItems(Connection conn, long slipId) throws SQLException {
        String checkQuery = "SELECT KAREKODNO FROM AST_PRODUCTION_ITEMS WHERE KAREKODNO = ?";
        String insertItemsQuery = "INSERT INTO AST_PRODUCTION_ITEMS " +
                "(KAREKODNO, TEDASKIRILIM, MARKA, MALZEME, TIPI, IMALYILI, BARKOD, SLIPID, UNIT, QUANTITY, ITMID, ENTRYTYPE, SLIPTYPEID, SIGN, ADDCODE, CREATE_DATE) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, ?, GETDATE())";

        duplicateMessages.clear();
        // Get all item IDs in one query for better performance
        Map<String, Long> barcodeToItemId = new HashMap<>();
        try (PreparedStatement idStmt = conn.prepareStatement(
                "SELECT ID, CODE FROM AST_ITEMS WHERE CODE IN (" +
                        String.join(",", Collections.nCopies(scannedItems.size(), "?")) + ")")) {

            int paramIndex = 1;
            for (ScannedItem item : scannedItems) {
                idStmt.setString(paramIndex++, extractBarcode(item.kareKodNo));
            }

            try (ResultSet rs = idStmt.executeQuery()) {
                while (rs.next()) {
                    barcodeToItemId.put(rs.getString("CODE"), rs.getLong("ID"));
                }
            }
        }
        try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
             PreparedStatement insertStmt = conn.prepareStatement(insertItemsQuery)) {

            for (ScannedItem item : scannedItems) {
                String qrCode = item.kareKodNo;
                String fullKareKodNo = extractPattern(qrCode, "KAREKODNO");

                // Handle different formats
                String kareKodNoAfterENT = "";
                if (fullKareKodNo.contains("ENT")) {
                    kareKodNoAfterENT = fullKareKodNo.split("ENT")[1];
                }

                // Check for duplicate
                checkStmt.setString(1, kareKodNoAfterENT);
                boolean isDuplicate = false;
                try (ResultSet rs = checkStmt.executeQuery()) {
                    isDuplicate = rs.next();
                }

                if (isDuplicate) {
                    // Add to duplicate messages list
                    String barcode = extractBarcode(qrCode);
                    duplicateMessages.add(String.format("Kodu: \"%s\" olan, \"%s\" seri numaralı ürün, veritabanında bulunduğu için eklenmemiştir",
                            barcode, kareKodNoAfterENT));
                    continue;
                }

                // If not duplicate, proceed with insertion
                String tedasKirilim = "";
                if (qrCode.contains("TCDD")) {
                    tedasKirilim = ""; // Empty for TCDD items
                } else {
                    tedasKirilim = extractTedasKirilimBeforeENT(fullKareKodNo);
                }
                String barcode = extractBarcode(qrCode);
                String imalYili = extractPattern(qrCode, "IMALYILI");

                Long itemId = barcodeToItemId.get(barcode);
                if (itemId == null) {
                    throw new SQLException("Could not find item ID for barcode: " + barcode);
                }

                insertStmt.setString(1, kareKodNoAfterENT);
                insertStmt.setString(2, tedasKirilim);
                insertStmt.setString(3, extractPattern(qrCode, "MARKA"));
                insertStmt.setString(4, barcode);
                insertStmt.setString(5, extractPattern(qrCode, "TIPI"));
                insertStmt.setString(6, imalYili);
                insertStmt.setString(7, barcode);
                insertStmt.setLong(8, slipId);
                insertStmt.setString(9, "Adet");
                insertStmt.setInt(10, 1);
                insertStmt.setLong(11, itemId);
                insertStmt.setString(12, item.entryMethod);
                insertStmt.setString(13, ""); // Insert empty ADDCODE

                insertStmt.addBatch();
            }

            int[] results = insertStmt.executeBatch();
            Log.d(TAG, "Batch execution results: " + java.util.Arrays.toString(results));
        }
    }

    private void verifyDataInserted(Connection conn, long slipId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM AST_PRODUCTION_SLIPS WHERE ID = ?")) {
            stmt.setLong(1, slipId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                Log.d(TAG, "Verified slips count: " + rs.getInt(1));
            }
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM AST_PRODUCTION_ITEMS WHERE SLIPID = ?")) {
            stmt.setLong(1, slipId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                Log.d(TAG, "Verified items count: " + rs.getInt(1));
            }
        }
    }
    private long insertProductionSlip(Connection conn) throws SQLException {
        int currentSlipNumber = Integer.parseInt(currentReceiptNo);
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        int lastDbNumber = dbHelper.getLastSlipNumber();
        int length = dbHelper.getSlipNumberLength();

        int numberToUse = Math.max(currentSlipNumber, lastDbNumber);

        // Update LASTNR
        String updateLastNr = "UPDATE A_ADOCNUM SET LASTNR = ? WHERE LOGICALREF = 5";
        try (PreparedStatement updateStmt = conn.prepareStatement(updateLastNr)) {
            updateStmt.setInt(1, numberToUse + 1);
            updateStmt.executeUpdate();
        }

        String formattedNumber = String.format("%0" + length + "d", numberToUse);
        // Verify we have a valid operator name
        if (currentOperator == null || currentOperator.isEmpty()) {
            // Try to get it one more time
            SharedPreferences loginPrefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
            currentOperator = loginPrefs.getString("logged_in_username", "Unknown User");
        }

        String insertSlipQuery = "INSERT INTO AST_PRODUCTION_SLIPS " +
                "(STATUS, SLIPDATE, SLIPNR, CREATEDUSERNAME, SLIPTYPE, CREATEDDATE, SLIPTYPEID, INTEGRATEDLOGO, DOCNUMBER, SIGN) " +
                "OUTPUT INSERTED.ID VALUES (1, ?, ?, ?, 1, GETDATE(), 1, 0, ?, 0)";

        try (PreparedStatement stmt = conn.prepareStatement(insertSlipQuery)) {
            stmt.setTimestamp(1, Timestamp.valueOf(creationTime));
            stmt.setString(2, formattedNumber);
            stmt.setString(3, currentOperator);
            stmt.setString(4, "");

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("ID");
                }
                throw new SQLException("Failed to get slip ID");
            }
        }
    }
    ProductionReceiptManager receiptManager;
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

        EditText tedasKirilimInput = createEditText("Stok No (örn: 561007ENT22000401)");
        EditText barcodeInput = createEditText("Seri No");
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
                String tedasPart = fullKareKodNo.split("ENT")[0];

                String formattedQR = formatManualQRCode(fullKareKodNo, tedasPart, malzeme, tipi, imalYili, barcode);

                QRCodeData qrData = new QRCodeData(formattedQR, fullKareKodNo, tedasPart, barcode);

                runOnUiThread(() -> {
                    dialog.dismiss();
                    showProcessedItemDialog(qrData, itemInfo, () ->
                            processScannedItem(qrData, itemInfo, "MANUAL")
                    );
                });

            } catch (Exception e) {
                Log.e(TAG, "Error processing manual entry", e);
                runOnUiThread(() -> showToast("İşlem sırasında hata oluştu: " + e.getMessage()));
            }
        });
    }

    private String getItemInfoFromDatabase(String tedasKirilim, String barcode) {
        Log.d(TAG, String.format("GetItemInfo: TEDAS code = '%s', barcode = '%s'",
                tedasKirilim != null ? tedasKirilim : "null", barcode));

        try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {
            // First try: with TEDAS if available
            if (tedasKirilim != null && !tedasKirilim.isEmpty() && !tedasKirilim.equals("TCDD")) {
                String tedasCode = tedasKirilim.contains("ENT") ?
                        tedasKirilim.split("ENT")[0] : tedasKirilim;

                String query = "SELECT i.DESCRIPTION, i.GROUPCODE, t.DESCRIPTION as TEDAS_DESCRIPTION " +
                        "FROM AST_ITEMS i " +
                        "JOIN AST_ITEMTYPES t ON i.ID = t.ITEMID " +
                        "WHERE i.CODE = ? AND t.DESCRIPTION = ?";

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, barcode.trim());
                    stmt.setString(2, tedasCode.trim());

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return formatItemInfo(rs);
                        }
                    }
                }
            }

            // Special handling for TCDD items
            if (tedasKirilim != null && tedasKirilim.equals("TCDD")) {
                String query = "SELECT i.DESCRIPTION, i.GROUPCODE, '' as TEDAS_DESCRIPTION " +
                        "FROM AST_ITEMS i " +
                        "WHERE i.CODE = ?";

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, barcode.trim());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return formatItemInfo(rs);
                        }
                    }
                }
            }

            // Fallback: try with just the barcode
            String fallbackQuery = "SELECT i.DESCRIPTION, i.GROUPCODE, '' as TEDAS_DESCRIPTION " +
                    "FROM AST_ITEMS i " +
                    "WHERE i.CODE = ?";

            try (PreparedStatement stmt = conn.prepareStatement(fallbackQuery)) {
                stmt.setString(1, barcode.trim());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return formatItemInfo(rs);
                    }
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Database error while checking item", e);
            Log.e(TAG, "SQL Error: " + e.getMessage());
        }
        return null;
    }

    private String formatItemInfo(ResultSet rs) throws SQLException {
        String dbMalzeme = rs.getString("GROUPCODE");
        String dbTipi = rs.getString("DESCRIPTION");
        String dbTedasKirilim = rs.getString("TEDAS_DESCRIPTION");

        // If TEDAS_DESCRIPTION is null, use an empty string
        dbTedasKirilim = dbTedasKirilim != null ? dbTedasKirilim : "";

        Log.d(TAG, String.format("GetItemInfo Results: malzeme='%s', tipi='%s', tedas='%s'",
                dbMalzeme, dbTipi, dbTedasKirilim));

        return String.format("%s|%s|%s", dbMalzeme, dbTipi, dbTedasKirilim);
    }

    private boolean validateTedasCode(String kareKodNoTedas, String tedasKirilim, String barcode) {
        Log.d(TAG, String.format("Validating: KareKodNo part = '%s', TedasKirilim = '%s', Barcode = '%s'",
                kareKodNoTedas, tedasKirilim, barcode));

        if (!isNetworkAvailable()) {
            showToast("İnternet bağlantısı yok. Lütfen bağlantınızı kontrol edin.");
            return false;
        }

        try (Connection conn = databaseHelper.getAnatoliaSoftConnection()) {
            // For TCDD items, only validate the barcode exists
            if (tedasKirilim != null && tedasKirilim.equals("TCDD")) {
                String query = "SELECT COUNT(*) FROM AST_ITEMS WHERE CODE = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, barcode.trim());
                    try (ResultSet rs = stmt.executeQuery()) {
                        rs.next();
                        int count = rs.getInt(1);
                        Log.d(TAG, "TCDD Barcode Validation Result: count = " + count);
                        return count > 0;
                    }
                }
            }

            // For TEDAS items, keep the original validation logic
            if (tedasKirilim != null && !tedasKirilim.isEmpty()) {
                String query = "SELECT COUNT(*) FROM AST_ITEMS i " +
                        "JOIN AST_ITEMTYPES t ON i.ID = t.ITEMID " +
                        "WHERE i.CODE = ? AND t.DESCRIPTION = ?";

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, barcode.trim());
                    stmt.setString(2, tedasKirilim.trim());

                    try (ResultSet rs = stmt.executeQuery()) {
                        rs.next();
                        int count = rs.getInt(1);
                        Log.d(TAG, "TEDAS Validation Result: count = " + count);
                        return count > 0;
                    }
                }
            }

            // If no TEDAS info, just check if the barcode exists
            String fallbackQuery = "SELECT COUNT(*) FROM AST_ITEMS WHERE CODE = ?";
            try (PreparedStatement stmt = conn.prepareStatement(fallbackQuery)) {
                stmt.setString(1, barcode.trim());
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    int count = rs.getInt(1);
                    Log.d(TAG, "Barcode-only Validation Result: count = " + count);
                    return count > 0;
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error validating code", e);
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
    private void showScannerInputDialog() {
        if (isCameraActive) {
            toggleCamera();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Barkod Tarayıcı");

        LinearLayout layout = createScannerInputLayout();
        builder.setView(layout);
        builder.setPositiveButton("Onayla", null);
        builder.setNegativeButton("İptal", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        setupScannerInputDialog(dialog, layout);
        dialog.show();
    }
    private LinearLayout createScannerInputLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        EditText scannerInput = createEditText("Barkod tarayıcı bekleniyor...");
        scannerInput.requestFocus(); // Automatically focus the input field for scanner

        layout.addView(scannerInput);

        return layout;
    }
    private void setupScannerInputDialog(AlertDialog dialog, LinearLayout layout) {
        EditText scannerInput = (EditText) layout.getChildAt(0);

        scannerInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isProcessing) return; // Prevent re-entry
                isProcessing = true;

                String scannedData = s.toString().trim();
                if (isValidQRFormat(scannedData)) {
                    executorService.submit(() -> processScannerInput(scannedData, dialog, scannerInput));
                } else {
                    // Reset flag if format is invalid
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
    }
    private boolean isValidQRFormat(String qrCode) {
        // First check if it starts and ends correctly
        if (!qrCode.startsWith("||") || !qrCode.contains("||")) {
            return false;
        }

        // Split the content into sections
        String[] sections = qrCode.split("\\|");

        // Check for minimum required sections
        if (sections.length < 8) {
            return false;
        }

        // Create a set of required patterns to check
        Set<String> foundPatterns = new HashSet<>();

        // Check each section
        for (String section : sections) {
            if (section.startsWith("KAREKODNO_")) foundPatterns.add("KAREKODNO");
            else if (section.equals("TCDD") || section.startsWith("TEDASKIRILIM_")) foundPatterns.add("TEDAS");
            else if (section.startsWith("MARKA_")) foundPatterns.add("MARKA");
            else if (section.startsWith("MALZEME_")) foundPatterns.add("MALZEME");
            else if (section.startsWith("TIPI_")) foundPatterns.add("TIPI");
            else if (section.startsWith("IMALYILI_")) foundPatterns.add("IMALYILI");
        }

        // Verify all required patterns are present
        return foundPatterns.contains("KAREKODNO") &&
                foundPatterns.contains("TEDAS") &&
                foundPatterns.contains("MARKA") &&
                foundPatterns.contains("MALZEME") &&
                foundPatterns.contains("TIPI") &&
                foundPatterns.contains("IMALYILI");
    }

    private void showProcessedItemDialog(QRCodeData data, String itemInfo, Runnable onConfirm) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("İşlenen Ürün Detayları");
        builder.setMessage(formatScannedItemText(data.rawData, DATE_FORMAT.format(new Date())));
        builder.setCancelable(false); // Prevent dialog from being canceled on outside touch
        builder.setPositiveButton("Onayla", (dialog, which) -> {
            new Handler().postDelayed(() -> {
                onConfirm.run();
            }, 500);
        });
        builder.setNegativeButton("İptal", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false); // Prevent cancellation when touching outside
        dialog.show();
    }
    private void processScannerInput(String scannedData, AlertDialog dialog, EditText scannerInput) {
        try {
            if (!isValidQRFormat(scannedData)) {
                runOnUiThread(() -> {
                    showAlertDialog("Hata", "Geçersiz barkod formatı", scannerInput);
                    isProcessing = false;
                });
                return;
            }

            QRCodeData parsedData = parseQRCode(scannedData);
            if (parsedData == null) {
                runOnUiThread(() -> {
                    showAlertDialog("Hata", "Barkod ayrıştırma hatası", scannerInput);
                    isProcessing = false;
                });
                return;
            }

            String kareKodNoTedas = parsedData.kareKodNo.contains("ENT") ?
                    parsedData.kareKodNo.split("ENT")[0] : "";

            if (isAlreadyScanned(parsedData.kareKodNo)) {
                runOnUiThread(() -> {
                    showAlertDialog("Uyarı", "Bu ürün zaten taranmış", scannerInput);
                    isProcessing = false;
                });
                return;
            }

            if (!validateTedasCode(kareKodNoTedas, parsedData.tedasKirilim, parsedData.barcode)) {
                runOnUiThread(() -> {
                    showAlertDialog("Hata", "TEDAŞ kodu ve barkod eşleşmesi bulunamadı", scannerInput);
                    isProcessing = false;
                });
                return;
            }

            String itemInfo = getItemInfoFromDatabase(parsedData.tedasKirilim, parsedData.barcode);
            if (itemInfo == null) {
                runOnUiThread(() -> {
                    showAlertDialog("Hata", "Ürün veya TEDAŞ eşleşmesi bulunamadı", scannerInput);
                    isProcessing = false;
                });
                return;
            }

            runOnUiThread(() -> {
                dialog.dismiss();
                new Handler().postDelayed(() -> {
                    showProcessedItemDialog(parsedData, itemInfo, () -> {
                        processScannedItem(parsedData, itemInfo, "SCANNER");
                        showScannerInputDialog();
                    });
                    isProcessing = false;
                }, 500);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error processing scanner input", e);
            runOnUiThread(() -> {
                showAlertDialog("Hata", "İşlem sırasında hata oluştu: " + e.getMessage(), scannerInput);
                isProcessing = false;
            });
        }
    }
    private void showAlertDialog(String title, String message, EditText scannerInput) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false); // Prevent dialog from being canceled on outside touch
        builder.setPositiveButton("Tamam", (dialog, which) -> {
            clearAndRefocusInput(scannerInput);
            dialog.dismiss();
        });
        builder.setOnDismissListener(dialog -> clearAndRefocusInput(scannerInput));
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false); // Prevent cancellation when touching outside
        dialog.show();
    }

    private void clearAndRefocusInput(EditText scannerInput) {
        scannerInput.setText(""); // Clear the input field
        scannerInput.requestFocus(); // Refocus on the input field
    }

    private void showScannedItemsList() {
        if (scannedItems.isEmpty()) {
            showToast("Henüz okutulan ürün bulunmamaktadır");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Okutulan Ürünler");

        // Create ScrollView container
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Set maximum height for ScrollView (80% of screen height)
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(maxHeight, ViewGroup.LayoutParams.WRAP_CONTENT)));

        LinearLayout layout = createScannedItemsLayout();
        scrollView.addView(layout);
        builder.setView(scrollView);
        builder.setPositiveButton("Kapat", null);
        builder.show();
    }

    private LinearLayout createScannedItemsLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        // Add items count header
        TextView countHeader = new TextView(this);
        countHeader.setText("Toplam Ürün: " + scannedItems.size());
        countHeader.setTypeface(null, Typeface.BOLD);
        countHeader.setPadding(0, 0, 0, 20);
        layout.addView(countHeader);

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

            // Optional: you might want to adjust the alpha for better visual feedback
            float alpha = hasScannedItems ? 1.0f : 0.5f;
            saveButton.setAlpha(alpha);
            confirmButton.setAlpha(alpha);
        });
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
    public void onBackPressed() {
        if (!scannedItems.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Taslak Kaydet")
                    .setMessage("Okutulmuş ürünler var, taslak olarak kaydedilsin mi?")
                    .setPositiveButton("Evet", (dialog, which) -> {
                        saveDraft();
                        super.onBackPressed();
                    })
                    .setNegativeButton("Hayır", (dialog, which) -> {
                        super.onBackPressed();
                    })
                    .setCancelable(true)
                    .show();
        } else {
            super.onBackPressed();
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