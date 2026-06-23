package com.example.bodeul.ui.auth;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.util.NotificationPermissionSupport;

/**
 * 앱 첫 실행 시 어떤 화면으로 보낼지 결정하는 진입 흐름 조정자다.
 */
public final class EntryFlowCoordinator {
    /**
     * 계산된 다음 화면 Intent를 호출자에게 전달한다.
     */
    public interface Callback {
        void onResolved(@NonNull Intent intent);
    }

    private final Context appContext;
    private final AuthRepository authRepository;
    private final PermissionGuidePreferences permissionGuidePreferences;

    public EntryFlowCoordinator(Context context) {
        this(
                context.getApplicationContext(),
                ServiceLocator.provideAuthRepository(context),
                new PermissionGuidePreferences(context)
        );
    }

    EntryFlowCoordinator(
            Context appContext,
            AuthRepository authRepository,
            PermissionGuidePreferences permissionGuidePreferences
    ) {
        this.appContext = appContext;
        this.authRepository = authRepository;
        this.permissionGuidePreferences = permissionGuidePreferences;
    }

    public void resolveLaunchIntent(@NonNull Callback callback) {
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                callback.onResolved(wrapWithPermissionGuide(
                        AuthFlowRouter.createPostAuthIntent(appContext, result)
                ));
            }

            @Override
            public void onError(String message) {
                callback.onResolved(createSignedOutIntent());
            }
        });
    }

    private Intent createSignedOutIntent() {
        return wrapWithPermissionGuide(new Intent(appContext, RoleSelectionActivity.class));
    }

    private Intent wrapWithPermissionGuide(@NonNull Intent nextIntent) {
        if (!shouldShowPermissionGuide()) {
            return nextIntent;
        }
        return PermissionGuideActivity.createIntent(appContext, nextIntent);
    }

    private boolean shouldShowPermissionGuide() {
        if (!permissionGuidePreferences.hasCompletedGuide()) {
            return true;
        }
        if (NotificationPermissionSupport.canPostNotifications(appContext)) {
            return false;
        }
        if (permissionGuidePreferences.hasCompletedNotificationPrompt()) {
            permissionGuidePreferences.markNotificationPromptPending();
        }
        return true;
    }
}
