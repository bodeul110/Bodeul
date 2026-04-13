package com.example.bodeul.data;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Firebase 설정이 없는 환경에서도 화면을 확인할 수 있도록 제공하는 인메모리 저장소다.
 */
public class MockBodeulRepository implements BodeulRepository {
    private final List<User> users = new ArrayList<>();
    private final List<AppointmentRequest> appointmentRequests = new ArrayList<>();
    private final List<CompanionSession> companionSessions = new ArrayList<>();
    private final List<HospitalGuide> hospitalGuides = new ArrayList<>();
    private final List<SessionReport> sessionReports = new ArrayList<>();
    private final Map<String, String> passwordsByEmail = new HashMap<>();

    public MockBodeulRepository() {
        seedUsers();
        seedAppointmentRequests();
        seedCompanionSessions();
        seedHospitalGuides();
    }

    @Override
    public synchronized List<User> getUsers() {
        return Collections.unmodifiableList(new ArrayList<>(users));
    }

    @Override
    public synchronized List<AppointmentRequest> getAppointmentRequests() {
        return Collections.unmodifiableList(new ArrayList<>(appointmentRequests));
    }

    @Override
    public synchronized List<CompanionSession> getManagerSessions(String managerUserId) {
        List<CompanionSession> result = new ArrayList<>();
        for (CompanionSession session : companionSessions) {
            if (session.getManagerUserId().equals(managerUserId)) {
                result.add(session);
            }
        }
        return result;
    }

    @Override
    public synchronized HospitalGuide getHospitalGuide(String hospitalName, String departmentName) {
        for (HospitalGuide guide : hospitalGuides) {
            if (guide.getHospitalName().equals(hospitalName)
                    && guide.getDepartmentName().equals(departmentName)) {
                return guide;
            }
        }
        return null;
    }

    @Override
    public synchronized SessionReport getSessionReport(String sessionId) {
        for (SessionReport report : sessionReports) {
            if (report.getSessionId().equals(sessionId)) {
                return report;
            }
        }
        return null;
    }

    @Nullable
    public synchronized User findUserById(String userId) {
        for (User user : users) {
            if (user.getId().equals(userId)) {
                return user;
            }
        }
        return null;
    }

    @Nullable
    public synchronized User findUserByEmail(String email) {
        for (User user : users) {
            if (user.getEmail().equalsIgnoreCase(email)) {
                return user;
            }
        }
        return null;
    }

    public synchronized boolean isPasswordValid(String email, String password) {
        String savedPassword = passwordsByEmail.get(normalizeKey(email));
        return savedPassword != null && savedPassword.equals(password);
    }

    @Nullable
    public synchronized User registerUser(
            String name,
            String email,
            String phone,
            UserRole role,
            String password
    ) {
        if (findUserByEmail(email) != null) {
            return null;
        }

        String id = role.name().toLowerCase(Locale.ROOT) + "-" + (users.size() + 1);
        User user = new User(id, role, name, email, phone);
        users.add(user);
        passwordsByEmail.put(normalizeKey(email), password);
        return user;
    }

    @Nullable
    public synchronized ManagerDashboard getManagerDashboard(String managerUserId) {
        // 매니저 홈과 가이드 화면이 한 번에 그려질 수 있도록 관련 데이터를 묶어 반환한다.
        User manager = findUserById(managerUserId);
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (manager == null || session == null) {
            return null;
        }

        AppointmentRequest request = findAppointmentRequest(session.getAppointmentRequestId());
        if (request == null) {
            return null;
        }

        User patient = findUserById(request.getPatientUserId());
        User guardian = findUserById(request.getGuardianUserId());
        HospitalGuide guide = getHospitalGuide(request.getHospitalName(), request.getDepartmentName());
        SessionReport report = getSessionReport(session.getId());

        if (patient == null || guardian == null || guide == null) {
            return null;
        }

        return new ManagerDashboard(manager, patient, guardian, request, session, guide, report);
    }

    @Nullable
    public synchronized ManagerDashboard advanceManagerSession(String managerUserId) {
        ManagerDashboard dashboard = getManagerDashboard(managerUserId);
        if (dashboard == null) {
            return null;
        }

        CompanionSession session = dashboard.getSession();
        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
        if (session.getCurrentStepOrder() >= totalSteps) {
            return dashboard;
        }

        int nextStep = session.getCurrentStepOrder() + 1;
        // 단계가 넘어가면 세션 상태와 요청 상태를 함께 진행 중으로 맞춘다.
        session.setCurrentStepOrder(nextStep);
        session.setStatus(resolveStepStatus(nextStep, totalSteps));
        dashboard.getAppointmentRequest().setStatus(AppointmentStatus.IN_PROGRESS);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updateGuardianMessage(String managerUserId, String message) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setGuardianUpdate(message);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updateMedicationNote(String managerUserId, String note) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setMedicationNote(note);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard saveSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String nextVisitAt
    ) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }

        SessionReport existingReport = getSessionReport(session.getId());
        if (existingReport != null) {
            sessionReports.remove(existingReport);
        }

        // 리포트 저장이 완료되면 세션과 요청 상태를 모두 종료 상태로 바꾼다.
        SessionReport report = new SessionReport(
                "report-" + session.getId(),
                session.getId(),
                summary,
                treatmentNotes,
                medicationNotes,
                nextVisitAt
        );
        sessionReports.add(report);
        session.setStatus(SessionStatus.COMPLETED);

        AppointmentRequest request = findAppointmentRequest(session.getAppointmentRequestId());
        if (request != null) {
            request.setStatus(AppointmentStatus.COMPLETED);
        }

        return getManagerDashboard(managerUserId);
    }

    @Nullable
    private AppointmentRequest findAppointmentRequest(String appointmentRequestId) {
        for (AppointmentRequest request : appointmentRequests) {
            if (request.getId().equals(appointmentRequestId)) {
                return request;
            }
        }
        return null;
    }

    @Nullable
    private CompanionSession getPrimaryManagerSession(String managerUserId) {
        for (CompanionSession session : companionSessions) {
            if (session.getManagerUserId().equals(managerUserId)) {
                return session;
            }
        }
        return null;
    }

    private SessionStatus resolveStepStatus(int stepOrder, int totalSteps) {
        if (stepOrder <= 1) {
            return SessionStatus.MEETING;
        }
        if (stepOrder == 2) {
            return SessionStatus.WAITING;
        }
        if (stepOrder <= 4) {
            return SessionStatus.IN_TREATMENT;
        }
        if (stepOrder < totalSteps) {
            return SessionStatus.PAYMENT;
        }
        return SessionStatus.PAYMENT;
    }

    private String normalizeKey(String value) {
        // 이메일과 내부 키는 사용자 기기 로케일과 무관하게 동일한 규칙으로 정규화한다.
        return value.toLowerCase(Locale.ROOT);
    }

    private void seedUsers() {
        // 로그인과 화면 데모에 사용할 기본 사용자 계정을 미리 만든다.
        users.add(new User(
                "patient-1",
                UserRole.PATIENT,
                "이현우",
                "patient@bodeul.app",
                "010-0000-0001"
        ));
        users.add(new User(
                "guardian-1",
                UserRole.GUARDIAN,
                "김유나",
                "guardian@bodeul.app",
                "010-0000-0002"
        ));
        users.add(new User(
                "manager-1",
                UserRole.MANAGER,
                "김승민",
                "manager@bodeul.app",
                "010-0000-0003"
        ));
        users.add(new User(
                "admin-1",
                UserRole.ADMIN,
                "관리자",
                "admin@bodeul.app",
                "010-0000-0004"
        ));

        passwordsByEmail.put("patient@bodeul.app", "bodeul1234");
        passwordsByEmail.put("guardian@bodeul.app", "bodeul1234");
        passwordsByEmail.put("manager@bodeul.app", "bodeul1234");
        passwordsByEmail.put("admin@bodeul.app", "bodeul1234");
    }

    private void seedAppointmentRequests() {
        appointmentRequests.add(new AppointmentRequest(
                "request-1",
                "patient-1",
                "guardian-1",
                "서울내과병원",
                "신경과",
                "2026-04-15 10:30",
                "본관 1층 안내 데스크",
                "어지럼 증상과 복용 중인 약 정보를 함께 확인해주세요.",
                AppointmentStatus.MATCHED,
                "manager-1"
        ));
    }

    private void seedCompanionSessions() {
        companionSessions.add(new CompanionSession(
                "session-1",
                "request-1",
                "manager-1",
                2,
                SessionStatus.MEETING,
                "환자분을 만나 병원으로 이동 중입니다.",
                "처방전 수령 전입니다."
        ));
    }

    private void seedHospitalGuides() {
        hospitalGuides.add(new HospitalGuide(
                "guide-1",
                "서울내과병원",
                "신경과",
                Arrays.asList(
                        new GuideStep(1, "환자 접촉", "환자분 도착 여부를 확인하고 보호자에게 출발 상황을 공유합니다."),
                        new GuideStep(2, "간편 등록", "접수 창구에서 예약 정보와 신분증을 확인합니다."),
                        new GuideStep(3, "진료 접수", "진료과와 대기 순서를 확인하고 필요한 서류를 제출합니다."),
                        new GuideStep(4, "진료 완료", "진료 결과와 다음 안내 사항을 메모합니다."),
                        new GuideStep(5, "수납 처리", "수납 및 검사 예약 여부를 확인합니다."),
                        new GuideStep(6, "약국 방문", "처방전을 수령하고 약 복용법을 정리합니다."),
                        new GuideStep(7, "환자 귀가(서비스 종료)", "귀가 동선을 확인하고 보호자에게 최종 상황을 전달합니다.")
                )
        ));
    }
}
