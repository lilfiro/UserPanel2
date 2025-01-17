package com.example.A_Soft;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UpcomingSchedulesAdapter extends RecyclerView.Adapter<UpcomingSchedulesAdapter.ViewHolder> {

    private List<DraftReceipt> receiptList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(DraftReceipt receipt);
    }

    public UpcomingSchedulesAdapter(List<DraftReceipt> receiptList, OnItemClickListener listener) {
        this.receiptList = receiptList;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.sevkiyat_invoices_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        DraftReceipt receipt = receiptList.get(position);
        holder.bind(receipt, listener);
    }

    @Override
    public int getItemCount() {
        return receiptList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView dateTextView, receiptNoTextView, statusTextView, carPlateTextView, carUserTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            receiptNoTextView = itemView.findViewById(R.id.receiptNoTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
            carPlateTextView = itemView.findViewById(R.id.carPlateTextView);
            carUserTextView = itemView.findViewById(R.id.carUserTextView);  // Initialize carUserTextView
        }

        public void bind(final DraftReceipt receipt, final OnItemClickListener listener) {
            Log.d("UpcomingSchedulesAdapter", "Receipt status: " + receipt.getStatus() +
                    " for receipt: " + receipt.getReceiptNo());
            statusTextView.setText(receipt.getStatus());
            dateTextView.setText("Tarih: " + receipt.getDate());
            receiptNoTextView.setText("Fiş No: " + receipt.getReceiptNo());
            carPlateTextView.setText("Araç Plaka: " + receipt.getCarPlate());
            carUserTextView.setText("Sürücü: " + receipt.getCarUser());  // Display car user

            // Reset the view's state first
            itemView.setClickable(true);
            itemView.setEnabled(true);
            itemView.setAlpha(1.0f);

            // Check status and update accordingly
            if (receipt.getStatus() != null && receipt.getStatus().contains("TAMAMLANDI")) {
                itemView.setClickable(false);
                itemView.setEnabled(false);
                itemView.setAlpha(0.5f);
            } else {
                itemView.setOnClickListener(v -> listener.onItemClick(receipt));
            }
        }
    }
}
