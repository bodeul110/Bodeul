package com.example.bodeul.data.coreapi;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.SessionReport;

/**
 * 리포트 제출 성공을 활성 세션 재조회와 분리해 완료 결과로 전달한다.
 */
final class CoreApiManagerReportCompletionCallback
        implements RepositoryCallback<CoreApiCompanionSessionClient.ReportSnapshot> {
    private final String externalSessionId;
    private final RepositoryCallback<SessionReport> callback;

    CoreApiManagerReportCompletionCallback(
            String externalSessionId,
            RepositoryCallback<SessionReport> callback
    ) {
        this.externalSessionId = externalSessionId;
        this.callback = callback;
    }

    @Override
    public void onSuccess(CoreApiCompanionSessionClient.ReportSnapshot result) {
        callback.onSuccess(result.toModel(externalSessionId));
    }

    @Override
    public void onError(String message) {
        callback.onError(message);
    }
}
