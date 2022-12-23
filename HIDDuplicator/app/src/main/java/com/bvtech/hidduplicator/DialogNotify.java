package com.bvtech.hidduplicator;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bvtech.hidduplicator.databinding.DialogConfirmBinding;
import com.bvtech.toolslibrary.utility.ViewUtility;

/**
 * DialogConfirm is an extended class from Dialog class
 * This class create a customized dialog of DialogNotify
 * This dialog only shows notifications
 */
public class DialogNotify extends Dialog {

    /**
     * OnConfirmListener is a specific interface for DialogNotify
     * it handle "onClick" event
     */
    public interface OnConfirmListener{
        void onClick();
    }

    /**
     * Defining Views and Variables
     */
    //private View view;
    private DialogConfirmBinding binding;
    private String header, title;
    //private TextView txtHeader, txtTitle;
    //private Button btnConfirm, btnCancel;
    private OnConfirmListener onConfirmListener;

    /**
     *
     * Customized constructor with context, header and title as inputs
     * @param context contains data of foreground activity
     * @param header is the text value of header in the dialog
     * @param title is the message of dialog
     */
    public DialogNotify(@NonNull Context context, @NonNull String header, @NonNull String title) {
        super(context);
        this.header = header;
        this.title = title;
        init();
    }

    /**
     *
     * Customized constructor with context, header and title as inputs
     * @param context contains data of foreground activity
     * @param header is the text value of header in the dialog
     * @param title is the message of dialog
     * @param onConfirmListener is an interface to handle "Click" event of button in the dialog
     */
    public DialogNotify(@NonNull Context context, @NonNull String header, @NonNull String title, OnConfirmListener onConfirmListener) {
        super(context);
        this.header = header;
        this.title = title;
        this.onConfirmListener = onConfirmListener;
        init();
    }

    /**
     * This method initializes the views and attaches the specific layout to the dialog
     */
    private void init(){
        //view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm, null);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(binding.getRoot());
        getWindow().setBackgroundDrawableResource(R.drawable.transparent_background);

//        txtDialogBody = view.findViewById(R.id.txtDialogBody);
//        txtDialogHeader = view.findViewById(R.id.txtDialogHeader);
//        btnConfirm = view.findViewById(R.id.btnConfirm);
//        btnCancel = view.findViewById(R.id.btnCancel);

        binding.btnConfirm.setText("Ok");
        binding.btnCancel.setVisibility(View.GONE);
        binding.txtDialogHeader.setText(header);
        binding.txtDialogBody.setText(title);

        //Attach click event of the "Confirm" button
        ViewUtility.shrinkExpandAnimation(binding.btnConfirm, 0.98f, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogNotify.this.dismiss();
                if(onConfirmListener != null){
                    onConfirmListener.onClick();
                }
            }
        });
    }
}
