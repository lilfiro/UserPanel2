package com.example.userpanel2;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


public class QuantityDialogFragment extends DialogFragment {

    private static final String ARG_ITEM_NAME = "itemName";

    public static QuantityDialogFragment newInstance(String itemName) {
        QuantityDialogFragment fragment = new QuantityDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ITEM_NAME, itemName);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.dialog_quantity, container, false);

        // Get item name from arguments
        String itemName = getArguments().getString(ARG_ITEM_NAME);

        // Setup views and buttons here

        return rootView;
    }
}

