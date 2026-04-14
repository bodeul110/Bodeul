package com.example.bodeul;

import android.app.Application;
import android.text.TextUtils;

import com.kakao.sdk.common.KakaoSdk;
import com.navercorp.nid.NaverIdLoginSDK;

/**
 * 앱 전역 SDK 초기화를 담당하는 Application 클래스다.
 */
public class BodeulApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 카카오 로그인은 앱 시작 시 네이티브 앱 키로 SDK를 초기화해야 한다.
        String kakaoNativeAppKey = getString(R.string.kakao_native_app_key);
        if (!TextUtils.isEmpty(kakaoNativeAppKey)) {
            KakaoSdk.init(this, kakaoNativeAppKey);
        }

        // 네이버 로그인은 앱 시작 시 클라이언트 정보로 SDK를 초기화해야 한다.
        String naverClientId = getString(R.string.naver_client_id);
        String naverClientSecret = getString(R.string.naver_client_secret);
        String naverClientName = getString(R.string.naver_client_name);
        if (!TextUtils.isEmpty(naverClientId)
                && !TextUtils.isEmpty(naverClientSecret)
                && !TextUtils.isEmpty(naverClientName)) {
            NaverIdLoginSDK.INSTANCE.initialize(this, naverClientId, naverClientSecret, naverClientName);
        }
    }
}
