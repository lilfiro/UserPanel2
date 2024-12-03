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
    private List<DraftReceipt> receiptList = new ArrayList<>();
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
                    intent.putExtra("FICHENO", receipt.getOprFicheNo());
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

    // AsyncTask to fetch data from the database in the background
    private class FetchDraftReceiptsTask extends AsyncTask<Void, Void, List<DraftReceipt>> {
        private final DatabaseHelper databaseHelper;

        public FetchDraftReceiptsTask(DatabaseHelper databaseHelper) {
            this.databaseHelper = databaseHelper;
        }

        @Override
        protected List<DraftReceipt> doInBackground(Void... voids) {
            List<DraftReceipt> drafts = new ArrayList<>();
            try (Connection connection = databaseHelper.getTigerConnection()) {
                // Dynamic table names using DatabaseHelper methods
                String tigerStFicheTable = databaseHelper.getTigerDbTableName("STFICHE");
                String tigerStLineTable = databaseHelper.getTigerDbTableName("STLINE");
                String tigerItemsTable = databaseHelper.getTigerDbItemsTableName("ITEMS");
                String anatoliaSoftItemsTable = databaseHelper.getAnatoliaSoftTableName("AST_ITEMS");
                String anatoliaSoftShipPlanTable = databaseHelper.getAnatoliaSoftTableName("AST_SHIPPLAN");
                String anatoliaSoftCarsTable = databaseHelper.getAnatoliaSoftTableName("AST_CARS");

                String query = String.format(
                        "SELECT " +
                                "SP.ORGNR AS [Fiş No], " +
                                "SP.SLIPNR AS [Fiş Kodu], " +
                                "STRING_AGG(CAST(SL.AMOUNT AS VARCHAR), ', ') AS [Miktar], " +
                                "COUNT(DISTINCT IT.LOGICALREF) AS [Toplam Kalem Sayısı], " +
                                "SP.SLIPDATE AS [Tarih], " +
                                "AC.ARAC_PLAKA AS [Araç Plaka], " +
                                "SP.STATUS AS [Durum] " +
                                "FROM %s ST " +
                                "INNER JOIN %s SL ON SL.STFICHEREF = ST.LOGICALREF " +
                                "INNER JOIN %s IT ON IT.LOGICALREF = SL.STOCKREF " +
                                "LEFT JOIN %s AI ON AI.CODE = IT.CODE " +
                                "LEFT JOIN %s SP ON SP.SLIPNR = ST.FICHENO " +
                                "LEFT JOIN %S AC ON AC.ARAC_KODU = SP.CARID " +
                                "WHERE ST.TRCODE = 8 " +
                                "AND ST.BILLED = 0 " +
                                "AND SP.ORGNR  IS NOT NULL " +
                                "AND (SP.STATUS = 0 OR SP.STATUS = 1) " +
                                "GROUP BY SP.ORGNR, SP.SLIPNR, SP.SLIPDATE, SP.STATUS, AC.ARAC_PLAKA",
                        tigerStFicheTable,
                        tigerStLineTable,
                        tigerItemsTable,
                        anatoliaSoftItemsTable,
                        anatoliaSoftShipPlanTable,
                        anatoliaSoftCarsTable
                );

                try (PreparedStatement statement = connection.prepareStatement(query);
                     ResultSet resultSet = statement.executeQuery()) {

                    while (resultSet.next()) {
                        String date = resultSet.getString("Tarih");
                        String formattedDate = formatDate(date);
                        String receiptNo = resultSet.getString("Fiş No");
                        String ficheNo = resultSet.getString("Fiş Kodu");
                        String carPlate = resultSet.getString("Araç Plaka");  // Fetch the car plate
                        String amount = resultSet.getString("Miktar");
                        int itemCount = resultSet.getInt("Toplam Kalem Sayısı");
                        String status;
                        if (resultSet.getInt("Durum") == 0) {
                            status = "DEVAM EDİYOR (" + itemCount + " Ürün)";
                        } else {
                            status = "TAMAMLANDI (" + itemCount + " Ürün)";
                        }

                        DraftReceipt draft = new DraftReceipt(
                                formattedDate,
                                amount,      // Pass amount here
                                status,
                                ficheNo,
                                carPlate,    // Pass car plate here
                                receiptNo    // Pass receipt number here
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
