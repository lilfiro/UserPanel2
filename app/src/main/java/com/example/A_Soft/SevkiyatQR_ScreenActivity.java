package com.example.A_Soft;

import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
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
    private void showScannerInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Barkod Okut");

        // Create layout for dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        // Create input field
        final EditText scanInput = new EditText(this);
        scanInput.setHint("Barkod");
        scanInput.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(scanInput);

        builder.setView(layout);

        // Set up the buttons
        builder.setPositiveButton("Onayla", null); // We'll override this below
        builder.setNegativeButton("İptal", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        // Override the positive button to handle validation
        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String scannedData = scanInput.getText().toString().trim();

                if (scannedData.isEmpty()) {
                    showAlert("Uyarı", "Lütfen barkod okutunuz");
                    return;
                }

                // Extract the KAREKODNO from the scanned format
                Pattern pattern = Pattern.compile("KAREKODNO_([^|]+)");
                Matcher matcher = pattern.matcher(scannedData);

                if (!matcher.find()) {
                    showAlert("Hata", "Geçersiz barkod formatı");
                    return;
                }

                String karekodno = matcher.group(1);

                // Extract the item code (last part after ||)
                String[] parts = scannedData.split("\\|\\|");
                if (parts.length < 2) {
                    showAlert("Hata", "Geçersiz barkod formatı");
                    return;
                }

                String itemCode = parts[parts.length - 1].trim();

                // Format the data in the expected QR format
                String formattedData = String.format("KAREKODNO_%s||%s", karekodno, itemCode);

                dialog.dismiss();
                processQRCode(formattedData);
            });
        });

        dialog.show();

        // Set focus to the input field and show keyboard
        scanInput.requestFocus();
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
        // Set table background and border
        tableLayout.setBackgroundResource(android.R.color.white);
        tableLayout.setPadding(2, 2, 2, 2);
        tableLayout.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));

        // Style header row
        TableRow headerRow = (TableRow) tableLayout.getChildAt(0);
        if (headerRow != null) {
            for (int i = 0; i < headerRow.getChildCount(); i++) {
                TextView headerCell = (TextView) headerRow.getChildAt(i);
                styleHeaderCell(headerCell);
            }
        }
    }
    private void styleHeaderCell(TextView cell) {
        cell.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
        cell.setTextColor(getResources().getColor(android.R.color.white));
        cell.setPadding(16, 16, 16, 16);
        cell.setTypeface(null, Typeface.BOLD);
    }

    private void styleDataCell(TextView cell) {
        cell.setBackgroundColor(getResources().getColor(android.R.color.white));
        cell.setPadding(16, 12, 16, 12);
        cell.setTextColor(getResources().getColor(android.R.color.black));

        // Add border
        GradientDrawable border = new GradientDrawable();
        border.setColor(getResources().getColor(android.R.color.white));
        border.setStroke(1, getResources().getColor(android.R.color.darker_gray));
        cell.setBackground(border);
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
        karekodInput.setHint("Stok Kodu");
        layout.addView(karekodInput);

        // Add some spacing
        View spacing = new View(this);
        spacing.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                30)); // 30dp spacing
        layout.addView(spacing);

        // Create Barcode input
        final EditText barcodeInput = new EditText(this);
        barcodeInput.setHint("Seri Kodu");
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
                ReceiptItemManager.ScanResult result = itemManager.cacheScannedItem(qrCodeData);
                runOnUiThread(() -> {
                    switch (result) {
                        case SUCCESS:
                            String itemCode = itemManager.extractItemCodeFromQR(qrCodeData);
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

    private void showItemConfirmationDialog(String qrCode) {
        // Parse the QR code to extract all needed information
        String karekodno = "";
        String malzeme = "";
        String tipi = "";

        // Extract KAREKODNO
        Pattern kareKodPattern = Pattern.compile("KAREKODNO_([^|]+)");
        Matcher kareKodMatcher = kareKodPattern.matcher(qrCode);
        if (kareKodMatcher.find()) {
            karekodno = kareKodMatcher.group(1);
        }

        // Extract MALZEME
        Pattern malzemePattern = Pattern.compile("MALZEME_([^|]+)");
        Matcher malzemeMatcher = malzemePattern.matcher(qrCode);
        if (malzemeMatcher.find()) {
            malzeme = malzemeMatcher.group(1);
        }

        // Extract TIPI
        Pattern tipiPattern = Pattern.compile("TIPI_([^|]+)");
        Matcher tipiMatcher = tipiPattern.matcher(qrCode);
        if (tipiMatcher.find()) {
            tipi = tipiMatcher.group(1);
        }

        // Build the message with the extracted information
        String message = String.format("KAREKODNO: %s\nMALZEME: %s\nTİPİ: %s",
                karekodno,
                malzeme,
                tipi);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ürün Onayı")
                .setMessage(message)
                .setPositiveButton("Tamam", (dialog, which) -> {
                    updateScanStatus();
                    confirmReceiptButton.setEnabled(itemManager.areAllItemsScanned());
                })
                .setCancelable(false)
                .show();
    }

    private void updateScanStatus() {
        runOnUiThread(() -> {
            // Update header or summary information
            String status = String.format("Okutulacak malzemeler: %d/%d",
                    itemManager.getScannedCount(),
                    itemManager.getTotalItems());
            scanStatusTextView.setText(status);

            // Update table rows dynamically
            for (int i = 1; i < tableLayout.getChildCount(); i++) { // Skip header row
                TableRow row = (TableRow) tableLayout.getChildAt(i);

                TextView materialNameView = (TextView) row.getChildAt(0); // First column
                TextView scannedQuantityView = (TextView) row.getChildAt(2); // Third column

                String materialName = materialNameView.getText().toString();
                int scannedCount = itemManager.getScannedCountForItem(materialName);

                scannedQuantityView.setText(String.valueOf(scannedCount));
            }

            // Enable/disable confirm button
            confirmReceiptButton.setEnabled(itemManager.areAllItemsScanned());
        });
    }


    private void loadReceiptItems() {
        executorService.submit(() -> {
            itemManager.loadReceiptItems();

            // Load draft data after receipt items are loaded
            runOnUiThread(() -> {
                loadDraftData();
                updateScanStatus();
            });
        });
    }

    // Update the updateReceiptStatus method
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

            // Add dynamic rows for each item
            for (DraftReceipt detail : details) {
                TableRow row = new TableRow(SevkiyatQR_ScreenActivity.this);

                TextView materialView = createTextView(detail.getMaterialName());
                TextView amountView = createTextView(detail.getAmount());
                TextView scannedView = createTextView("0");

                styleDataCell(materialView);
                styleDataCell(amountView);
                styleDataCell(scannedView);

                row.addView(materialView);
                row.addView(amountView);
                row.addView(scannedView);

                tableLayout.addView(row);
            }
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
        this.scannedSerials = new HashSet<>(draftData.scannedSerials);
        this.scannedItemCounts = new HashMap<>(draftData.scannedItemCounts);
        this.itemQuantities = new HashMap<>(draftData.itemQuantities);
        this.itemNames = new HashMap<>(draftData.itemNames);
    }
    public ReceiptItemManager(String receiptNo, DatabaseHelper databaseHelper) {
        this.receiptNo = receiptNo;
        this.databaseHelper = databaseHelper;
    }

    public void loadReceiptItems() {
        try (Connection conn = databaseHelper.getTigerConnection()) {
            String[] ficheNos = receiptNo.split("/");
            itemQuantities.clear();
            scannedItemCounts.clear();
            scannedSerials.clear();
            itemNames.clear();

            String tigerStFicheTable = databaseHelper.getTigerDbTableName("STFICHE");
            String tigerStLineTable = databaseHelper.getTigerDbTableName("STLINE");
            String tigerItemsTable = databaseHelper.getTigerDbItemsTableName("ITEMS");

            String ficheNosList = Arrays.stream(ficheNos)
                    .map(no -> "'" + no.trim() + "'")
                    .collect(Collectors.joining(","));

            String query = String.format(
                    "SELECT DISTINCT " +
                            "IT.CODE AS ItemCode, " +
                            "IT.NAME AS ItemName, " +
                            "SUM(SL.AMOUNT) AS TotalQuantity " +
                            "FROM %s ST " +
                            "INNER JOIN %s SL ON SL.STFICHEREF = ST.LOGICALREF " +
                            "INNER JOIN %s IT ON IT.LOGICALREF = SL.STOCKREF " +
                            "WHERE ST.FICHENO IN (%s) " +
                            "AND ST.TRCODE = 8 " +
                            "AND ST.BILLED = 0 " +
                            "GROUP BY IT.CODE, IT.NAME",
                    tigerStFicheTable, tigerStLineTable, tigerItemsTable, ficheNosList
            );

            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String itemCode = rs.getString("ItemCode");
                    String itemName = rs.getString("ItemName");
                    int totalQuantity = rs.getInt("TotalQuantity");
                    itemQuantities.put(itemCode, totalQuantity);
                    itemNames.put(itemCode, itemName);
                    scannedItemCounts.put(itemCode, 0);
                }
            }
        } catch (SQLException e) {
            Log.e(TAG, "Error loading receipt items: " + e.getMessage(), e);
        }
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

            // Check if serial has already been scanned
            if (scannedSerials.contains(serialNumber)) {
                return ScanResult.ALREADY_SCANNED;
            }

            // Check if item exists in receipt
            if (!itemQuantities.containsKey(itemCode)) {
                return ScanResult.ITEM_NOT_IN_RECEIPT;
            }

            // Check quantity limits
            int currentScannedCount = scannedItemCounts.get(itemCode);
            int totalQuantity = itemQuantities.get(itemCode);

            if (currentScannedCount >= totalQuantity) {
                return ScanResult.COMPLETE_ITEM;
            }


            // Add to tracking collections
            scannedSerials.add(serialNumber);
            qrCodeCache.add(new ScannedQRItem(serialNumber, itemCode, null));
            scannedItemCounts.put(itemCode, currentScannedCount + 1);

            return ScanResult.SUCCESS;

        } catch (Exception e) {
            Log.e(TAG, "Error processing scanned item", e);
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

            // Get ORGNR
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
                return false;
            }

            // Insert QR records
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

    public int getScannedCount() {
        return scannedItemCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    public int getTotalItems() {
        return itemQuantities.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
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