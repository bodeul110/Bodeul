package com.example.bodeul.ui.manager;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.example.bodeul.R;

/**
 * 매니저 자격 인증 요청이 저장된 뒤 Figma 완료 상태를 표시한다.
 */
public final class ManagerQualificationCompletionDialog {
    private ManagerQualificationCompletionDialog() {
    }

    public static void show(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_manager_qualification_complete);
        dialog.setCancelable(false);
        dialog.findViewById(R.id.buttonManagerQualificationCompleteConfirm)
                .setOnClickListener(view -> dialog.dismiss());

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.55f;
            window.setAttributes(attributes);
        }

        dialog.show();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}
