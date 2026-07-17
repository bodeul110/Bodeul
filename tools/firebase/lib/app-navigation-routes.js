const APPLICATION_ID = "com.example.bodeul";
const DEFAULT_ROUTE_WAIT_MILLIS = 10000;

const ROUTE_PRESETS = {
  "patient-home": {
    screenId: "patient-home",
    title: "환자 홈",
    role: "PATIENT",
    automationScreen: "CLIENT_HOME",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.MainActivity`,
  },
  "guardian-home": {
    screenId: "guardian-home",
    title: "보호자 홈",
    role: "GUARDIAN",
    automationScreen: "CLIENT_HOME",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.MainActivity`,
  },
  "patient-booking": {
    screenId: "patient-booking",
    title: "예약 작성 화면",
    role: "PATIENT",
    automationScreen: "BOOKING",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.booking.BookingActivity`,
  },
  "guardian-booking-status": {
    screenId: "guardian-booking-status",
    title: "보호자 예약 상세",
    role: "GUARDIAN",
    automationScreen: "BOOKING_STATUS",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.booking.BookingStatusActivity`,
    defaultRequestId: "request-seed-progress",
  },
  "patient-booking-follow-up": {
    screenId: "patient-booking-follow-up",
    title: "환자 종료 후속 처리",
    role: "PATIENT",
    automationScreen: "BOOKING_FOLLOW_UP",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.booking.BookingFollowUpActivity`,
    defaultRequestId: "request-seed-completed",
  },
  "guardian-report": {
    screenId: "guardian-report",
    title: "보호자 리포트",
    role: "GUARDIAN",
    automationScreen: "GUARDIAN_REPORT",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.report.GuardianReportActivity`,
  },
  "guardian-chat": {
    screenId: "guardian-chat",
    title: "보호자 안심 채팅",
    role: "GUARDIAN",
    automationScreen: "COMPANION_CHAT",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.chat.CompanionChatActivity`,
    defaultRequestId: "request-seed-progress",
  },
  "manager-home": {
    screenId: "manager-home",
    title: "매니저 홈",
    role: "MANAGER",
    automationScreen: "MANAGER_HOME",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.manager.ManagerActivity`,
  },
  "manager-history": {
    screenId: "manager-history",
    title: "매니저 과거 이력",
    role: "MANAGER",
    automationScreen: "MANAGER_HISTORY",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.manager.ManagerHistoryActivity`,
  },
  "manager-guide": {
    screenId: "manager-guide",
    title: "매니저 가이드",
    role: "MANAGER",
    automationScreen: "MANAGER_GUIDE",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.manager.ManagerGuideActivity`,
  },
  "manager-chat": {
    screenId: "manager-chat",
    title: "매니저 안심 채팅",
    role: "MANAGER",
    automationScreen: "COMPANION_CHAT",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.chat.CompanionChatActivity`,
  },
  "manager-support": {
    screenId: "manager-support",
    title: "매니저 문의",
    role: "MANAGER",
    automationScreen: "MANAGER_SUPPORT",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.manager.ManagerSupportActivity`,
  },
  "manager-profile": {
    screenId: "manager-profile",
    title: "매니저 내 페이지",
    role: "MANAGER",
    automationScreen: "MANAGER_PROFILE",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.manager.ManagerProfileActivity`,
  },
  "admin-dashboard": {
    screenId: "admin-dashboard",
    title: "관리자 운영 대시보드",
    role: "ADMIN",
    automationScreen: "ADMIN_DASHBOARD",
    expectedActivity: `${APPLICATION_ID}/com.example.bodeul.ui.admin.AdminActivity`,
  },
};

function resolveRoutePreset(presetName) {
  if (!presetName) {
    return null;
  }
  return ROUTE_PRESETS[presetName] || null;
}

module.exports = {
  APPLICATION_ID,
  DEFAULT_ROUTE_WAIT_MILLIS,
  ROUTE_PRESETS,
  resolveRoutePreset,
};
