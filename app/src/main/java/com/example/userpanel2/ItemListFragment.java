package com.example.userpanel2;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.List;

public class ItemListFragment extends Fragment {

    private List<String> itemList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.receipt_item_list, container, false);

        // Get the item list data passed from the previous fragment
        if (getArguments() != null) {
            itemList = getArguments().getStringArrayList("itemList");
        }

        // Populate ListView with item list
        ListView listView = rootView.findViewById(R.id.itemListView);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, itemList);
        listView.setAdapter(adapter);

        return rootView;
    }
}
