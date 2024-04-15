package com.example.userpanel2;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.List;
import java.util.ArrayList;


public class ItemListBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private ItemListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.item_list_bottom_sheet, container, false);

        // Initialize RecyclerView
        recyclerView = rootView.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Retrieve item list from arguments
        List<String> itemList = getArguments().getStringArrayList("itemList");

        // Set up RecyclerView adapter
        adapter = new ItemListAdapter(getContext(), itemList, new ItemListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String itemName) {
                // Show quantity popup
                showQuantityPopup(itemName);
            }

        });

        recyclerView.setAdapter(adapter);

        return rootView;
    }
    public void showQuantityPopup(String itemName) {
        QuantityDialogFragment quantityDialogFragment = QuantityDialogFragment.newInstance(itemName);
        quantityDialogFragment.show(getChildFragmentManager(), quantityDialogFragment.getTag());
    }

}

