package com.example.bodeul.ui.manager;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.example.bodeul.data.ManagerDocumentPreviewResolver;
import com.example.bodeul.data.ManagerDocumentStorageUploader;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ManagerDocumentRegistrationViewModelTest {
    private static final String MANAGER_USER_ID = "manager-1";
    private static final String STATE_OPERATION = "manager_document_registration.operation";

    @Rule
    public final InstantTaskExecutorRule instantTaskExecutorRule =
            new InstantTaskExecutorRule();

    @Test
    public void recoveredPendingOrApproved_emitsCompletionEventImmediately() {
        for (ManagerDocumentStatus status : new ManagerDocumentStatus[]{
                ManagerDocumentStatus.PENDING_REVIEW,
                ManagerDocumentStatus.APPROVED
        }) {
            RepositoryHarness repository = new RepositoryHarness();
            FakeRecoveryRetryScheduler scheduler = new FakeRecoveryRetryScheduler();
            ManagerDocumentRegistrationViewModel viewModel = createViewModel(
                    repository,
                    scheduler
            );

            viewModel.reconcileRecoveredSubmission(MANAGER_USER_ID, profile(status));

            assertFalse(viewModel.isOperationInFlight());
            assertTrue(viewModel.isCompletionPending());
            ManagerDocumentRegistrationViewModel.UiEvent event =
                    viewModel.getUiEvent().getValue();
            assertNotNull(event);
            assertEquals(
                    ManagerDocumentRegistrationViewModel.EventType.SUBMISSION_SUCCEEDED,
                    event.getType()
            );
            assertEquals(0, scheduler.pendingCount());
        }
    }

    @Test
    public void recoveredNotSubmitted_keepsLockUntilRecheckFindsPending() {
        RepositoryHarness repository = new RepositoryHarness();
        repository.enqueueOverview(ManagerDocumentStatus.PENDING_REVIEW);
        FakeRecoveryRetryScheduler scheduler = new FakeRecoveryRetryScheduler();
        ManagerDocumentRegistrationViewModel viewModel = createViewModel(
                repository,
                scheduler
        );

        viewModel.reconcileRecoveredSubmission(
                MANAGER_USER_ID,
                profile(ManagerDocumentStatus.NOT_SUBMITTED)
        );

        assertTrue(viewModel.isOperationInFlight());
        assertEquals(1, scheduler.pendingCount());
        assertFalse(viewModel.submitRegistration(MANAGER_USER_ID, "자격 증빙 원본 업로드 완료"));
        assertEquals(0, repository.submissionCount);

        scheduler.runNext();

        assertEquals(1, repository.overviewLoadCount);
        assertFalse(viewModel.isOperationInFlight());
        assertTrue(viewModel.isCompletionPending());
        assertEquals(
                ManagerDocumentRegistrationViewModel.EventType.SUBMISSION_SUCCEEDED,
                viewModel.getUiEvent().getValue().getType()
        );
    }

    @Test
    public void recoveredNotSubmitted_releasesLockOnlyAfterBoundedRechecks() {
        RepositoryHarness repository = new RepositoryHarness();
        repository.enqueueOverview(ManagerDocumentStatus.NOT_SUBMITTED);
        repository.enqueueOverview(ManagerDocumentStatus.NOT_SUBMITTED);
        repository.enqueueOverview(ManagerDocumentStatus.NOT_SUBMITTED);
        FakeRecoveryRetryScheduler scheduler = new FakeRecoveryRetryScheduler();
        ManagerDocumentRegistrationViewModel viewModel = createViewModel(
                repository,
                scheduler
        );

        viewModel.reconcileRecoveredSubmission(
                MANAGER_USER_ID,
                profile(ManagerDocumentStatus.NOT_SUBMITTED)
        );

        scheduler.runNext();
        assertTrue(viewModel.isOperationInFlight());
        scheduler.runNext();
        assertTrue(viewModel.isOperationInFlight());
        scheduler.runNext();

        assertEquals(3, repository.overviewLoadCount);
        assertFalse(viewModel.isOperationInFlight());
        assertFalse(viewModel.isCompletionPending());
        assertEquals(
                ManagerDocumentRegistrationViewModel.EventType.ERROR,
                viewModel.getUiEvent().getValue().getType()
        );
        assertEquals(0, scheduler.pendingCount());
    }

    private static ManagerDocumentRegistrationViewModel createViewModel(
            RepositoryHarness repository,
            FakeRecoveryRetryScheduler scheduler
    ) {
        SavedStateHandle state = new SavedStateHandle(
                Collections.singletonMap(STATE_OPERATION, "SUBMISSION")
        );
        ManagerDocumentStorageUploader storageUploader =
                new ManagerDocumentStorageUploader() {
                    @Override
                    public void uploadDocument(
                            String managerUserId,
                            ManagerDocumentFileType fileType,
                            android.net.Uri fileUri,
                            RepositoryCallback<ManagerDocumentFileMetadata> callback
                    ) {
                        throw new AssertionError("복구 테스트에서 업로드가 호출되면 안 됩니다.");
                    }

                    @Override
                    public boolean isFirebaseBacked() {
                        return false;
                    }
                };
        ManagerDocumentPreviewResolver previewResolver = (metadata, callback) -> {
            throw new AssertionError("복구 테스트에서 미리보기가 호출되면 안 됩니다.");
        };
        return new ManagerDocumentRegistrationViewModel(
                repository.repository,
                storageUploader,
                previewResolver,
                state,
                scheduler
        );
    }

    private static ManagerHomeProfile profile(ManagerDocumentStatus status) {
        return new ManagerHomeProfile("", "", status, "");
    }

    private static ManagerDocumentOverview overview(ManagerDocumentStatus status) {
        return new ManagerDocumentOverview(
                new User(MANAGER_USER_ID, UserRole.MANAGER, "매니저", "", ""),
                profile(status)
        );
    }

    private static final class FakeRecoveryRetryScheduler
            implements ManagerDocumentRegistrationViewModel.RecoveryRetryScheduler {
        private final Deque<Runnable> pending = new ArrayDeque<>();

        @Override
        public void schedule(Runnable runnable, long delayMillis) {
            pending.addLast(runnable);
        }

        @Override
        public void cancelAll() {
            pending.clear();
        }

        private void runNext() {
            Runnable runnable = pending.removeFirst();
            runnable.run();
        }

        private int pendingCount() {
            return pending.size();
        }
    }

    private static final class RepositoryHarness {
        private final Deque<ManagerDocumentOverview> overviews = new ArrayDeque<>();
        private int overviewLoadCount;
        private int submissionCount;
        private final ManagerRepository repository = (ManagerRepository) Proxy.newProxyInstance(
                ManagerRepository.class.getClassLoader(),
                new Class<?>[]{ManagerRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getManagerDocumentOverview")) {
                        overviewLoadCount++;
                        @SuppressWarnings("unchecked")
                        RepositoryCallback<ManagerDocumentOverview> callback =
                                (RepositoryCallback<ManagerDocumentOverview>) args[1];
                        callback.onSuccess(overviews.removeFirst());
                        return null;
                    }
                    if (method.getName().equals("saveManagerDocumentSummary")) {
                        submissionCount++;
                        return null;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return null;
                }
        );

        private void enqueueOverview(ManagerDocumentStatus status) {
            overviews.addLast(overview(status));
        }
    }
}
