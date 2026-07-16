package com.example.bodeul;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import android.util.Log;

import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.firebase.AppCheckInstaller;
import com.example.bodeul.util.AppActivityTracker;
import com.kakao.sdk.common.KakaoSdk;
import com.kakao.vectormap.KakaoMapSdk;

/**
 * 앱 전역 SDK 초기화를 담당하는 Application 클래스다.
 */
public class BodeulApplication extends Application {
    private static final String TAG = "BodeulApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // 카카오 로그인은 앱 시작 시 네이티브 앱 키로 SDK를 초기화해야 한다.
        String kakaoNativeAppKey = getString(R.string.kakao_native_app_key);
        if (!TextUtils.isEmpty(kakaoNativeAppKey)) {
            KakaoSdk.init(this, kakaoNativeAppKey);
            initializeKakaoMap(kakaoNativeAppKey);
        }

        // Firebase App Check는 디버그/릴리스 변형에 맞는 제공자를 설치한다.
        AppCheckInstaller.installIfConfigured(this);
        AppActivityTracker.install(this);

        // 네이버 로그인은 클라이언트 시크릿을 앱에 포함하지 않도록 비활성화한 상태다.
        ServiceLocator.provideNotificationTokenRegistrar(this).syncCurrentUserToken();
    }

    private void initializeKakaoMap(String kakaoNativeAppKey) {
        try {
            KakaoMapSdk.init(this, kakaoNativeAppKey);
        } catch (UnsatisfiedLinkError error) {
            boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            if (!debuggable) {
                throw error;
            }
            Log.w(TAG, "현재 에뮬레이터 ABI에서 Kakao Map을 지원하지 않아 지도 SDK 초기화를 건너뜁니다.");
        }
    }
}
