package com.example.bodeul.domain.model;

import java.util.List;

/**
 * 특정 병원과 진료과에 맞는 동행 절차 목록을 보관한다.
 */
public class HospitalGuide {
    // 병원 가이드 자체와 적용 대상을 식별하는 정보다.
    private final String id;
    private final String hospitalName;
    private final String departmentName;

    // 실제 현장 진행 순서를 화면에 보여주기 위한 단계 목록이다.
    private final List<GuideStep> steps;

    public HospitalGuide(String id, String hospitalName, String departmentName, List<GuideStep> steps) {
        this.id = id;
        this.hospitalName = hospitalName;
        this.departmentName = departmentName;
        this.steps = steps;
    }

    public String getId() {
        return id;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public List<GuideStep> getSteps() {
        return steps;
    }
}
