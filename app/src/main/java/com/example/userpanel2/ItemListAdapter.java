package com.example.userpanel2;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ItemListAdapter extends RecyclerView.Adapter<ItemListAdapter.ItemViewHolder> {

    private List<String> itemList;
    private OnItemClickListener listener;
    private Context context;

    public ItemListAdapter(Context context, List<String> itemList, OnItemClickListener listener) {
        this.context = context;
        this.itemList = itemList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ItemViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        final String itemName = itemList.get(position);
        holder.bind(itemName, listener);
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public class ItemViewHolder extends RecyclerView.ViewHolder {

        private TextView itemNameTextView;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemNameTextView = itemView.findViewById(android.R.id.text1);
        }

        public void bind(final String itemName, final OnItemClickListener listener) {
            itemNameTextView.setText(itemName);

            // Set click listener for the item view
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Pass the selected item name to the click listener
                    listener.onItemClick(itemName);
                }
            });
        }
    }

    // Define an interface for item click listener
    public interface OnItemClickListener {
        void onItemClick(String itemName);
    }
}

