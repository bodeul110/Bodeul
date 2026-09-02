package com.example.bodeul.ui.booking;

import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

public final class BankTransferPaymentBinder {
    private final TextView statusText;
    private final TextView statusBodyText;
    private final TextView expectedAmountText;
    private final TextView dueAtText;
    private final TextView receivedAmountText;
    private final TextView confirmedAtText;
    private final TextView refundRequestedAtText;
    private final TextView refundedAtText;
    private final TextView currentDepositorText;
    private final TextView instructionText;
    private final TextInputLayout depositorInputLayout;
    private final MaterialButton saveButton;

    public BankTransferPaymentBinder(
            TextView statusText,
            TextView statusBodyText,
            TextView expectedAmountText,
            TextView dueAtText,
            TextView receivedAmountText,
            TextView confirmedAtText,
            TextView refundRequestedAtText,
            TextView refundedAtText,
            TextView currentDepositorText,
            TextView instructionText,
            TextInputLayout depositorInputLayout,
            MaterialButton saveButton
    ) {
        this.statusText = statusText;
        this.statusBodyText = statusBodyText;
        this.expectedAmountText = expectedAmountText;
        this.dueAtText = dueAtText;
        this.receivedAmountText = receivedAmountText;
        this.confirmedAtText = confirmedAtText;
        this.refundRequestedAtText = refundRequestedAtText;
        this.refundedAtText = refundedAtText;
        this.currentDepositorText = currentDepositorText;
        this.instructionText = instructionText;
        this.depositorInputLayout = depositorInputLayout;
        this.saveButton = saveButton;
    }

    public void bind(BankTransferPaymentScreenModel model) {
        statusText.setText(model.getStatusLabel());
        statusBodyText.setText(model.getStatusBody());
        expectedAmountText.setText(model.getExpectedAmount());
        dueAtText.setText(model.getPaymentDueAt());
        receivedAmountText.setText(model.getReceivedAmount());
        confirmedAtText.setText(model.getConfirmedAt());
        refundRequestedAtText.setText(model.getRefundRequestedAt());
        refundedAtText.setText(model.getRefundedAt());
        currentDepositorText.setText(model.getCurrentDepositorName());
        instructionText.setText(model.getInstructionNotice());
        depositorInputLayout.setHelperText(model.getDepositorHelper());
        depositorInputLayout.setEnabled(model.isDepositorEditable());
        saveButton.setEnabled(model.isDepositorEditable());
    }
}
