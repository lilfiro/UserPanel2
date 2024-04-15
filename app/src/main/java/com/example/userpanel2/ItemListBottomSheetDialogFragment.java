package com.example.userpanel2;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;


public class ItemListBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private ItemListAdapter adapter;
    private static final String ARG_ITEM_LIST = "itemList";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.item_list_bottom_sheet, container, false);

        // Initialize RecyclerView
        recyclerView = rootView.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Retrieve item list from arguments
        List<String> itemList = getArguments().getStringArrayList(ARG_ITEM_LIST);

        // Set up RecyclerView adapter
        adapter = new ItemListAdapter(getContext(), itemList, new ItemListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String itemName) {
                // Show quantity popup
                // Pass selectedItemsMap to the method
                showQuantityPopup(itemName, null); // Replace null with your selectedItemsMap
            }

        });

        recyclerView.setAdapter(adapter);

        return rootView;
    }

    public static ItemListBottomSheetDialogFragment newInstance(List<String> itemList) {
        ItemListBottomSheetDialogFragment fragment = new ItemListBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_ITEM_LIST, new ArrayList<>(itemList));
        fragment.setArguments(args);
        return fragment;
    }

    // Update the method signature to accept a Map<String, Integer> parameter
    public void showQuantityPopup(String itemName, Map<String, Integer> selectedItemsMap) {
        QuantityDialogFragment quantityDialogFragment = QuantityDialogFragment.newInstance(itemName, selectedItemsMap);
        quantityDialogFragment.show(getChildFragmentManager(), "quantity_dialog");
    }

}
