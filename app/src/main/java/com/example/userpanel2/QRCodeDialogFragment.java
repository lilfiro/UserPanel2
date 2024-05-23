package com.example.userpanel2;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class QRCodeDialogFragment extends Fragment {

    private static final String ARG_ITEM_NAME = "item_name";
    private static final String ARG_ITEM_QUANTITY = "item_quantity";
    private static final String ARG_ITEM_DESCRIPTION = "item_description";

    private EditText editTextItemName;
    private EditText editTextItemQuantity;
    private EditText editTextItemDescription;
    private Button buttonConfirm;
    private Button buttonCancel;

    public static QRCodeDialogFragment newInstance(String itemName, int itemQuantity, String itemDescription) {
        QRCodeDialogFragment fragment = new QRCodeDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ITEM_NAME, itemName);
        args.putInt(ARG_ITEM_QUANTITY, itemQuantity);
        args.putString(ARG_ITEM_DESCRIPTION, itemDescription);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_qr_code_dialog, container, false);

        Log.d("QRCodeDialogFragment", "Creating QRCodeDialogFragment");

        editTextItemName = view.findViewById(R.id.editTextItemName);
        editTextItemQuantity = view.findViewById(R.id.editTextItemQuantity);
        editTextItemDescription = view.findViewById(R.id.editTextItemDescription);
        buttonConfirm = view.findViewById(R.id.buttonConfirm);
        buttonCancel = view.findViewById(R.id.buttonCancel);

        if (getArguments() != null) {
            editTextItemName.setText(getArguments().getString(ARG_ITEM_NAME));
            editTextItemQuantity.setText(String.valueOf(getArguments().getInt(ARG_ITEM_QUANTITY)));
            editTextItemDescription.setText(getArguments().getString(ARG_ITEM_DESCRIPTION));
        }

        buttonConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Pass data back to the FragmentIslem
                if (getParentFragment() instanceof FragmentIslem) {
                    FragmentIslem parentFragment = (FragmentIslem) getParentFragment();
                    parentFragment.onQRCodeResultConfirmed(
                            editTextItemName.getText().toString(),
                            Integer.parseInt(editTextItemQuantity.getText().toString()),
                            editTextItemDescription.getText().toString()
                    );
                }
                // Close the popup
                requireActivity().getSupportFragmentManager().beginTransaction().remove(QRCodeDialogFragment.this).commit();
            }
        });

        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Close the popup
                requireActivity().getSupportFragmentManager().beginTransaction().remove(QRCodeDialogFragment.this).commit();
            }
        });

        return view;
    }
}
