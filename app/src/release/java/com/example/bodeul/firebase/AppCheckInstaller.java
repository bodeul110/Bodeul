package com.example.bodeul.firebase;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/**
 * 릴리스 환경에서는 Play Integrity provider로 App Check를 설치한다.
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
                PlayIntegrityAppCheckProviderFactory.getInstance()
        );
        firebaseAppCheck.setTokenAutoRefreshEnabled(true);
    }
}
