package com.example.bodeul.ui.manager;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.bodeul.R;

/**
 * 매니저 자격 인증 요청이 저장된 뒤 Figma 완료 상태를 표시한다.
 */
public final class ManagerQualificationCompletionDialog extends DialogFragment {
    static final String TAG = "manager_qualification_completion";
    static final String RESULT_KEY = "manager_qualification_completion_confirmed";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_manager_qualification_complete);
        setCancelable(false);
        dialog.findViewById(R.id.buttonManagerQualificationCompleteConfirm)
                .setOnClickListener(view -> {
                    getParentFragmentManager().setFragmentResult(RESULT_KEY, Bundle.EMPTY);
                    dismiss();
                });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.55f;
            window.setAttributes(attributes);
        }

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}
