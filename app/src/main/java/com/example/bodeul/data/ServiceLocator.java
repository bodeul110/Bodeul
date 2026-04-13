package com.example.bodeul.data;

import android.content.Context;

import com.example.bodeul.data.firebase.FirebaseAuthRepository;
import com.example.bodeul.data.firebase.FirebaseManagerRepository;
import com.example.bodeul.data.firebase.FirebaseSupport;
import com.example.bodeul.data.mock.MockAuthRepository;
import com.example.bodeul.data.mock.MockManagerRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * 실행 환경에 맞는 저장소 구현을 한 곳에서 선택해 주입하는 단순 서비스 로케이터다.
 */
public final class ServiceLocator {
    // 앱 전역에서 같은 저장소 인스턴스를 재사용해 세션 상태를 유지한다.
    private static MockBodeulRepository mockBodeulRepository;
    private static AuthRepository authRepository;
    private static ManagerRepository managerRepository;

    private ServiceLocator() {
    }

    public static synchronized AuthRepository provideAuthRepository(Context context) {
        if (authRepository == null) {
            // Firebase 설정이 있으면 실제 인증 저장소를, 없으면 데모 저장소를 사용한다.
            if (FirebaseSupport.isConfigured(context)) {
                authRepository = new FirebaseAuthRepository(
                        FirebaseAuth.getInstance(),
                        FirebaseFirestore.getInstance()
                );
            } else {
                authRepository = new MockAuthRepository(getMockBodeulRepository());
            }
        }
        return authRepository;
    }

    public static synchronized ManagerRepository provideManagerRepository(Context context) {
        if (managerRepository == null) {
            // 매니저 기능도 같은 기준으로 Firebase 또는 목업 구현을 선택한다.
            if (FirebaseSupport.isConfigured(context)) {
                managerRepository = new FirebaseManagerRepository(FirebaseFirestore.getInstance());
            } else {
                managerRepository = new MockManagerRepository(getMockBodeulRepository());
            }
        }
        return managerRepository;
    }

    private static MockBodeulRepository getMockBodeulRepository() {
        // Firebase가 없을 때는 인증/매니저 기능이 같은 목업 데이터 원본을 공유한다.
        if (mockBodeulRepository == null) {
            mockBodeulRepository = new MockBodeulRepository();
        }
        return mockBodeulRepository;
    }
}
