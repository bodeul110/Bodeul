package com.example.bodeul.data.coreapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;

import org.junit.Test;

public class CoreApiManagerReportCompletionCallbackTest {
    @Test
    public void submissionSuccess_deliversReportWithoutActiveSessionReload() {
        RecordingCallback callback = new RecordingCallback();
        CoreApiManagerReportCompletionCallback completionCallback =
                new CoreApiManagerReportCompletionCallback("external-session", callback);
        CoreApiCompanionSessionClient.ReportSnapshot snapshot =
                new CoreApiCompanionSessionClient.ReportSnapshot(
                        "core-report",
                        "",
                        "core-session",
                        "동행 완료",
                        "진료 메모",
                        "복약 메모",
                        "처방약",
                        "변경 없음",
                        "아침 식후",
                        MedicationComparisonDecision.MATCHED,
                        "기존 처방과 일치",
                        "다음 달");

        completionCallback.onSuccess(snapshot);

        assertEquals("external-session", callback.result.getSessionId());
        assertEquals("동행 완료", callback.result.getSummary());
        assertNull(callback.error);
    }

    private static final class RecordingCallback implements RepositoryCallback<SessionReport> {
        private SessionReport result;
        private String error;

        @Override
        public void onSuccess(SessionReport result) {
            this.result = result;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }
}
