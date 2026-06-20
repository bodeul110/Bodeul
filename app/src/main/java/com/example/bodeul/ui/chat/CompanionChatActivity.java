package com.example.bodeul.ui.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.CompanionChatAttachmentPreviewResolver;
import com.example.bodeul.data.CompanionChatAttachmentUploadPolicy;
import com.example.bodeul.data.CompanionChatAttachmentUploader;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.firebase.CompanionChatPushContract;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.DocumentPreviewLauncher;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class CompanionChatActivity extends AppCompatActivity {
    private static final String EXTRA_REQUEST_ID = "requestId";

    private CompanionChatViewModel viewModel;
    private CompanionChatBinder binder;
    private CompanionChatAttachmentPreviewResolver attachmentPreviewResolver;
    private CompanionChatAttachmentThumbnailLoader attachmentThumbnailLoader;

    private View statePanel;
    private View contentContainer;
    private ProgressBar progressBar;
    private TextInputEditText inputMessage;
    private String requestId;
    private boolean chatRefreshReceiverRegistered;
    @Nullable
    private CompanionChatPendingAttachment pendingAttachment;

    private ActivityResultLauncher<String[]> attachmentPickerLauncher;

    private final BroadcastReceiver chatRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!shouldHandleChatRefresh(intent)) {
                return;
            }
            viewModel.reload();
            showChatMessageSnackbar(intent);
        }
    };

    public static Intent createIntent(Context context) {
        return new Intent(context, CompanionChatActivity.class);
    }

    public static Intent createIntent(Context context, String requestId) {
        Intent intent = new Intent(context, CompanionChatActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_companion_chat);

        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);

        AuthRepository authRepository = ServiceLocator.provideAuthRepository(this);
        BookingRepository bookingRepository = ServiceLocator.provideBookingRepository(this);
        ManagerRepository managerRepository = ServiceLocator.provideManagerRepository(this);
        CompanionChatAttachmentUploader attachmentUploader =
                ServiceLocator.provideCompanionChatAttachmentUploader(this);
        attachmentPreviewResolver = ServiceLocator.provideCompanionChatAttachmentPreviewResolver(this);
        attachmentThumbnailLoader = new CompanionChatAttachmentThumbnailLoader(this);
        CompanionChatCoordinator coordinator = new CompanionChatCoordinator(this);

        CompanionChatViewModel.Factory factory = new CompanionChatViewModel.Factory(
                authRepository,
                bookingRepository,
                managerRepository,
                attachmentUploader,
                coordinator
        );
        viewModel = new ViewModelProvider(this, factory).get(CompanionChatViewModel.class);

        attachmentPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleAttachmentPicked
        );

        statePanel = findViewById(R.id.companionChatStatePanel);
        contentContainer = findViewById(R.id.companionChatContentContainer);
        progressBar = findViewById(R.id.progressCompanionChat);
        inputMessage = findViewById(R.id.inputCompanionChatMessage);

        binder = new CompanionChatBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textCompanionChatMode),
                findViewById(R.id.textCompanionChatTitle),
                findViewById(R.id.textCompanionChatSubtitle),
                findViewById(R.id.textCompanionChatHeroBadge),
                findViewById(R.id.textCompanionChatHeroTitle),
                findViewById(R.id.textCompanionChatHeroBody),
                findViewById(R.id.textCompanionChatSectionTitle),
                findViewById(R.id.textCompanionChatEmptyBody),
                (LinearLayout) findViewById(R.id.layoutCompanionChatMessages),
                (TextInputLayout) findViewById(R.id.layoutCompanionChatInput),
                (MaterialButton) findViewById(R.id.buttonCompanionChatSend),
                findViewById(R.id.layoutCompanionChatPendingAttachment),
                findViewById(R.id.imageCompanionChatPendingAttachmentPreview),
                findViewById(R.id.textCompanionChatPendingAttachmentTitle),
                findViewById(R.id.textCompanionChatPendingAttachmentMeta),
                findViewById(R.id.buttonCompanionChatAttachmentPreview),
                findViewById(R.id.buttonCompanionChatAttachmentClear),
                this::bindPendingAttachmentThumbnail,
                this::bindMessageAttachmentThumbnail,
                this::openMessageAttachment
        );

        findViewById(R.id.buttonBackCompanionChat).setOnClickListener(view -> finish());
        findViewById(R.id.buttonCompanionChatSend).setOnClickListener(view -> sendMessage());
        findViewById(R.id.buttonCompanionChatAttach).setOnClickListener(view -> openAttachmentPicker());
        contentContainer.setVisibility(View.GONE);

        viewModel.getUiState().observe(this, this::handleUiState);
        viewModel.getToastMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                viewModel.toastMessageHandled();
            }
        });
        viewModel.getMessageSentEvent().observe(this, eventMillis -> {
            if (eventMillis == null) {
                return;
            }
            inputMessage.setText("");
            clearPendingAttachment();
            viewModel.messageSentHandled();
        });

        if (savedInstanceState == null) {
            viewModel.init(requestId);
        }
        refreshPendingAttachmentUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerChatRefreshReceiver();
    }

    @Override
    protected void onStop() {
        unregisterChatRefreshReceiver();
        super.onStop();
    }

    private void handleUiState(CompanionChatViewModel.UiState state) {
        if (state == null) {
            return;
        }

        progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);

        if (state.requireProfileCompletion) {
            openProfileCompletion();
            return;
        }

        if (state.statePanelType != CompanionChatViewModel.StatePanelType.NONE) {
            contentContainer.setVisibility(View.GONE);
            switch (state.statePanelType) {
                case PERMISSION:
                    showPermissionState();
                    break;
                case AUTH:
                    showAuthState();
                    break;
                case EMPTY:
                    showEmptyState();
                    break;
                case LOAD_ERROR:
                    showLoadErrorState(state.errorMessage);
                    break;
                default:
                    StatePanelHelper.hide(statePanel);
                    break;
            }
        } else {
            StatePanelHelper.hide(statePanel);
            if (state.screenModel != null) {
                contentContainer.setVisibility(View.VISIBLE);
                binder.bindScreen(state.screenModel);
                refreshPendingAttachmentUi();
            } else {
                contentContainer.setVisibility(View.GONE);
            }
        }
    }

    private void sendMessage() {
        String message = inputMessage.getText() == null ? "" : inputMessage.getText().toString();
        viewModel.sendMessage(message, pendingAttachment);
    }

    private void openAttachmentPicker() {
        attachmentPickerLauncher.launch(new String[]{"application/pdf", "image/*"});
    }

    private void handleAttachmentPicked(@Nullable Uri fileUri) {
        if (fileUri == null) {
            return;
        }

        persistAttachmentReadPermission(fileUri);
        String validationError = CompanionChatAttachmentUploadPolicy.validate(
                getContentResolver(),
                fileUri
        );
        if (!TextUtils.isEmpty(validationError)) {
            Toast.makeText(this, validationError, Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = CompanionChatAttachmentUploadPolicy.resolveFileName(
                getContentResolver(),
                fileUri
        );
        String contentType = CompanionChatAttachmentUploadPolicy.resolveContentType(
                getContentResolver(),
                fileUri
        );
        pendingAttachment = new CompanionChatPendingAttachment(fileUri, fileName, contentType);
        refreshPendingAttachmentUi();
    }

    private void persistAttachmentReadPermission(@Nullable Uri fileUri) {
        if (fileUri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    fileUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
            // 일부 공급자는 현재 세션 읽기만 허용한다.
        }
    }

    private void refreshPendingAttachmentUi() {
        binder.bindPendingAttachment(
                pendingAttachment,
                pendingAttachment == null ? null : view -> previewPendingAttachment(),
                pendingAttachment == null ? null : view -> clearPendingAttachment()
        );
    }

    private void previewPendingAttachment() {
        if (pendingAttachment == null) {
            return;
        }
        if (!DocumentPreviewLauncher.open(
                this,
                pendingAttachment.getFileUri(),
                pendingAttachment.getContentType()
        )) {
            Toast.makeText(
                    this,
                    R.string.companion_chat_attachment_preview_error,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void clearPendingAttachment() {
        pendingAttachment = null;
        refreshPendingAttachmentUi();
    }

    private void bindPendingAttachmentThumbnail(
            ImageView imageView,
            CompanionChatPendingAttachment attachment
    ) {
        attachmentThumbnailLoader.loadInto(imageView, attachment.getFileUri());
    }

    private void bindMessageAttachmentThumbnail(
            ImageView imageView,
            CompanionChatAttachment attachment
    ) {
        if (!attachment.isImageType()) {
            attachmentThumbnailLoader.clear(imageView);
            return;
        }

        if (!TextUtils.isEmpty(attachment.getPreviewUri())) {
            attachmentThumbnailLoader.loadInto(imageView, Uri.parse(attachment.getPreviewUri()));
            return;
        }

        attachmentThumbnailLoader.clear(imageView);
        attachmentPreviewResolver.resolvePreviewUri(attachment, new RepositoryCallback<Uri>() {
            @Override
            public void onSuccess(Uri previewUri) {
                attachmentThumbnailLoader.loadInto(imageView, previewUri);
            }

            @Override
            public void onError(String message) {
                attachmentThumbnailLoader.clear(imageView);
            }
        });
    }

    private void openMessageAttachment(CompanionChatAttachment attachment) {
        attachmentPreviewResolver.resolvePreviewUri(attachment, new RepositoryCallback<Uri>() {
            @Override
            public void onSuccess(Uri previewUri) {
                if (!DocumentPreviewLauncher.open(
                        CompanionChatActivity.this,
                        previewUri,
                        attachment.getContentType()
                )) {
                    Toast.makeText(
                            CompanionChatActivity.this,
                            R.string.companion_chat_attachment_preview_error,
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(CompanionChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.companion_chat_title)),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_home),
                view -> openHome(),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection()
        );
    }

    private void showAuthState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth),
                getString(R.string.state_auth_title),
                getString(R.string.state_auth_body),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection(),
                null,
                null
        );
    }

    private void showEmptyState() {
        showBlockingState(
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                getString(R.string.companion_chat_empty_title),
                getString(R.string.companion_chat_empty_session_body),
                getString(R.string.state_action_open_home),
                view -> openHome(),
                null,
                null
        );
    }

    private void showLoadErrorState(String message) {
        String body = getString(R.string.state_load_error_body);
        if (!TextUtils.isEmpty(message)) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.state_load_error_title, getString(R.string.companion_chat_title)),
                body,
                getString(R.string.state_action_retry),
                view -> viewModel.reload(),
                getString(R.string.state_action_open_home),
                view -> openHome()
        );
    }

    private void showBlockingState(
            StatePanelHelper.Tone tone,
            CharSequence badge,
            CharSequence title,
            CharSequence body,
            @Nullable CharSequence primaryText,
            @Nullable View.OnClickListener primaryListener,
            @Nullable CharSequence secondaryText,
            @Nullable View.OnClickListener secondaryListener
    ) {
        StatePanelHelper.show(
                statePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        contentContainer.setVisibility(View.GONE);
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void openRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openProfileCompletion() {
        Intent intent = ProfileCompletionActivity.createIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void registerChatRefreshReceiver() {
        if (chatRefreshReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(CompanionChatPushContract.ACTION_COMPANION_CHAT_UPDATED);
        ContextCompat.registerReceiver(
                this,
                chatRefreshReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        chatRefreshReceiverRegistered = true;
    }

    private void unregisterChatRefreshReceiver() {
        if (!chatRefreshReceiverRegistered) {
            return;
        }
        unregisterReceiver(chatRefreshReceiver);
        chatRefreshReceiverRegistered = false;
    }

    private boolean shouldHandleChatRefresh(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }
        String updatedRequestId = intent.getStringExtra(
                CompanionChatPushContract.EXTRA_APPOINTMENT_REQUEST_ID
        );
        return TextUtils.isEmpty(requestId)
                || TextUtils.isEmpty(updatedRequestId)
                || TextUtils.equals(requestId, updatedRequestId);
    }

    private void showChatMessageSnackbar(@Nullable Intent intent) {
        String message = getString(R.string.companion_chat_push_body_fallback);
        if (intent != null) {
            String payloadBody = intent.getStringExtra(CompanionChatPushContract.EXTRA_BODY);
            if (!TextUtils.isEmpty(payloadBody)) {
                message = payloadBody.trim();
            }
        }
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }
}
