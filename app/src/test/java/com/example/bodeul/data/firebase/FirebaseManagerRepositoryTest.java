package com.example.bodeul.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDashboard;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class FirebaseManagerRepositoryTest {

    @Test
    public void preConsultationConfirmationRequiresCoreApiInsteadOfDirectFirestoreWrite() {
        FirebaseManagerRepository repository = new FirebaseManagerRepository(null);
        AtomicBoolean succeeded = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>();

        repository.updatePreConsultationConfirmed(
                "manager-1",
                true,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        succeeded.set(true);
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                    }
                });

        assertFalse(succeeded.get());
        assertEquals("진료 전 확인 저장에는 Core API 연결이 필요합니다.", error.get());
    }
}
