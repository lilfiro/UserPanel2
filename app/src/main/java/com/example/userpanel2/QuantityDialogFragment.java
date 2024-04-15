package com.example.userpanel2;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.io.Serializable;
import java.util.Map;

public class QuantityDialogFragment extends DialogFragment {

    private static final String ARG_ITEM_NAME = "itemName";
    private static final String ARG_SELECTED_ITEMS_MAP = "selectedItemsMap";

    private String itemName;
    private Map<String, Integer> selectedItemsMap;
    private QuantityDialogListener quantityDialogListener;

    public static QuantityDialogFragment newInstance(String itemName, Map<String, Integer> selectedItemsMap) {
        QuantityDialogFragment fragment = new QuantityDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ITEM_NAME, itemName);
        args.putSerializable(ARG_SELECTED_ITEMS_MAP, (Serializable) selectedItemsMap);
        fragment.setArguments(args);
        return fragment;
    }

    public void setQuantityDialogListener(QuantityDialogListener listener) {
        this.quantityDialogListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.dialog_quantity, container, false);

        // Get item name and selected items map from arguments
        if (getArguments() != null) {
            itemName = getArguments().getString(ARG_ITEM_NAME);
            selectedItemsMap = (Map<String, Integer>) getArguments().getSerializable(ARG_SELECTED_ITEMS_MAP);
        }

        // Setup views and buttons
        EditText editTextQuantity = rootView.findViewById(R.id.editTextQuantity);
        Button buttonConfirm = rootView.findViewById(R.id.buttonConfirm);
        Button buttonCancel = rootView.findViewById(R.id.buttonCancel);

        buttonConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Retrieve the quantity entered by the user
                String quantityString = editTextQuantity.getText().toString();
                int quantity = Integer.parseInt(quantityString);

                // Pass the quantity back to the calling fragment
                if (quantityDialogListener != null) {
                    quantityDialogListener.onQuantityConfirmed(itemName, quantity);
                }

                dismiss(); // Dismiss the dialog
            }
        });

        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss(); // Dismiss the dialog
            }
        });

        return rootView;
    }

    public interface QuantityDialogListener {
        void onQuantityConfirmed(String itemName, int quantity);
    }
}
