package com.example.A_Soft;

import android.os.AsyncTask;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.util.Log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class SevkiyatOzet extends Fragment {

    private TextView ficheNoTextView, dateTextView, customerTextView, addressTextView, productTextView, aoFicheNoTextView;
    private String ficheNo;

    // Inflate the fragment's view
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Use the correct fragment-specific layout
        View rootView = inflater.inflate(R.layout.sevkiyat_invoices_details_receipts, container, false);

        // Initialize views
        productTextView = rootView.findViewById(R.id.detailProduct);

        // Retrieve arguments
        if (getArguments() != null) {
            ficheNo = getArguments().getString("FICHENO");
        }
        Log.d("FICHENO", "Received FICHENO: " + ficheNo);

        // Start fetching data
        new FetchDetailsTask().execute(ficheNo);

        return rootView;
    }


    private class FetchDetailsTask extends AsyncTask<String, Void, List<Map<String, String>>> {

        @Override
        protected List<Map<String, String>> doInBackground(String... params) {
            List<Map<String, String>> detailsList = new ArrayList<>();
            String ficheNo = params[0]; // Received FICHENO

            // Split by '/' and then format it properly
            String[] ficheNos = ficheNo.split("/"); // Split by '/' to handle multiple fiche numbers
            StringBuilder formattedFicheNo = new StringBuilder();
            for (String fiche : ficheNos) {
                if (formattedFicheNo.length() > 0) {
                    formattedFicheNo.append("','"); // Add a separator between fiche numbers
                }
                formattedFicheNo.append(fiche.trim()); // Trim whitespace and append ficheNo
            }

            // Add quotes around each ficheNo
            ficheNo = "'" + formattedFicheNo.toString() + "'";

            try (Connection connection = DriverManager.getConnection(DatabaseHelper.DB_URL, DatabaseHelper.DB_USER, DatabaseHelper.DB_PASSWORD)) {

                // Dynamically build table names using the table suffix
                String tableSuffix = "001"; // Replace with dynamic table suffix (e.g., "001", "002")
                String tablePrefix = String.format("LG_%s", tableSuffix);
                String tableName = tablePrefix + "_01_STFICHE";
                String stLineTable = tablePrefix + "_01_STLINE";
                String itemsTable = tablePrefix + "_ITEMS";

                // Construct the SQL query dynamically to refer to the TIGERDB database
                String query = String.format(
                        "SELECT " +
                                "ST.FICHENO AS [Fiş No], " +
                                "IT.NAME AS [Ürün] " + // Keep the 'items' related column
                                "FROM TIGERDB.dbo.%s ST " +
                                "INNER JOIN TIGERDB.dbo.%s SL ON SL.STFICHEREF = ST.LOGICALREF " +
                                "INNER JOIN TIGERDB.dbo.%s IT ON IT.LOGICALREF = SL.STOCKREF " +
                                "WHERE ST.FICHENO IN (%s) AND ST.TRCODE = 8 AND ST.BILLED = 0 " +
                                "ORDER BY ST.DATE_ ASC", // No need to include other fields
                        tableName, stLineTable, itemsTable,
                        ficheNo // Pass the formatted multiple FICHENO values directly
                );


                PreparedStatement statement = connection.prepareStatement(query);
                try (ResultSet resultSet = statement.executeQuery()) {

                    // Group by Fiş No and accumulate Ürünler
                    Map<String, StringBuilder> groupedItems = new HashMap<>();
                    while (resultSet.next()) {
                        String ficheNoValue = resultSet.getString("Fiş No");
                        String product = resultSet.getString("Ürün");

                        // If receipt already exists in map, append the item to it
                        if (groupedItems.containsKey(ficheNoValue)) {
                            groupedItems.get(ficheNoValue).append(", ").append(product);
                        } else {
                            groupedItems.put(ficheNoValue, new StringBuilder(product));
                        }
                    }

                    // Convert grouped data into list format for displaying
                    for (Map.Entry<String, StringBuilder> entry : groupedItems.entrySet()) {
                        Map<String, String> detail = new HashMap<>();
                        detail.put("Fiş No", entry.getKey());
                        detail.put("Ürünler", entry.getValue().toString());
                        detailsList.add(detail);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return detailsList;
        }

        @Override
        protected void onPostExecute(List<Map<String, String>> details) {
            if (!details.isEmpty()) {
                // Build the display format for all receipt details
                StringBuilder displayData = new StringBuilder();
                for (Map<String, String> detail : details) {
                    displayData.append("Fiş No: ").append(detail.get("Fiş No")).append("\n");
                    displayData.append("Ürünler: ").append(detail.get("Ürünler")).append("\n\n");
                }
                productTextView.setText(displayData.toString());
            } else {
                ficheNoTextView.setText("Veri bulunamadı.");
            }
        }
    }
}
