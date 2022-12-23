package com.bvtech.hidduplicator;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bvtech.hidduplicator.databinding.DialogConfirmBinding;
import com.bvtech.toolslibrary.utility.ViewUtility;
/**
 * DialogConfirm is an extended class from Dialog class
 * This class create a customized dialog of DialogConfirm
 */
public class DialogConfirm extends Dialog {

    /**
     * OnConfirmationListener is a specific interface for DialogConfirm
     * it handle both "Confirm" and "Cancel" events
     */
    public interface OnConfirmationListener{
        void onConfirm();
        void onCancel();
    }

    /**
     * Defining Views and Variables
     */
    private DialogConfirmBinding binding;
    private OnConfirmationListener onConfirmationListener;
    //private View view;
    private String header, title;
    //private TextView txtHeader, txtTitle;
    //private Button btnConfirm, btnCancel;

    /**
     * Default constructor with a context in input
     */
    public DialogConfirm(@NonNull Context context) {
        super(context);
        init();
    }

    /**
     *
     * Default constructor context and themeResId
     */
    public DialogConfirm(@NonNull Context context, int themeResId) {
        super(context, themeResId);
        init();
    }

    /**
     * Default constructor context, cancelable and cancelListener inputs
     */
    protected DialogConfirm(@NonNull Context context, boolean cancelable, @Nullable OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
        init();
    }

    /**
     *
     * Customized constructor with context, header and title as inputs
     * header is the text value of header in the dialog
     * title is the message of dialog
     */
    public DialogConfirm(@NonNull Context context, @NonNull String header, @NonNull String title) {
        super(context);
        this.header = header;
        this.title = title;
        init();
    }

    /**
     *
     * Customized constructor with context, header, title and onConfirmationListener as inputs
     * header is the text value of header in the dialog
     * title is the message of dialog
     * onConfirmationListener is an interface to handle both "Confirm" and "Cancel" events
     */
    public DialogConfirm(@NonNull Context context, @NonNull String header, @NonNull String title, @NonNull OnConfirmationListener onConfirmationListener) {
        super(context);
        this.header = header;
        this.title = title;
        this.onConfirmationListener = onConfirmationListener;
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

        binding.txtDialogHeader.setText(header);
        binding.txtDialogBody.setText(title);

        //Attach click event of the "Confirm" button
        ViewUtility.shrinkExpandAnimation(binding.btnConfirm, 0.98f, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogConfirm.this.dismiss();
                if(onConfirmationListener != null){
                    onConfirmationListener.onConfirm();
                }
            }
        });

        //Attach click event of the "Cancel" button
        ViewUtility.shrinkExpandAnimation(binding.btnCancel, 0.98f, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogConfirm.this.dismiss();
                if(onConfirmationListener != null){
                    onConfirmationListener.onCancel();
                }
            }
        });

        DialogConfirm.this.setCancelable(false);
    }

    public void setOnConfirmationListener(OnConfirmationListener onConfirmationListener){
        this.onConfirmationListener = onConfirmationListener;
    }
}
