package com.example.bodeul.data.firebase;

import android.content.Context;

import com.google.firebase.FirebaseApp;

import java.util.List;

/**
 * Firebase 초기화 가능 여부를 안전하게 확인하는 보조 클래스다.
 */
public final class FirebaseSupport {
    private FirebaseSupport() {
    }

    public static boolean isConfigured(Context context) {
        try {
            // google-services 설정이 없는 환경에서도 앱이 죽지 않도록 안전하게 판별한다.
            List<FirebaseApp> apps = FirebaseApp.getApps(context);
            if (!apps.isEmpty()) {
                // 이미 초기화된 FirebaseApp이 있으면 바로 Firebase 모드로 판단한다.
                return true;
            }
            // 아직 초기화되지 않았다면 한 번만 초기화를 시도해 설정 존재 여부를 확인한다.
            return FirebaseApp.initializeApp(context) != null;
        } catch (Exception exception) {
            // 설정 파일 누락, 초기화 실패 같은 경우에는 목업 모드로 안전하게 내려간다.
            return false;
        }
    }
}
