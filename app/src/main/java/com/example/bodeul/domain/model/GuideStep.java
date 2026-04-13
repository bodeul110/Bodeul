package com.example.bodeul.domain.model;

/**
 * 병원 동행 가이드에서 한 단계의 제목과 설명을 표현한다.
 */
public class GuideStep {
    // 화면 정렬과 진행 판별에 사용하는 단계 순서다.
    private final int order;

    // 매니저 화면에 노출할 단계 이름과 설명이다.
    private final String title;
    private final String description;

    public GuideStep(int order, String title, String description) {
        this.order = order;
        this.title = title;
        this.description = description;
    }

    public int getOrder() {
        return order;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
