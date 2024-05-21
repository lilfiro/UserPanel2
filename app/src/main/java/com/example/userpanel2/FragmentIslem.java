package com.example.userpanel2;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FragmentIslem extends Fragment implements QuantityDialogFragment.QuantityDialogListener {

    private static final String DB_URL = DatabaseHelper.DB_URL;
    private static final String DB_USER = DatabaseHelper.DB_USER;
    private static final String DB_PASSWORD = DatabaseHelper.DB_PASSWORD;

    private Button retrieveItemListButton;
    private TextView textViewSummary;
    private Map<String, Integer> selectedItemsMap = new HashMap<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.receipt_islem, container, false);

        retrieveItemListButton = rootView.findViewById(R.id.retrieveItemListButton);
        textViewSummary = rootView.findViewById(R.id.textViewSummary);

        // // click listener
        retrieveItemListButton.setOnClickListener(v -> {
            // Item listesini veritabanından eşzamansız olarak alır
            new FetchItemListTask().execute();
        });

        return rootView;
    }

    private class FetchItemListTask extends AsyncTask<Void, Void, List<String>> {

        @Override
        protected List<String> doInBackground(Void... voids) {
            List<String> itemList = new ArrayList<>();
            try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "SELECT name FROM Items";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            String itemName = resultSet.getString("name");
                            itemList.add(itemName);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return itemList;
        }

        @Override
        protected void onPostExecute(List<String> itemList) {
            // Item listesinin bulunduğu alt sayfa kutusunu gösterir
            ItemListBottomSheetDialogFragment bottomSheetDialogFragment = new ItemListBottomSheetDialogFragment();
            Bundle args = new Bundle();
            args.putStringArrayList("itemList", new ArrayList<>(itemList));
            bottomSheetDialogFragment.setArguments(args);
            bottomSheetDialogFragment.show(getParentFragmentManager(), bottomSheetDialogFragment.getTag());
        }
    }

    // SeçilenItemsMap'i QuantityDialogFragment'e iletir
    public void showQuantityPopup(String itemName) {
        QuantityDialogFragment quantityDialogFragment = QuantityDialogFragment.newInstance(itemName, selectedItemsMap);
        quantityDialogFragment.setQuantityDialogListener(this); // Set the listener
        quantityDialogFragment.show(getChildFragmentManager(), quantityDialogFragment.getTag());
    }

    // özeti günceller
    protected void updateSummary() {
        // seçilen ürünler ve miktarlarla birlikte güncelleme yapar
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Integer> entry : selectedItemsMap.entrySet()) {
            summary.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        // text view elementine sunar
        textViewSummary.setText(summary.toString());
    }

    @Override
    public void onQuantityConfirmed(String itemName, int quantity) {
        // SeçilenItemsMap'i yeni miktarla güncelle
        selectedItemsMap.put(itemName, quantity);
        // özeti günceller
        updateSummary();
    }
}
