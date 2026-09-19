package com.example.bodeul.ui.manager;

/** 최종 화면에 표시할 단계 메모 원문 한 묶음이다. */
public final class ManagerGuideMemoItem {
    private final String title;
    private final String body;
    private final boolean empty;

    ManagerGuideMemoItem(String title, String body, boolean empty) {
        this.title = title;
        this.body = body;
        this.empty = empty;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public boolean isEmpty() {
        return empty;
    }
}
