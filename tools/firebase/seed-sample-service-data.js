#!/usr/bin/env node

const {
  BASELINE_GUIDES,
  BASELINE_USERS,
  DAY_IN_MILLIS,
} = require("./lib/baseline-config");
const {
  createCliContext,
  getCollectionCounts,
  getDocument,
  patchDocumentData,
  resolveBaselineUsers,
} = require("./lib/firebase-toolkit");

const SAMPLE_COLLECTIONS = Object.freeze([
  "appointmentRequests",
  "companionSessions",
  "sessionReports",
  "appointmentFollowUps",
  "supportInquiries",
  "clientSupportRequests",
  "adminSettlementRecords",
  "adminEmergencyIssues",
  "adminActionNotifications",
  "adminAuditLogs",
  "adminActionDeliveries",
  "adminActionDeliveryJobs",
  "appointmentReminderJobs",
]);

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  const baselineUsers = await resolveBaselineUsers(context, BASELINE_USERS, false);
  if (baselineUsers.missingUsers.length > 0) {
    throw new Error(
        "기준선 Auth 계정이 부족합니다. 먼저 reset:baseline:apply로 기준선을 다시 맞춰주세요.",
    );
  }

  await assertUserDocumentsReady(context, baselineUsers.foundUsers);

  const collectionCounts = await getCollectionCounts(context, SAMPLE_COLLECTIONS);
  const sampleState = buildSampleState(baselineUsers.foundUsers);

  printPlan(options, context.projectId, baselineUsers.foundUsers, collectionCounts, sampleState);

  if (!options.apply) {
    console.log("");
    console.log("dry-run 완료: 샘플 데이터는 실제로 저장하지 않았습니다.");
    return;
  }

  let writtenCount = 0;
  for (const document of sampleState.documents) {
    await patchDocumentData(context, document.path, document.data);
    writtenCount++;
  }

  const updatedCounts = await getCollectionCounts(context, SAMPLE_COLLECTIONS);
  console.log("");
  console.log(`샘플 데이터 저장이 끝났습니다. 총 ${writtenCount}개 문서를 upsert했습니다.`);
  console.log("");
  console.log("저장 후 컬렉션 문서 수:");
  for (const collectionName of SAMPLE_COLLECTIONS) {
    console.log(`- ${collectionName}: ${updatedCounts[collectionName]}건`);
  }
}

function parseOptions(args) {
  return {
    apply: args.includes("--apply"),
    help: args.includes("--help") || args.includes("-h"),
  };
}

function printHelp() {
  console.log("보들 Firebase 샘플 서비스 데이터 주입 스크립트");
  console.log("");
  console.log("사용법:");
  console.log("  node seed-sample-service-data.js --dry-run");
  console.log("  node seed-sample-service-data.js --apply");
  console.log("");
  console.log("기본 동작:");
  console.log("- users, hospitalGuides 기준선은 건드리지 않습니다.");
  console.log("- 고정 ID 문서를 upsert하므로 같은 스크립트를 다시 실행해도 중복 문서를 늘리지 않습니다.");
  console.log("- 예약 요청, 진행 중 세션, 종료 후속 처리, 관리자 후속 알림 샘플을 함께 넣습니다.");
}

async function assertUserDocumentsReady(context, baselineUsers) {
  for (const baselineUser of baselineUsers) {
    const userDocument = await getDocument(context, `users/${baselineUser.uid}`);
    if (!userDocument) {
      throw new Error(
          `users/${baselineUser.uid} 문서를 찾지 못했습니다. 먼저 reset:baseline:apply를 실행해주세요.`,
      );
    }
  }
}

function buildSampleState(baselineUsers) {
  if (!Array.isArray(BASELINE_GUIDES) || BASELINE_GUIDES.length === 0) {
    throw new Error("기준선 병원 가이드가 없습니다. baseline-config를 먼저 확인해주세요.");
  }

  const usersByRole = new Map();
  for (const baselineUser of baselineUsers) {
    usersByRole.set(baselineUser.role, baselineUser);
  }

  const patient = requireRoleUser(usersByRole, "PATIENT");
  const guardian = requireRoleUser(usersByRole, "GUARDIAN");
  const manager = requireRoleUser(usersByRole, "MANAGER");
  const admin = requireRoleUser(usersByRole, "ADMIN");
  const primaryGuide = BASELINE_GUIDES[0];

  const now = Date.now();
  const requestedAppointmentAtMillis = buildLocalTimeFromNow(4, 10, 30);
  const inProgressAppointmentAtMillis = buildLocalTimeFromNow(1, 14, 0);
  const completedAppointmentAtMillis = buildLocalTimeFromNow(-2, 9, 20);

  const requestedCreatedAt = now - (18 * 60 * 60 * 1000);
  const inProgressCreatedAt = now - (3 * DAY_IN_MILLIS);
  const inProgressUpdatedAt = now - (45 * 60 * 1000);
  const completedCreatedAt = now - (8 * DAY_IN_MILLIS);
  const completedUpdatedAt = now - (2 * DAY_IN_MILLIS);

  const supportAnsweredCreatedAt = now - (3 * DAY_IN_MILLIS);
  const supportAnsweredRespondedAt = supportAnsweredCreatedAt + (2 * 60 * 60 * 1000);
  const supportReceivedCreatedAt = now - (75 * 60 * 1000);

  const settlementNotificationCreatedAt = now - (3 * 60 * 60 * 1000);
  const supportNotificationCreatedAt = now - (25 * 60 * 1000);

  const documents = [
    {
      collection: "appointmentRequests",
      path: "appointmentRequests/request-seed-requested",
      data: buildAppointmentRequest({
        id: "request-seed-requested",
        patient,
        guardian,
        manager: null,
        hospitalName: primaryGuide.hospitalName,
        departmentName: primaryGuide.departmentName,
        appointmentAtMillis: requestedAppointmentAtMillis,
        meetingPlace: "본관 1층 안내 데스크",
        specialNotes: "신분증과 최근 복약 정보를 다시 확인해 주세요.",
        patientConditionSummary: "가벼운 어지럼 증상이 있어 천천히 이동이 필요합니다.",
        medicationSummary: "혈압약 아침 복용 중",
        mobilitySupportCode: "INDEPENDENT",
        tripTypeCode: "ROUND_TRIP",
        managerGenderPreferenceCode: "ANY",
        paymentMethodCode: "CARD",
        couponCode: "FIRST_VISIT",
        basePrice: 68000,
        optionSurchargePrice: 22000,
        couponDiscountPrice: 5000,
        finalPrice: 85000,
        paymentStatusCode: "AUTHORIZED",
        paymentApprovalCode: "APPROVAL-SEED-REQUESTED",
        paymentApprovedAt: toIsoString(requestedCreatedAt + (5 * 60 * 1000)),
        paymentProviderLabel: "테스트 카드 승인",
        requesterUserId: guardian.uid,
        requesterRole: guardian.role,
        requesterName: guardian.name,
        requesterPhone: guardian.phone,
        status: "REQUESTED",
        createdAt: requestedCreatedAt,
        updatedAt: requestedCreatedAt,
      }),
    },
    {
      collection: "appointmentRequests",
      path: "appointmentRequests/request-seed-progress",
      data: buildAppointmentRequest({
        id: "request-seed-progress",
        patient,
        guardian,
        manager,
        hospitalName: primaryGuide.hospitalName,
        departmentName: primaryGuide.departmentName,
        appointmentAtMillis: inProgressAppointmentAtMillis,
        meetingPlace: "본관 1층 로비",
        specialNotes: "보행 보조기 사용 중이라 접수 창구까지 함께 이동이 필요합니다.",
        patientConditionSummary: "장시간 대기 시 어지럼이 심해질 수 있습니다.",
        medicationSummary: "점심 복용 약 확인 필요",
        mobilitySupportCode: "WALKING_AID",
        tripTypeCode: "ONE_WAY",
        managerGenderPreferenceCode: "ANY",
        paymentMethodCode: "CARD",
        couponCode: "NONE",
        basePrice: 68000,
        optionSurchargePrice: 8000,
        couponDiscountPrice: 0,
        finalPrice: 76000,
        paymentStatusCode: "AUTHORIZED",
        paymentApprovalCode: "APPROVAL-SEED-PROGRESS",
        paymentApprovedAt: toIsoString(inProgressCreatedAt + (12 * 60 * 1000)),
        paymentProviderLabel: "테스트 카드 승인",
        requesterUserId: patient.uid,
        requesterRole: patient.role,
        requesterName: patient.name,
        requesterPhone: patient.phone,
        status: "IN_PROGRESS",
        createdAt: inProgressCreatedAt,
        updatedAt: inProgressUpdatedAt,
      }),
    },
    {
      collection: "appointmentRequests",
      path: "appointmentRequests/request-seed-completed",
      data: buildAppointmentRequest({
        id: "request-seed-completed",
        patient,
        guardian,
        manager,
        hospitalName: primaryGuide.hospitalName,
        departmentName: primaryGuide.departmentName,
        appointmentAtMillis: completedAppointmentAtMillis,
        meetingPlace: "본관 2층 수납 창구 앞",
        specialNotes: "검사 결과를 보호자에게 바로 전달해 주세요.",
        patientConditionSummary: "휠체어 보조가 필요하고 검사 후 귀가 지원이 필요합니다.",
        medicationSummary: "처방전 수령 후 복약 설명 확인 예정",
        mobilitySupportCode: "WHEELCHAIR",
        tripTypeCode: "ROUND_TRIP",
        managerGenderPreferenceCode: "ANY",
        paymentMethodCode: "ON_SITE",
        couponCode: "FAMILY",
        basePrice: 68000,
        optionSurchargePrice: 37000,
        couponDiscountPrice: 10000,
        finalPrice: 95000,
        paymentStatusCode: "DEFERRED",
        paymentApprovalCode: "",
        paymentApprovedAt: "",
        paymentProviderLabel: "현장 결제",
        requesterUserId: guardian.uid,
        requesterRole: guardian.role,
        requesterName: guardian.name,
        requesterPhone: guardian.phone,
        status: "COMPLETED",
        createdAt: completedCreatedAt,
        updatedAt: completedUpdatedAt,
      }),
    },
    {
      collection: "companionSessions",
      path: "companionSessions/session-seed-progress",
      data: {
        appointmentRequestId: "request-seed-progress",
        managerUserId: manager.uid,
        currentStepOrder: 3,
        currentStatus: "WAITING",
        guardianUpdate: "접수를 마치고 진료 대기 중입니다.",
        locationSummary: `${primaryGuide.hospitalName} ${primaryGuide.departmentName} 대기 구역`,
        fieldPhotoNote: "대기 번호표와 접수 확인 화면을 확인했습니다.",
        medicationNote: "복용 약 목록을 진료 전 다시 확인할 예정입니다.",
        createdAt: inProgressCreatedAt,
        updatedAt: inProgressUpdatedAt,
      },
    },
    {
      collection: "companionSessions",
      path: "companionSessions/session-seed-completed",
      data: {
        appointmentRequestId: "request-seed-completed",
        managerUserId: manager.uid,
        currentStepOrder: 7,
        currentStatus: "COMPLETED",
        guardianUpdate: "진료와 수납을 마치고 보호자에게 결과를 전달했습니다.",
        locationSummary: "귀가 차량 탑승 완료",
        fieldPhotoNote: "수납 확인서와 처방전 수령 완료",
        medicationNote: "복약 설명을 듣고 보호자에게 전달했습니다.",
        createdAt: completedCreatedAt,
        updatedAt: completedUpdatedAt,
      },
    },
    {
      collection: "sessionReports",
      path: "sessionReports/report-seed-completed",
      data: {
        sessionId: "session-seed-completed",
        summary: `${primaryGuide.departmentName} 진료 후 추가 경과 관찰이 필요하다는 안내를 받았습니다.`,
        treatmentNotes: "기본 검사 진행 후 2주 뒤 재방문 권고",
        medicationNotes: "식후 복용 약 2종과 취침 전 복용 약 1종을 처방받았습니다.",
        nextVisitAt: formatAppointmentAt(buildLocalTimeFromNow(12, 10, 0)),
        createdAt: completedUpdatedAt + (20 * 60 * 1000),
        updatedAt: completedUpdatedAt + (20 * 60 * 1000),
      },
    },
    {
      collection: "appointmentFollowUps",
      path: "appointmentFollowUps/request-seed-completed",
      data: {
        requestId: "request-seed-completed",
        reviewRatingCode: "good",
        reviewSavedAt: completedUpdatedAt + (40 * 60 * 1000),
        settlementFollowUpStatus: "NEEDS_HELP",
        settlementFollowUpNote: "현장 결제 영수증과 실제 청구 금액을 다시 확인하고 싶습니다.",
        settlementFollowUpSavedAt: completedUpdatedAt + (55 * 60 * 1000),
        supportEscalationStatus: "MANAGER_CALLED",
        supportEscalatedAt: completedUpdatedAt + (65 * 60 * 1000),
      },
    },
    {
      collection: "supportInquiries",
      path: "supportInquiries/support-seed-answered",
      data: {
        managerUserId: manager.uid,
        managerName: manager.name,
        category: "SETTLEMENT",
        title: "정산 일정 확인 요청",
        body: "현장 결제 건이 정산 목록에 언제 반영되는지 확인 부탁드립니다.",
        status: "ANSWERED",
        createdAt: supportAnsweredCreatedAt,
        responseText: "영업일 기준 다음 날 오후 정산 내역에서 확인할 수 있습니다.",
        respondedAt: supportAnsweredRespondedAt,
        respondedByName: admin.name,
      },
    },
    {
      collection: "supportInquiries",
      path: "supportInquiries/support-seed-received",
      data: {
        managerUserId: manager.uid,
        managerName: manager.name,
        category: "MATCHING",
        title: "다음 주 배정 가능 시간 문의",
        body: "다음 주 오전 배정 가능 시간을 한 번에 확인할 수 있는지 문의드립니다.",
        status: "RECEIVED",
        createdAt: supportReceivedCreatedAt,
        responseText: "",
        respondedAt: 0,
        respondedByName: "",
      },
    },
    {
      collection: "clientSupportRequests",
      path: "clientSupportRequests/client-support-seed-guardian-answered",
      data: {
        userId: guardian.uid,
        userName: guardian.name,
        userRole: guardian.role,
        appointmentRequestId: "request-seed-completed",
        category: "report",
        title: "복약 변화 정리 요청",
        body: "진료 후 복약 변화 내용을 보호자 리포트에서 조금 더 자세히 확인하고 싶습니다.",
        status: "ANSWERED",
        createdAt: supportAnsweredCreatedAt,
        responseText: "복약 변화 섹션과 보호자 리포트 메모를 함께 확인해 주세요. 필요하면 추가 정리해 드리겠습니다.",
        respondedAt: supportAnsweredRespondedAt,
        respondedByName: admin.name,
      },
    },
    {
      collection: "clientSupportRequests",
      path: "clientSupportRequests/client-support-seed-patient-received",
      data: {
        userId: patient.uid,
        userName: patient.name,
        userRole: patient.role,
        appointmentRequestId: "request-seed-progress",
        category: "progress",
        title: "현재 진행 상태 문의",
        body: "실시간 위치와 동행 단계가 언제 다시 갱신되는지 확인 요청드립니다.",
        status: "RECEIVED",
        createdAt: supportReceivedCreatedAt,
        responseText: "",
        respondedAt: 0,
        respondedByName: "",
      },
    },
    {
      collection: "adminSettlementRecords",
      path: "adminSettlementRecords/request-seed-completed",
      data: {
        requestId: "request-seed-completed",
        status: "NEEDS_REVIEW",
        note: "사용자가 현장 결제 내역 재확인을 요청해 운영 재검토 대상으로 표시했습니다.",
        handledByName: admin.name,
        handledAt: settlementNotificationCreatedAt,
      },
    },
    {
      collection: "adminEmergencyIssues",
      path: "adminEmergencyIssues/request-seed-completed",
      data: {
        requestId: "request-seed-completed",
        status: "REPORTED",
        note: "SOS 후속 확인을 위해 매니저 재연락 기록을 남겼습니다.",
        handledByName: admin.name,
        handledAt: settlementNotificationCreatedAt + (8 * 60 * 1000),
      },
    },
    {
      collection: "adminActionNotifications",
      path: "adminActionNotifications/admin-notification-seed-settlement",
      data: {
        sourceType: "SETTLEMENT",
        level: "WARNING",
        requestId: "request-seed-completed",
        inquiryId: "",
        title: "정산 재확인 요청 저장",
        body: "완료된 요청에서 현장 결제 재확인 요청이 접수되었습니다.",
        actorName: guardian.name,
        createdAt: settlementNotificationCreatedAt,
        isRead: false,
        readAt: 0,
        isResolved: false,
        resolvedAt: 0,
        resolvedByName: "",
      },
    },
    {
      collection: "adminActionNotifications",
      path: "adminActionNotifications/admin-notification-seed-support",
      data: {
        sourceType: "SUPPORT",
        level: "INFO",
        requestId: "",
        inquiryId: "support-seed-received",
        title: "매니저 문의 접수",
        body: "배정 가능 시간 관련 문의가 접수되어 응답이 필요합니다.",
        actorName: manager.name,
        createdAt: supportNotificationCreatedAt,
        isRead: false,
        readAt: 0,
        isResolved: false,
        resolvedAt: 0,
        resolvedByName: "",
      },
    },
    {
      collection: "adminAuditLogs",
      path: "adminAuditLogs/admin-audit-seed-settlement",
      data: {
        sourceType: "SETTLEMENT",
        requestId: "request-seed-completed",
        inquiryId: "",
        actionSummary: "정산 후속 상태를 재확인으로 저장",
        note: "현장 결제 영수증 재확인 요청을 접수했습니다.",
        actorName: admin.name,
        createdAt: settlementNotificationCreatedAt + (2 * 60 * 1000),
      },
    },
    {
      collection: "adminAuditLogs",
      path: "adminAuditLogs/admin-audit-seed-support",
      data: {
        sourceType: "SUPPORT",
        requestId: "",
        inquiryId: "support-seed-answered",
        actionSummary: "지원 문의 답변 저장",
        note: "정산 일정 문의에 대한 운영 답변을 등록했습니다.",
        actorName: admin.name,
        createdAt: supportAnsweredRespondedAt,
      },
    },
    {
      collection: "adminActionDeliveries",
      path: "adminActionDeliveries/admin-delivery-seed-settlement-feed",
      data: {
        notificationId: "admin-notification-seed-settlement",
        sourceType: "SETTLEMENT",
        trigger: "notification_created",
        channel: "operations_feed",
        status: "confirmed",
        requestId: "request-seed-completed",
        inquiryId: "",
        title: "정산 재확인 요청 저장",
        body: "운영 피드에 정산 재확인 요청을 노출했습니다.",
        targetLabel: "관리자 운영 피드",
        note: "운영 피드 등록 완료",
        createdAt: settlementNotificationCreatedAt,
        processedAt: settlementNotificationCreatedAt + (30 * 1000),
        attemptCount: 1,
        maxAttemptCount: 1,
      },
    },
    {
      collection: "adminActionDeliveries",
      path: "adminActionDeliveries/admin-delivery-seed-settlement-push",
      data: {
        notificationId: "admin-notification-seed-settlement",
        sourceType: "SETTLEMENT",
        trigger: "notification_created",
        channel: "app_push",
        status: "failed",
        requestId: "request-seed-completed",
        inquiryId: "",
        title: "정산 재확인 요청 저장",
        body: "관리자 앱 푸시 발송에 실패해 재확인이 필요합니다.",
        targetLabel: "관리자 앱",
        note: "테스트 푸시 발송 실패 후 재시도 대기",
        createdAt: settlementNotificationCreatedAt,
        processedAt: settlementNotificationCreatedAt + (5 * 60 * 1000),
        attemptCount: 2,
        maxAttemptCount: 3,
      },
    },
    {
      collection: "adminActionDeliveries",
      path: "adminActionDeliveries/admin-delivery-seed-support-feed",
      data: {
        notificationId: "admin-notification-seed-support",
        sourceType: "SUPPORT",
        trigger: "notification_created",
        channel: "operations_feed",
        status: "confirmed",
        requestId: "",
        inquiryId: "support-seed-received",
        title: "매니저 문의 접수",
        body: "운영 피드에 새 문의를 노출했습니다.",
        targetLabel: "관리자 운영 피드",
        note: "운영 피드 등록 완료",
        createdAt: supportNotificationCreatedAt,
        processedAt: supportNotificationCreatedAt + (20 * 1000),
        attemptCount: 1,
        maxAttemptCount: 1,
      },
    },
    {
      collection: "adminActionDeliveries",
      path: "adminActionDeliveries/admin-delivery-seed-support-push",
      data: {
        notificationId: "admin-notification-seed-support",
        sourceType: "SUPPORT",
        trigger: "notification_created",
        channel: "app_push",
        status: "sent",
        requestId: "",
        inquiryId: "support-seed-received",
        title: "매니저 문의 접수",
        body: "관리자 앱 푸시 확인 대기 중입니다.",
        targetLabel: "관리자 앱",
        note: "테스트 푸시를 발송 대기열에 등록했습니다.",
        createdAt: supportNotificationCreatedAt,
        processedAt: 0,
        attemptCount: 1,
        maxAttemptCount: 3,
      },
    },
    {
      collection: "adminActionDeliveryJobs",
      path: "adminActionDeliveryJobs/admin-job-seed-settlement-push",
      data: {
        deliveryId: "admin-delivery-seed-settlement-push",
        notificationId: "admin-notification-seed-settlement",
        sourceType: "SETTLEMENT",
        trigger: "notification_created",
        channel: "app_push",
        requestId: "request-seed-completed",
        inquiryId: "",
        title: "정산 재확인 요청 저장",
        body: "관리자 앱 푸시 발송에 실패해 재확인이 필요합니다.",
        targetLabel: "관리자 앱",
        messagePreview: "정산 재확인 요청 저장 - 관리자 앱 푸시 발송에 실패해 재확인이 필요합니다.",
        recipientRole: "ADMIN",
        recipientUserIds: [],
        state: "FAILED",
        deliveryAttempts: 2,
        maxAttempts: 3,
        lastDeliverySource: "seed-script",
        lastError: "테스트 푸시 재시도 대기 상태입니다.",
        queuedAt: settlementNotificationCreatedAt,
        updatedAt: settlementNotificationCreatedAt + (5 * 60 * 1000),
      },
    },
    {
      collection: "adminActionDeliveryJobs",
      path: "adminActionDeliveryJobs/admin-job-seed-support-push",
      data: {
        deliveryId: "admin-delivery-seed-support-push",
        notificationId: "admin-notification-seed-support",
        sourceType: "SUPPORT",
        trigger: "notification_created",
        channel: "app_push",
        requestId: "",
        inquiryId: "support-seed-received",
        title: "매니저 문의 접수",
        body: "관리자 앱 푸시 확인 대기 중입니다.",
        targetLabel: "관리자 앱",
        messagePreview: "매니저 문의 접수 - 관리자 앱 푸시 확인 대기 중입니다.",
        recipientRole: "ADMIN",
        recipientUserIds: [],
        state: "PENDING",
        deliveryAttempts: 0,
        maxAttempts: 3,
        lastDeliverySource: "",
        lastError: "",
        queuedAt: supportNotificationCreatedAt,
        updatedAt: supportNotificationCreatedAt,
      },
    },
    {
      collection: "appointmentReminderJobs",
      path: "appointmentReminderJobs/reminder-seed-requested-d3",
      data: {
        appointmentRequestId: "request-seed-requested",
        reminderStage: "D3",
        templateKey: "appointment_d3",
        channel: "KAKAO_ALIMTALK",
        state: "PENDING",
        reminderDateKey: formatDateKey(requestedAppointmentAtMillis - (3 * DAY_IN_MILLIS)),
        appointmentDateKey: formatDateKey(requestedAppointmentAtMillis),
        recipientUserIds: [patient.uid, guardian.uid],
        messagePreview: `${primaryGuide.hospitalName} ${primaryGuide.departmentName} 예약이 3일 남았습니다. 만남 장소와 이동 경로를 다시 확인해 주세요.`,
        queuedAt: now,
        updatedAt: now,
      },
    },
  ];

  return {
    scenarios: [
      {
        label: "예약 대기",
        requestId: "request-seed-requested",
        summary: "보호자가 등록한 향후 예약 요청",
      },
      {
        label: "진행 중 동행",
        requestId: "request-seed-progress",
        summary: "매니저가 배정되어 병원 대기 중인 동행 세션",
      },
      {
        label: "종료 후속 처리",
        requestId: "request-seed-completed",
        summary: "리포트, 후기, 정산 재확인, SOS 후속이 남아 있는 완료 요청",
      },
    ],
    documents,
  };
}

function requireRoleUser(usersByRole, role) {
  const user = usersByRole.get(role);
  if (!user) {
    throw new Error(`${role} 기준선 사용자를 찾지 못했습니다.`);
  }
  return user;
}

function buildAppointmentRequest({
  id,
  patient,
  guardian,
  manager,
  hospitalName,
  departmentName,
  appointmentAtMillis,
  meetingPlace,
  specialNotes,
  patientConditionSummary,
  medicationSummary,
  mobilitySupportCode,
  tripTypeCode,
  managerGenderPreferenceCode,
  paymentMethodCode,
  couponCode,
  basePrice,
  optionSurchargePrice,
  couponDiscountPrice,
  finalPrice,
  paymentStatusCode,
  paymentApprovalCode,
  paymentApprovedAt,
  paymentProviderLabel,
  requesterUserId,
  requesterRole,
  requesterName,
  requesterPhone,
  status,
  createdAt,
  updatedAt,
}) {
  return {
    requestId: id,
    patientUserId: patient.uid,
    guardianUserId: guardian.uid,
    managerUserId: manager ? manager.uid : null,
    patientName: patient.name,
    patientPhone: patient.phone,
    patientEmail: patient.email,
    guardianName: guardian.name,
    guardianPhone: guardian.phone,
    guardianEmail: guardian.email,
    hospitalName,
    departmentName,
    appointmentAt: formatAppointmentAt(appointmentAtMillis),
    appointmentAtEpochMillis: appointmentAtMillis,
    appointmentDateKey: formatDateKey(appointmentAtMillis),
    meetingPlace,
    specialNotes,
    patientConditionSummary,
    medicationSummary,
    mobilitySupportCode,
    tripTypeCode,
    managerGenderPreferenceCode,
    paymentMethodCode,
    couponCode,
    basePrice,
    optionSurchargePrice,
    couponDiscountPrice,
    finalPrice,
    paymentStatusCode,
    paymentApprovalCode,
    paymentApprovedAt,
    paymentProviderLabel,
    reminderStages: ["D7", "D3", "D1"],
    status,
    requesterUserId,
    requesterRole,
    requesterName,
    requesterPhone,
    createdAt,
    updatedAt,
  };
}

function printPlan(options, projectId, baselineUsers, collectionCounts, sampleState) {
  console.log("보들 Firebase 샘플 서비스 데이터 계획");
  console.log(`프로젝트: ${projectId}`);
  console.log(`모드: ${options.apply ? "apply" : "dry-run"}`);
  console.log("");
  console.log("기준선 사용자:");
  for (const baselineUser of baselineUsers) {
    console.log(`- ${baselineUser.role} ${baselineUser.email} (${baselineUser.uid})`);
  }
  console.log("");
  console.log("추가할 샘플 시나리오:");
  for (const scenario of sampleState.scenarios) {
    console.log(`- ${scenario.label}: ${scenario.summary} (${scenario.requestId})`);
  }
  console.log("");
  console.log("영향 컬렉션 현재 문서 수:");
  for (const collectionName of SAMPLE_COLLECTIONS) {
    console.log(`- ${collectionName}: ${collectionCounts[collectionName]}건`);
  }
  console.log("");
  console.log(`예정 문서 upsert 수: ${sampleState.documents.length}건`);
}

function buildLocalTimeFromNow(dayOffset, hour, minute) {
  const date = new Date();
  date.setDate(date.getDate() + dayOffset);
  date.setHours(hour, minute, 0, 0);
  return date.getTime();
}

function formatDateKey(epochMillis) {
  const date = new Date(epochMillis);
  const year = String(date.getFullYear());
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatAppointmentAt(epochMillis) {
  const date = new Date(epochMillis);
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return `${formatDateKey(epochMillis)} ${hours}:${minutes}`;
}

function toIsoString(epochMillis) {
  return new Date(epochMillis).toISOString();
}

main().catch((error) => {
  console.error("샘플 서비스 데이터 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
