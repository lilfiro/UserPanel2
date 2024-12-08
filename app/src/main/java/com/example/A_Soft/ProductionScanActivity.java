package com.example.A_Soft;

import android.app.AlertDialog;
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
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProductionScanActivity extends AppCompatActivity {
    private static final String TAG = "ProductionScanActivity";
    private static final Pattern QR_PATTERN = Pattern.compile("([A-Z]+)_([^|]+)");
    private Set<String> scannedKareKodNos = new HashSet<>();
    private Map<String, Long> materialCounts = new HashMap<>();
    private static final long SCAN_DEBOUNCE_INTERVAL = 2000; // 2 seconds

    private TableLayout tableLayout;
    private CameraSourcePreview cameraPreview;
    private TextView scanStatusTextView;
    private Button saveButton;
    private Button confirmButton;
    private Button manualQrButton;
    private ProductionReceiptManager receiptManager;
    private ProductionReceipt currentReceipt;
    private String currentReceiptNo;
    private ExecutorService executorService;
    private long lastScanTime = 0;
    private Set<ProductionItem> scannedItems = new HashSet<>();
    private String lastScannedQR = ""; // Add this to track last scanned QR

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_production_scan);

        currentReceiptNo = getIntent().getStringExtra("RECEIPT_NO");
        receiptManager = new ProductionReceiptManager(this);

        initializeComponents();
        loadReceipt();
        requestCameraPermissionIfNeeded();
    }

    private TextView lastScannedItemText; // Add this field

    private void initializeComponents() {
        tableLayout = findViewById(R.id.tableLayout);
        cameraPreview = findViewById(R.id.camera_preview);
        scanStatusTextView = findViewById(R.id.scan_status);
        saveButton = findViewById(R.id.saveButton);
        confirmButton = findViewById(R.id.confirmButton);
        manualQrButton = findViewById(R.id.manual_qr_button);
        executorService = Executors.newSingleThreadExecutor();

        styleTableLayout();
        setupButtons();
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

    private TextView createHeaderTextView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(16, 16, 16, 16);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
        tv.setTextColor(getResources().getColor(android.R.color.white));
        return tv;
    }

    private void setupButtons() {
        saveButton.setOnClickListener(v -> saveReceipt());
        confirmButton.setOnClickListener(v -> showConfirmDialog());
        manualQrButton.setOnClickListener(v -> showManualQRInputDialog());
    }

    private void setupBarcodeDetection() {
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.QR_CODE)
                .build();

        if (!barcodeDetector.isOperational()) {
            showAlert("Hata", "Barkod okuyucu başlatılamadı");
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
                    String qrCode = barcode.displayValue;

                    // Only process if it's a different QR code or enough time has passed
                    if (!qrCode.equals(lastScannedQR) ||
                            (System.currentTimeMillis() - lastScanTime) >= SCAN_DEBOUNCE_INTERVAL) {
                        lastScannedQR = qrCode;
                        processQRCode(qrCode);
                    }
                }
            }
        });
    }

    private void processQRCode(String qrCode) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime < SCAN_DEBOUNCE_INTERVAL) {
            return;
        }
        lastScanTime = currentTime;

        executorService.execute(() -> {
            Map<String, String> qrValues = parseQRCode(qrCode);

            String kareKodNo = qrValues.get("KAREKODNO");
            if (kareKodNo == null || kareKodNo.isEmpty()) {
                showAlert("Hata", "Geçersiz QR kod formatı");
                return;
            }

            if (scannedKareKodNos.contains(kareKodNo)) {
                showAlert("Uyarı", "Bu ürün zaten taranmış");
                return;
            }

            String malzeme = qrValues.get("MALZEME");
            if (malzeme == null || malzeme.isEmpty()) {
                showAlert("Hata", "Malzeme bilgisi bulunamadı");
                return;
            }

            ProductionItem newItem = new ProductionItem(
                    kareKodNo,
                    qrValues.get("TEDASKIRILIM"),
                    malzeme,
                    qrValues.get("TIPI")
            );

            runOnUiThread(() -> {
                // Show confirmation dialog before adding the item
                showItemConfirmationDialog(newItem, qrValues);

                // Update collections and UI
                scannedKareKodNos.add(kareKodNo);
                scannedItems.add(newItem);
                materialCounts.merge(malzeme, 1L, Long::sum);
                updateTable();
                scanStatusTextView.setText(String.format("Toplam Okutulan: %d", scannedItems.size()));
            });
        });
    }
    private Map<String, String> parseQRCode(String qrCode) {
        Map<String, String> values = new HashMap<>();

        // Split the QR code by pipes and process each section
        String[] sections = qrCode.split("\\|");

        for (String section : sections) {
            Matcher matcher = QR_PATTERN.matcher(section);
            if (matcher.matches()) {
                String key = matcher.group(1);   // The label (before _)
                String value = matcher.group(2);  // The value (after _)
                values.put(key, value);
            }
        }

        // Handle barcode (last part after all pipes)
        if (sections.length > 0) {
            String lastSection = sections[sections.length - 1];
            if (!lastSection.contains("_")) {
                values.put("BARCODE", lastSection.trim());
            }
        }

        return values;
    }

    private void updateTable() {
        // Clear existing rows except header
        int childCount = tableLayout.getChildCount();
        if (childCount > 1) {
            tableLayout.removeViews(1, childCount - 1);
        }

        // Add rows for each material
        materialCounts.forEach((material, count) -> {
            TableRow row = new TableRow(this);

            TextView materialView = createDataTextView(material);
            TextView countView = createDataTextView(String.valueOf(count));

            // Center align the count
            countView.setGravity(Gravity.CENTER);

            row.addView(materialView);
            row.addView(countView);
            tableLayout.addView(row);
        });
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


    private void showManualQRInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Manuel Ürün Girişi");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        EditText kareKodInput = new EditText(this);
        kareKodInput.setHint("KAREKODNO");
        EditText barcodeInput = new EditText(this);
        barcodeInput.setHint("Barkod");

        layout.addView(kareKodInput);
        layout.addView(barcodeInput);
        builder.setView(layout);

        builder.setPositiveButton("Onayla", null);
        builder.setNegativeButton("İptal", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String karekod = kareKodInput.getText().toString().trim();
                String barcode = barcodeInput.getText().toString().trim();

                if (karekod.isEmpty() || barcode.isEmpty()) {
                    showAlert("Uyarı", "Lütfen tüm alanları doldurun");
                    return;
                }

                String formattedQR = String.format("||KAREKODNO_%s|TEDASKIRILIM_561007|MARKA_ENT|MALZEME_BETON|TIPI_9.30/3|IMALYILI_2022||%s",
                        karekod, barcode);

                dialog.dismiss();
                processQRCode(formattedQR);
            });
        });

        dialog.show();
    }

    private void showItemConfirmationDialog(ProductionItem item, Map<String, String> qrValues) {
        String message = String.format("Malzeme Detayları:\nKAREKODNO: %s\nMALZEME: %s\nTİPİ: %s\nMARKA: %s\nBAKOD: %s",
                item.getSerialNo(),
                item.getMaterialName(),
                item.getType(),
                qrValues.get("MARKA"),
                qrValues.get("BARCODE"));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ürün Onayı")
                .setMessage(message)
                .setPositiveButton("Tamam", (dialog, which) -> {})
                .setCancelable(false)
                .show();
    }

    private void saveReceipt() {
        if (scannedItems.isEmpty()) {
            showAlert("Uyarı", "Kaydedilecek malzeme bulunmamaktadır");
            return;
        }

        currentReceipt.getItems().clear();
        currentReceipt.getItems().addAll(scannedItems);
        receiptManager.saveReceipt(currentReceipt);

        showAlert("Başarılı", "Fiş kaydedildi", true);
    }

    private void showConfirmDialog() {
        if (scannedItems.isEmpty()) {
            showAlert("Uyarı", "Onaylanacak malzeme bulunmamaktadır");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Onay")
                .setMessage("Fişi onaylamak istediğinizden emin misiniz?")
                .setPositiveButton("Evet", (dialog, which) -> {
                    currentReceipt.setStatus("COMPLETED");
                    receiptManager.saveReceipt(currentReceipt);
                    showAlert("Başarılı", "Fiş onaylandı", true);
                })
                .setNegativeButton("Hayır", null)
                .show();
    }

    private void loadReceipt() {
        List<ProductionReceipt> receipts = receiptManager.getAllReceipts();
        currentReceipt = receipts.stream()
                .filter(r -> r.getReceiptNo().equals(currentReceiptNo))
                .findFirst()
                .orElse(null);

        if (currentReceipt != null) {
            scannedItems.clear();
            scannedKareKodNos.clear();
            materialCounts.clear();

            scannedItems.addAll(currentReceipt.getItems());

            for (ProductionItem item : currentReceipt.getItems()) {
                scannedKareKodNos.add(item.getSerialNo());
                materialCounts.merge(item.getMaterialName(), 1L, Long::sum);
            }

            updateTable();
            scanStatusTextView.setText(String.format("Toplam Okutulan: %d", scannedItems.size()));
        }
    }

    private void showAlert(String title, String message) {
        showAlert(title, message, false);
    }

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupBarcodeDetection();
        } else {
            showAlert("Hata", "Kamera izni gereklidir");
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