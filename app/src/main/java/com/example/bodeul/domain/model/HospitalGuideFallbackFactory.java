package com.example.bodeul.domain.model;

import java.util.Arrays;

/**
 * 관리자 병원 가이드가 아직 없을 때 사용할 공통 동행 가이드를 만든다.
 */
public final class HospitalGuideFallbackFactory {
    private HospitalGuideFallbackFactory() {
    }

    public static HospitalGuide create(String hospitalName, String departmentName) {
        return new HospitalGuide(
                "default-guide",
                normalize(hospitalName),
                normalize(departmentName),
                Arrays.asList(
                        new GuideStep(1, "환자 접촉", "환자 도착 여부와 기본 컨디션을 확인하고 보호자에게 시작 상황을 공유합니다."),
                        new GuideStep(2, "접수 준비", "예약 정보, 신분증, 필요 서류를 확인하고 접수 창구 위치를 확인합니다."),
                        new GuideStep(3, "진료 접수", "진료과 접수와 대기 순서를 확인하고 예상 대기 시간을 보호자에게 전달합니다."),
                        new GuideStep(4, "진료 동행", "진료 중 필요한 설명과 요청 사항을 메모하고 의료진 안내를 정리합니다."),
                        new GuideStep(5, "수납 처리", "수납, 검사 예약, 다음 방문 일정 여부를 확인합니다."),
                        new GuideStep(6, "약국 방문", "처방전 수령과 약국 방문 여부를 확인하고 복약 안내를 정리합니다."),
                        new GuideStep(7, "귀가 및 종료", "귀가 동선과 최종 특이사항을 확인하고 보호자에게 종료 상황을 전달합니다.")
                )
        );
    }

    public static HospitalGuide fallbackIfMissing(
            HospitalGuide hospitalGuide,
            String hospitalName,
            String departmentName
    ) {
        return hospitalGuide == null ? create(hospitalName, departmentName) : hospitalGuide;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
