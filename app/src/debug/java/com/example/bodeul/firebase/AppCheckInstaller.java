package com.example.bodeul.firebase;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

/**
 * 개발 환경에서는 Debug provider로 App Check 토큰을 발급한다.
 */
public final class AppCheckInstaller {
    private AppCheckInstaller() {
    }

    public static void installIfConfigured(Application application) {
        FirebaseApp firebaseApp = FirebaseApp.initializeApp(application);
        if (firebaseApp == null) {
            return;
        }

        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
        );
        firebaseAppCheck.setTokenAutoRefreshEnabled(true);
    }
}
