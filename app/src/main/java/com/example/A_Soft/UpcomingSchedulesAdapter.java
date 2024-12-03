package com.example.A_Soft;

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

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView dateTextView, receiptNoTextView, materialCodeTextView, materialNameTextView, amountTextView, statusTextView, carPlateTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            receiptNoTextView = itemView.findViewById(R.id.receiptNoTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
            carPlateTextView = itemView.findViewById(R.id.carPlateTextView);
        }

        public void bind(final DraftReceipt receipt, final OnItemClickListener listener) {
            statusTextView.setText(receipt.getStatus());
            dateTextView.setText("Tarih: " + receipt.getDate());
            receiptNoTextView.setText("Fiş No: " + receipt.getReceiptNo());
            carPlateTextView.setText("Araç Plaka: " + receipt.getCarPlate());

            // Disable clicking for completed invoices
            if (receipt.getStatus() != null && receipt.getStatus().contains("TAMAMLANDI")) {
                itemView.setClickable(false);
                itemView.setEnabled(false);
                itemView.setAlpha(0.5f); // Optional: make it visually less prominent
            } else {
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onItemClick(receipt);
                    }
                });
            }
        }
    }
}
