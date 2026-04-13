package com.example.bodeul.data;

/**
 * 비동기 저장소 호출 결과를 화면 계층으로 전달하기 위한 콜백이다.
 */
public interface RepositoryCallback<T> {
    // 저장소 호출이 정상 완료되면 결과 데이터를 전달한다.
    void onSuccess(T result);

    // 저장소 호출 실패 시 사용자에게 보여줄 메시지를 전달한다.
    void onError(String message);
}
