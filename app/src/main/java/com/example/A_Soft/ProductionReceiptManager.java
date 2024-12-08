package com.example.A_Soft;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProductionReceiptManager {
    private static final String PREFS_NAME = "ProductionReceiptPrefs";
    private static final String RECEIPT_COUNT_KEY = "ReceiptCount";
    private static final String RECEIPTS_KEY = "Receipts";
    private final Context context;
    private final Gson gson;

    public ProductionReceiptManager(Context context) {
        this.context = context;
        this.gson = new Gson();
    }

    public String generateNewReceiptNo() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int count = prefs.getInt(RECEIPT_COUNT_KEY, 0) + 1;
        prefs.edit().putInt(RECEIPT_COUNT_KEY, count).apply();
        return String.format("%05d", count);
    }

    public void saveReceipt(ProductionReceipt receipt) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, String> receipts = getReceiptsMap(prefs);
        receipts.put(receipt.getReceiptNo(), gson.toJson(receipt));
        prefs.edit().putString(RECEIPTS_KEY, gson.toJson(receipts)).apply();
    }

    public void deleteReceipt(String receiptNo) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, String> receipts = getReceiptsMap(prefs);
        receipts.remove(receiptNo);
        prefs.edit().putString(RECEIPTS_KEY, gson.toJson(receipts)).apply();
    }

    public List<ProductionReceipt> getAllReceipts() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, String> receipts = getReceiptsMap(prefs);
        List<ProductionReceipt> receiptList = new ArrayList<>();

        for (String json : receipts.values()) {
            receiptList.add(gson.fromJson(json, ProductionReceipt.class));
        }

        return receiptList;
    }

    private Map<String, String> getReceiptsMap(SharedPreferences prefs) {
        String json = prefs.getString(RECEIPTS_KEY, "{}");
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        return gson.fromJson(json, type);
    }
}
