package com.example.A_Soft;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SevkiyatMainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private final List<DraftReceipt> receiptList = new ArrayList<>();
    private UpcomingSchedulesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sevkiyat_giris);

        recyclerView = findViewById(R.id.upcomingSchedulesRecyclerView); // Ensure this matches your layout ID
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize adapter
        adapter = new UpcomingSchedulesAdapter(receiptList, new UpcomingSchedulesAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(DraftReceipt receipt) {
                // Only navigate if the receipt is not completed
                if (!receipt.getStatus().contains("TAMAMLANDI")) {
                    //Intent intent = new Intent(SevkiyatMainActivity.this, SevkiyatInvoiceFragmentActivity.class);
                    Intent intent = new Intent(SevkiyatMainActivity.this, SevkiyatQR_ScreenActivity.class);
                    intent.putExtra("FICHENO", receipt.getReceiptNo());
                    startActivity(intent);
                }
            }
        });

        recyclerView.setAdapter(adapter);
        // Initialize DatabaseHelper
        DatabaseHelper databaseHelper = new DatabaseHelper(this);

        // Fetch data using DatabaseHelper
        new FetchDraftReceiptsTask(databaseHelper).execute();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the data when returning to this activity
        new FetchDraftReceiptsTask(new DatabaseHelper(this)).execute();
    }

    // AsyncTask to fetch data from the database in the background
    private class FetchDraftReceiptsTask extends AsyncTask<Void, Void, List<DraftReceipt>> {
        private final DatabaseHelper databaseHelper;

        public FetchDraftReceiptsTask(DatabaseHelper databaseHelper) {
            this.databaseHelper = databaseHelper;
        }

        @Override
        protected List<DraftReceipt> doInBackground(Void... voids) {
            List<DraftReceipt> drafts = new ArrayList<>();
            try (Connection connection = databaseHelper.getAnatoliaSoftConnection()) {
                // Dynamic table names using DatabaseHelper methods
                String anatoliaSoftShipPlanTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN");
                String anatoliaSoftShipPlanLineTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLANLINE");
                String anatoliaSoftCarsTable = databaseHelper.getAnatoliaSoftTableName("AST_CARS");
                String tigerItemsTable = databaseHelper.getTigerDbItemsTableName("ITEMS");

                String query = String.format(
                        "SELECT " +
                                "SHP.SLIPNR AS [Fiş No], " +  // Changed from ORGNR to SLIPNR
                                "SHP.ORGNR AS [Fiş Kodu], " +  // Kept ORGNR as Fiş Kodu
                                "STUFF((SELECT ',' + CAST(QUANTITY AS VARCHAR) " +
                                "       FROM %s INNER_SHPL " +
                                "       WHERE INNER_SHPL.SHIPPLANID = SHP.ID " +
                                "       FOR XML PATH('')), 1, 1, '') AS [Miktar], " +
                                "SHP.SLIPDATE AS [Tarih], " +
                                "AC.ARAC_PLAKA AS [Araç Plaka], " +
                                "AC.ARAC_KULLANICISI AS [Araç Kullanıcısı], " +  // Added car user
                                "SHP.STATUS AS [Durum], " +
                                "SUM(SHPL.QUANTITY) AS [Total Quantity], " +
                                "SUM(SHPL.TOTALWEIGHT) AS [Total Weight] " +
                                "FROM %s SHP " +
                                "INNER JOIN %s SHPL ON SHP.ID = SHPL.SHIPPLANID " +
                                "LEFT JOIN %s AC ON AC.ARAC_KODU = SHP.CARID " +
                                "LEFT JOIN %s IT ON IT.LOGICALREF = SHPL.ERPITEMID " +
                                "WHERE SHP.STATUS IN (0, 1) " +
                                "GROUP BY SHP.ID, SHP.STATUS, AC.ARAC_PLAKA, AC.ARAC_KULLANICISI, SHP.SLIPDATE, SHP.SLIPNR, SHP.ORGNR " +  // Added ARAC_KULLANICISI to GROUP BY
                                "ORDER BY SHP.STATUS ASC, SHP.SLIPDATE DESC",
                        anatoliaSoftShipPlanLineTable,
                        anatoliaSoftShipPlanTable,
                        anatoliaSoftShipPlanLineTable,
                        anatoliaSoftCarsTable,
                        tigerItemsTable
                );

                try (PreparedStatement statement = connection.prepareStatement(query);
                     ResultSet resultSet = statement.executeQuery()) {

                    while (resultSet.next()) {
                        String date = resultSet.getString("Tarih");
                        String formattedDate = formatDate(date);
                        String receiptNo = resultSet.getString("Fiş No");  // This will now get SLIPNR
                        String ficheNo = resultSet.getString("Fiş Kodu");  // This will now get ORGNR
                        String carPlate = resultSet.getString("Araç Plaka");
                        String carUser = resultSet.getString("Araç Kullanıcısı");  // Get car user
                        String amount = resultSet.getString("Miktar");
                        String status = resultSet.getInt("Durum") == 0 ? "DEVAM EDİYOR" : "TAMAMLANDI";

                        DraftReceipt draft = new DraftReceipt(
                                formattedDate,
                                amount,
                                status,
                                ficheNo,
                                carPlate,
                                carUser,  // Added car user
                                receiptNo
                        );

                        drafts.add(draft);
                    }
                }
            } catch (Exception e) {
                Log.e("FetchDraftReceiptsTask", "Error fetching drafts", e);
            }
            return drafts;
        }


        @Override
        protected void onPostExecute(List<DraftReceipt> drafts) {
            // Update the list and notify the adapter
            receiptList.clear();
            receiptList.addAll(drafts);
            adapter.notifyDataSetChanged();
        }

        private String formatDate(String date) {
            // Implement date formatting as per your requirements (e.g., dd.MM.yyyy)
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                Date parsedDate = inputFormat.parse(date);
                return outputFormat.format(parsedDate);
            } catch (ParseException e) {
                e.printStackTrace();
                return date; // Return original date if parsing fails
            }
        }
    }
}
