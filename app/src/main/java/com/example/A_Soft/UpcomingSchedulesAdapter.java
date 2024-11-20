package com.example.A_Soft;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView; // Import RecyclerView here

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
        private TextView dateTextView;
        private TextView carPlateTextView;
        private TextView receiptNoTextView;
        private TextView statusTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            carPlateTextView = itemView.findViewById(R.id.carPlateTextView);
            receiptNoTextView = itemView.findViewById(R.id.receiptNoTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
        }

        public void bind(final DraftReceipt receipt, final OnItemClickListener listener) {
            dateTextView.setText(receipt.getDate());
            carPlateTextView.setText(receipt.getCarPlate());
            receiptNoTextView.setText(receipt.getReceiptNo());
            statusTextView.setText(receipt.getStatus());

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(receipt);
                }
            });
        }
    }
}