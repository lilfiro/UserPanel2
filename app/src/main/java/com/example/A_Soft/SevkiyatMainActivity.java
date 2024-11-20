package com.example.A_Soft;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

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
                // Pass selected receipt details to SevkiyatInvoiceFragmentActivity
                Intent intent = new Intent(SevkiyatMainActivity.this, SevkiyatInvoiceFragmentActivity.class);
                intent.putExtra("FICHENO", receipt.getOprFicheNo()); // Pass the FICHENO
                startActivity(intent); // Navigate to the activity
            }
        });

        recyclerView.setAdapter(adapter);

        // Fetch data
        new FetchDraftReceiptsTask().execute();
    }


    // AsyncTask to fetch data from the database in the background
    private class FetchDraftReceiptsTask extends AsyncTask<Void, Void, List<DraftReceipt>> {
        @Override
        protected List<DraftReceipt> doInBackground(Void... voids) {
            List<DraftReceipt> drafts = new ArrayList<>();
            try (Connection connection = DriverManager.getConnection(DatabaseHelper.DB_URL, DatabaseHelper.DB_USER, DatabaseHelper.DB_PASSWORD)) {
                String query = "SELECT OPR_DATE, OPR_CAR, OPR_NUMBER, OPR_FICHENO FROM ANATOLIASOFT.dbo.AST_OPERATION WHERE OPR_STATUS = 0";
                PreparedStatement statement = connection.prepareStatement(query);
                ResultSet resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    String date = resultSet.getString("OPR_DATE");
                    String carPlate = resultSet.getString("OPR_CAR");
                    String receiptNo = resultSet.getString("OPR_NUMBER");
                    String oprFicheNo = resultSet.getString("OPR_FICHENO");
                    String status = "DEVAM EDİYOR";

                    DraftReceipt draft = new DraftReceipt(date, carPlate, receiptNo, status, oprFicheNo);
                    drafts.add(draft);
                }
            } catch (Exception e) {
                e.printStackTrace();
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
    }
}
