package com.example.userpanel2;

import android.os.AsyncTask;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReceiptIslem extends Fragment {

    private static final String DB_URL = DatabaseHelper.DB_URL;
    private static final String DB_USER = DatabaseHelper.DB_USER;
    private static final String DB_PASSWORD = DatabaseHelper.DB_PASSWORD;

    private Button retrieveItemListButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.receipt_islem, container, false);

        retrieveItemListButton = rootView.findViewById(R.id.retrieveItemListButton);

        // Set onClickListener for the retrieveItemListButton
        retrieveItemListButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Retrieve item list from database asynchronously
                new FetchItemListTask().execute();
            }
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
            Log.d("ItemFetch", "Item list size: " + itemList.size());
            return itemList;
        }


        @Override
        protected void onPostExecute(List<String> itemList) {
            // Show the bottom sheet dialog with the retrieved item list
            ItemListBottomSheetDialogFragment bottomSheetDialogFragment = new ItemListBottomSheetDialogFragment();
            Bundle args = new Bundle();
            args.putStringArrayList("itemList", new ArrayList<>(itemList));
            bottomSheetDialogFragment.setArguments(args);
            bottomSheetDialogFragment.show(getParentFragmentManager(), bottomSheetDialogFragment.getTag());
        }

    }
}
