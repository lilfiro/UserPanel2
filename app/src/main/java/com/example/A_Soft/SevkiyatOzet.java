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
        // Initialize DatabaseHelper
        DatabaseHelper databaseHelper = new DatabaseHelper(requireContext());

        // Start fetching data
        new FetchDetailsTask(databaseHelper).execute(ficheNo);

        return rootView;
    }


    private class FetchDetailsTask extends AsyncTask<String, Void, List<DraftReceipt>> {
        private final DatabaseHelper databaseHelper;

        public FetchDetailsTask(DatabaseHelper databaseHelper) {
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
                String anatoliaSoftItemsTable = databaseHelper.getAnatoliaSoftTableName("AST_ITEMS");
                String anatoliaSoftShipPlanTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN");

                String query = String.format(
                        "SELECT " +
                                "CONVERT(VARCHAR, SP.SLIPDATE, 23) AS [Tarih], " +
                                "STRING_AGG(IT.CODE, ', ') AS [Malzeme Kodu], " +
                                "STRING_AGG(IT.NAME, ', ') AS [Malzeme Adı], " +
                                "STRING_AGG(CAST(SL.AMOUNT AS VARCHAR), ', ') AS [Miktar], " +
                                "SP.SLIPNR AS [Fiş Kodu], " +
                                "CASE SP.STATUS WHEN 0 THEN 'Açık' WHEN 1 THEN 'Kısmi' ELSE 'Bilinmeyen' END AS [Durum], " +
                                "SP.ORGNR AS [Operasyon Fiş No] " +
                                "FROM %s ST " +
                                "INNER JOIN %s SL ON SL.STFICHEREF = ST.LOGICALREF " +
                                "INNER JOIN %s IT ON IT.LOGICALREF = SL.STOCKREF " +
                                "LEFT JOIN %s AI ON AI.CODE = IT.CODE " +
                                "LEFT JOIN %s SP ON SP.SLIPNR = ST.FICHENO " +
                                "WHERE ST.TRCODE = 8 " +
                                "AND ST.BILLED = 0 " +
                                "AND ST.FICHENO IN (%s) " +
                                "AND SP.ORGNR IS NOT NULL " +
                                "AND (SP.STATUS = 0 OR SP.STATUS = 1) " +
                                "GROUP BY SP.SLIPNR, SP.SLIPDATE, SP.STATUS, SP.ORGNR",
                        tigerStFicheTable,
                        tigerStLineTable,
                        tigerItemsTable,
                        anatoliaSoftItemsTable,
                        anatoliaSoftShipPlanTable,
                        ficheNo
                );

                try (PreparedStatement statement = connection.prepareStatement(query);
                     ResultSet resultSet = statement.executeQuery()) {

                    while (resultSet.next()) {
                        detailsList.add(new DraftReceipt(
                                resultSet.getString("Tarih"),
                                resultSet.getString("Malzeme Kodu"),
                                resultSet.getString("Malzeme Adı"),
                                resultSet.getString("Miktar"),
                                resultSet.getString("Fiş Kodu"),
                                resultSet.getString("Durum"),
                                resultSet.getString("Operasyon Fiş No")
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
            if (!details.isEmpty()) {
                // Build the display format for all receipt details
                StringBuilder displayData = new StringBuilder();
                for (DraftReceipt detail : details) {
                    displayData.append("Tarih: ").append(detail.getDate()).append("\n");
                    displayData.append("Malzeme Kodu: ").append(detail.getMaterialCode()).append("\n");
                    displayData.append("Malzeme Adı: ").append(detail.getMaterialName()).append("\n");
                    displayData.append("Miktar: ").append(detail.getAmount()).append("\n");
                    displayData.append("Fiş Kodu: ").append(detail.getReceiptNo()).append("\n");
                    displayData.append("Durum: ").append(detail.getStatus()).append("\n");
                    displayData.append("Operasyon Fiş No: ").append(detail.getOprFicheNo()).append("\n\n");
                }
                productTextView.setText(displayData.toString());
            } else {
                ficheNoTextView.setText("Veri bulunamadı.");
            }
        }
    }
}