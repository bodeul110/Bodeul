package com.bodeul.core.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.bodeul.core.auth.AppUserRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * PostgreSQL 커밋 뒤 Firebase 토큰 저장소를 읽어 민감 내용 없는 FCM 보조 알림을 보낸다.
 */
@Component
@Profile("database")
@ConditionalOnProperty(name = "FIREBASE_PROJECT_ID")
class FirebaseCompanionNotificationListener {

    private static final String FIREBASE_APP_NAME = "bodeul-core-notification";
    private static final Logger LOGGER = LoggerFactory.getLogger(
            FirebaseCompanionNotificationListener.class);
    private static final int MAX_MULTICAST_TOKENS = 500;
    private static final AndroidConfig HIGH_PRIORITY = AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .build();

    private final AppUserRepository appUserRepository;
    private final String firebaseProjectId;
    private volatile Firestore firestore;
    private volatile FirebaseMessaging messaging;

    FirebaseCompanionNotificationListener(
            AppUserRepository appUserRepository,
            @Value("${FIREBASE_PROJECT_ID:}") String firebaseProjectId) {
        this.appUserRepository = appUserRepository;
        this.firebaseProjectId = firebaseProjectId.trim();
    }

    @Async("companionNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onChatMessage(CompanionChatMessageCreatedEvent event) {
        String title = switch (event.senderRole().toUpperCase(Locale.ROOT)) {
            case "MANAGER" -> "매니저가 안심 채팅을 보냈습니다";
            case "PATIENT" -> "환자가 안심 채팅을 보냈습니다";
            case "GUARDIAN" -> "보호자가 안심 채팅을 보냈습니다";
            default -> "안심 채팅 새 메시지가 도착했습니다";
        };
        MulticastMessage.Builder message = MulticastMessage.builder()
                .putData("type", "companion_chat_message")
                .putData("sessionId", event.sessionId().toString())
                .putData("appointmentRequestId", event.appointmentRequestId().toString())
                .putData("senderRole", event.senderRole())
                .putData("sentAtMillis", Long.toString(event.sentAt().toEpochMilli()))
                .putData("title", title)
                .putData("body", "새 안심 채팅 메시지가 도착했습니다.")
                .setAndroidConfig(HIGH_PRIORITY);
        deliver(event.sessionId(), event.recipientUserIds(), message, "채팅");
    }

    @Async("companionNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onLocationAlert(CompanionLocationAlertChangedEvent event) {
        if ("none".equals(event.alertStage())) {
            return;
        }
        String title = "pharmacy_near".equals(event.alertStage())
                ? "약국 도착이 가까워졌습니다"
                : "병원 도착이 가까워졌습니다";
        String body = "pharmacy_near".equals(event.alertStage())
                ? "동행 위치가 약국 인근에 도착했습니다."
                : "동행 위치가 병원 인근에 도착했습니다.";
        MulticastMessage.Builder message = MulticastMessage.builder()
                .putData("type", "companion_location_alert")
                .putData("sessionId", event.sessionId().toString())
                .putData("appointmentRequestId", event.appointmentRequestId().toString())
                .putData("alertStage", event.alertStage())
                .putData("title", title)
                .putData("body", body)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setAndroidConfig(HIGH_PRIORITY);
        deliver(event.sessionId(), event.recipientUserIds(), message, "위치 알림");
    }

    private void deliver(
            UUID sessionId,
            List<UUID> recipientUserIds,
            MulticastMessage.Builder message,
            String notificationType) {
        List<String> tokens = resolveTokens(recipientUserIds);
        if (tokens.isEmpty()) {
            LOGGER.info("동행 {} 대상 기기 토큰이 없습니다. sessionId={}", notificationType, sessionId);
            return;
        }
        try {
            ensureFirebaseServices();
            BatchResponse response = messaging.sendEachForMulticast(
                    message.addAllTokens(tokens).build());
            LOGGER.info(
                    "동행 {} 전송을 마쳤습니다. sessionId={}, recipientCount={}, tokenCount={}, successCount={}, failureCount={}",
                    notificationType,
                    sessionId,
                    recipientUserIds.size(),
                    tokens.size(),
                    response.getSuccessCount(),
                    response.getFailureCount());
        } catch (Exception exception) {
            LOGGER.warn(
                    "동행 {} 전송을 완료하지 못했습니다. sessionId={}, recipientCount={}, tokenCount={}",
                    notificationType,
                    sessionId,
                    recipientUserIds.size(),
                    tokens.size());
        }
    }

    private List<String> resolveTokens(List<UUID> recipientUserIds) {
        Set<String> tokens = new LinkedHashSet<>();
        for (UUID userId : recipientUserIds) {
            if (tokens.size() >= MAX_MULTICAST_TOKENS) {
                break;
            }
            appUserRepository.findById(userId)
                    .map(AppUserRepository.AppUser::firebaseUid)
                    .filter(uid -> !uid.isBlank())
                    .ifPresent(uid -> addTokens(tokens, uid));
        }
        return new ArrayList<>(tokens).subList(0, Math.min(tokens.size(), MAX_MULTICAST_TOKENS));
    }

    private void addTokens(Set<String> tokens, String firebaseUid) {
        try {
            ensureFirebaseServices();
            DocumentSnapshot snapshot = firestore.collection("users")
                    .document(firebaseUid)
                    .get()
                    .get(5, TimeUnit.SECONDS);
            Object rawTokens = snapshot.get("notificationTokens");
            if (!(rawTokens instanceof Collection<?> values)) {
                return;
            }
            for (Object value : values) {
                if (tokens.size() >= MAX_MULTICAST_TOKENS) {
                    return;
                }
                if (value instanceof String token && !token.isBlank()) {
                    tokens.add(token.trim());
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("동행 알림 대상 기기 토큰을 읽지 못했습니다.");
        }
    }

    private void ensureFirebaseServices() throws Exception {
        if (firestore != null && messaging != null) {
            return;
        }
        synchronized (this) {
            if (firestore != null && messaging != null) {
                return;
            }
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(candidate -> firebaseProjectId.equals(
                            candidate.getOptions().getProjectId()))
                    .findFirst()
                    .orElseGet(this::initializeFirebaseApp);
            firestore = FirestoreClient.getFirestore(app);
            messaging = FirebaseMessaging.getInstance(app);
        }
    }

    private FirebaseApp initializeFirebaseApp() {
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(firebaseProjectId)
                    .build();
            return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
        } catch (Exception exception) {
            throw new IllegalStateException("Firebase Admin SDK를 초기화하지 못했습니다.", exception);
        }
    }
}
