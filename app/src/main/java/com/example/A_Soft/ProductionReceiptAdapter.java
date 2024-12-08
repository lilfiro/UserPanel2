package com.example.A_Soft;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ProductionReceiptAdapter extends RecyclerView.Adapter<ProductionReceiptAdapter.ViewHolder> {
    private List<ProductionReceipt> receipts;
    private final OnReceiptClickListener clickListener;
    private final OnReceiptLongClickListener longClickListener;

    public interface OnReceiptClickListener {
        void onReceiptClick(ProductionReceipt receipt);
    }

    public interface OnReceiptLongClickListener {
        boolean onReceiptLongClick(ProductionReceipt receipt);
    }

    public ProductionReceiptAdapter(List<ProductionReceipt> receipts,
                                    OnReceiptClickListener clickListener,
                                    OnReceiptLongClickListener longClickListener) {
        this.receipts = receipts;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_production_receipt, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProductionReceipt receipt = receipts.get(position);
        holder.receiptNoText.setText("Fiş No: " + receipt.getReceiptNo());
        holder.dateText.setText("Tarih: " + receipt.getDate());
        holder.statusText.setText(receipt.getStatus());

        holder.itemView.setOnClickListener(v -> clickListener.onReceiptClick(receipt));
        holder.itemView.setOnLongClickListener(v -> longClickListener.onReceiptLongClick(receipt));
    }

    @Override
    public int getItemCount() {
        return receipts.size();
    }

    public void updateReceipts(List<ProductionReceipt> newReceipts) {
        this.receipts = newReceipts;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView receiptNoText;
        TextView dateText;
        TextView statusText;

        ViewHolder(View view) {
            super(view);
            receiptNoText = view.findViewById(R.id.receiptNoText);
            dateText = view.findViewById(R.id.dateText);
            statusText = view.findViewById(R.id.statusText);
        }
    }
}
