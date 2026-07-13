package com.bodeul.core.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@ExtendWith(OutputCaptureExtension.class)
class FirebaseAdminConfigurationTests {

    @Test
    void missingProjectIdDisablesVerifier() {
        FirebaseTokenVerifier verifier = FirebaseAdminConfiguration.createVerifier(" ", projectId -> {
            fail("project ID가 없으면 Firebase 초기화를 시도하지 않아야 합니다.");
            return idToken -> "unused";
        });

        assertThatThrownBy(() -> verifier.verify("firebase-token"))
                .isInstanceOf(FirebaseTokenVerifier.UnavailableException.class)
                .hasMessage("Firebase ID token 검증기가 설정되지 않았습니다.");
    }

    @Test
    void initializationFailureDoesNotExposeCredentialDetails(CapturedOutput output) {
        FirebaseTokenVerifier verifier = FirebaseAdminConfiguration.createVerifier(
                "bodeul-dev",
                projectId -> {
                    throw new IllegalStateException("private_key=local-secret-key");
                });

        assertThatThrownBy(() -> verifier.verify("firebase-token"))
                .isInstanceOf(FirebaseTokenVerifier.UnavailableException.class)
                .hasMessage("Firebase ID token 검증기가 설정되지 않았습니다.")
                .hasMessageNotContaining("local-secret-key");

        assertThat(output)
                .contains("Firebase Admin SDK 초기화에 실패했습니다.")
                .doesNotContain("local-secret-key")
                .doesNotContain("private_key");
    }

    @Test
    void returnsOnlyUidVerifiedByAdminSdk() {
        FirebaseTokenVerifier verifier = FirebaseAdminConfiguration.createVerifier(
                "bodeul-dev",
                projectId -> {
                    assertThat(projectId).isEqualTo("bodeul-dev");
                    return idToken -> {
                        assertThat(idToken).isEqualTo("firebase-token");
                        return "firebase-user-1";
                    };
                });

        assertThat(verifier.verify("firebase-token").uid()).isEqualTo("firebase-user-1");
    }

    @Test
    void mapsAdminSdkFailureToGenericTokenError() {
        FirebaseTokenVerifier verifier = FirebaseAdminConfiguration.createVerifier(
                "bodeul-dev",
                projectId -> idToken -> {
                    throw new IllegalArgumentException("signature details");
                });

        assertThatThrownBy(() -> verifier.verify("tampered-token"))
                .isInstanceOf(FirebaseTokenVerifier.InvalidTokenException.class)
                .hasMessage("Firebase ID token 검증에 실패했습니다.")
                .hasMessageNotContaining("signature details")
                .hasMessageNotContaining("tampered-token");
    }

    @Test
    void rejectsBlankUid() {
        FirebaseTokenVerifier verifier = FirebaseAdminConfiguration.createVerifier(
                "bodeul-dev",
                projectId -> idToken -> " ");

        assertThatThrownBy(() -> verifier.verify("firebase-token"))
                .isInstanceOf(FirebaseTokenVerifier.InvalidTokenException.class);
    }
}
