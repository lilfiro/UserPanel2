package com.example.A_Soft;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class QRCodeDialogFragment extends DialogFragment {

    private static final String ARG_NAME = "name";
    private static final String ARG_QUANTITY = "quantity";
    private static final String ARG_DESCRIPTION = "description";

    private EditText editTextItemName, editTextItemQuantity, editTextItemDescription;
    private Button buttonConfirm, buttonCancel;

    public static QRCodeDialogFragment newInstance(String name, int quantity, String description) {
        QRCodeDialogFragment fragment = new QRCodeDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        args.putInt(ARG_QUANTITY, quantity);
        args.putString(ARG_DESCRIPTION, description);
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
            String name = getArguments().getString(ARG_NAME);
            int quantity = getArguments().getInt(ARG_QUANTITY);
            String description = getArguments().getString(ARG_DESCRIPTION);

            editTextItemName.setText(name);
            editTextItemQuantity.setText(String.valueOf(quantity));
            editTextItemDescription.setText(description);
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
                // Close the dialog
                dismiss();
            }
        });

        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Close the dialog
                dismiss();
            }
        });

        return view;
    }
}
