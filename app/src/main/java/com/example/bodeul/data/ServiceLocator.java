package com.example.bodeul.data;

import android.content.Context;

import com.example.bodeul.data.firebase.FirebaseAdminRepository;
import com.example.bodeul.data.firebase.FirebaseAuthRepository;
import com.example.bodeul.data.firebase.FirebaseBookingRepository;
import com.example.bodeul.data.firebase.FirebaseGuardianReportRepository;
import com.example.bodeul.data.firebase.FirebaseManagerDocumentStorageUploader;
import com.example.bodeul.data.firebase.FirebaseManagerRepository;
import com.example.bodeul.data.firebase.FirebaseSupport;
import com.example.bodeul.data.mock.MockAdminRepository;
import com.example.bodeul.data.mock.MockAuthRepository;
import com.example.bodeul.data.mock.MockBookingRepository;
import com.example.bodeul.data.mock.MockGuardianReportRepository;
import com.example.bodeul.data.mock.MockManagerDocumentStorageUploader;
import com.example.bodeul.data.mock.MockManagerRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;

/**
 * 실행 환경에 맞는 저장소 구현을 공급자에서 선택해 주입하는 단순 서비스 로케이터다.
 */
public final class ServiceLocator {
    // 앱 전역에서 같은 저장소 인스턴스를 재사용해 세션 상태를 유지한다.
    private static MockBodeulRepository mockBodeulRepository;
    private static AuthRepository authRepository;
    private static BookingRepository bookingRepository;
    private static GuardianReportRepository guardianReportRepository;
    private static ManagerRepository managerRepository;
    private static AdminRepository adminRepository;
    private static ManagerDocumentStorageUploader managerDocumentStorageUploader;
    private static FirebaseFirestore firestore;

    private ServiceLocator() {
    }

    public static synchronized AuthRepository provideAuthRepository(Context context) {
        if (authRepository == null) {
            // Firebase 설정이 있으면 실제 인증 저장소를, 없으면 데모 저장소를 사용한다.
            if (FirebaseSupport.isConfigured(context)) {
                authRepository = new FirebaseAuthRepository(
                        context.getApplicationContext(),
                        FirebaseAuth.getInstance(),
                        provideFirestore()
                );
            } else {
                authRepository = new MockAuthRepository(getMockBodeulRepository());
            }
        }
        return authRepository;
    }

    public static synchronized ManagerRepository provideManagerRepository(Context context) {
        if (managerRepository == null) {
            // 매니저 기능은 같은 기준으로 Firebase 또는 목업 구현을 선택한다.
            if (FirebaseSupport.isConfigured(context)) {
                managerRepository = new FirebaseManagerRepository(provideFirestore());
            } else {
                managerRepository = new MockManagerRepository(getMockBodeulRepository());
            }
        }
        return managerRepository;
    }

    public static synchronized ManagerDocumentStorageUploader provideManagerDocumentStorageUploader(Context context) {
        if (managerDocumentStorageUploader == null) {
            if (FirebaseSupport.isConfigured(context)) {
                managerDocumentStorageUploader =
                        new FirebaseManagerDocumentStorageUploader(context.getApplicationContext());
            } else {
                managerDocumentStorageUploader =
                        new MockManagerDocumentStorageUploader(context.getApplicationContext());
            }
        }
        return managerDocumentStorageUploader;
    }

    public static synchronized BookingRepository provideBookingRepository(Context context) {
        if (bookingRepository == null) {
            // 예약 화면은 같은 기준으로 Firebase 또는 목업 구현을 선택한다.
            if (FirebaseSupport.isConfigured(context)) {
                bookingRepository = new FirebaseBookingRepository(provideFirestore());
            } else {
                bookingRepository = new MockBookingRepository(getMockBodeulRepository());
            }
        }
        return bookingRepository;
    }

    public static synchronized GuardianReportRepository provideGuardianReportRepository(Context context) {
        if (guardianReportRepository == null) {
            // 보호자 진행 현황 화면은 같은 기준으로 Firebase 또는 목업 구현을 고른다.
            if (FirebaseSupport.isConfigured(context)) {
                guardianReportRepository = new FirebaseGuardianReportRepository(provideFirestore());
            } else {
                guardianReportRepository = new MockGuardianReportRepository(getMockBodeulRepository());
            }
        }
        return guardianReportRepository;
    }

    public static synchronized AdminRepository provideAdminRepository(Context context) {
        if (adminRepository == null) {
            // 관리자 운영 화면은 별도 저장소로 분리한다.
            if (FirebaseSupport.isConfigured(context)) {
                adminRepository = new FirebaseAdminRepository(provideFirestore());
            } else {
                adminRepository = new MockAdminRepository(getMockBodeulRepository());
            }
        }
        return adminRepository;
    }

    private static MockBodeulRepository getMockBodeulRepository() {
        // Firebase가 없을 때는 인증과 화면 기능이 같은 목업 데이터를 공유한다.
        if (mockBodeulRepository == null) {
            mockBodeulRepository = new MockBodeulRepository();
        }
        return mockBodeulRepository;
    }

    private static FirebaseFirestore provideFirestore() {
        if (firestore == null) {
            firestore = FirebaseFirestore.getInstance();

            // 앱 재시작 뒤 디스크에 남는 민감 정보 범위를 줄이기 위해 메모리 캐시만 사용한다.
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder(
                    firestore.getFirestoreSettings()
            )
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build();
            firestore.setFirestoreSettings(settings);
        }
        return firestore;
    }
}
