const {onCall, HttpsError} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore} = require("firebase-admin/firestore");

const HTTP_FUNCTIONS_OPTIONS = {
  region: "asia-northeast3",
  cors: true,
  invoker: "public",
};

const APP_CHECK_ENFORCEMENT_ENABLED =
  `${process.env.ENABLE_APPCHECK_ENFORCEMENT ?? ""}`.trim() === "true";

const CALLABLE_FUNCTIONS_OPTIONS = {
  ...HTTP_FUNCTIONS_OPTIONS,
  // 앱 체크 강제 적용은 환경 변수로만 연다.
  enforceAppCheck: APP_CHECK_ENFORCEMENT_ENABLED,
};

const CLIENT_CREATABLE_ROLES = new Set(["PATIENT", "GUARDIAN", "MANAGER"]);

const kakaoCustomToken = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  const accessToken = `${request.data?.accessToken ?? ""}`.trim();
  const role = `${request.data?.role ?? ""}`.trim();

  if (!CLIENT_CREATABLE_ROLES.has(role)) {
    throw invalidArgument("허용되지 않은 사용자 역할입니다.");
  }

  if (!accessToken) {
    throw invalidArgument("카카오 access token이 필요합니다.");
  }

  const kakaoProfile = await fetchKakaoProfile(accessToken);
  const providerUserId = `${kakaoProfile.id ?? ""}`.trim();
  if (!providerUserId) {
    throw unauthenticated("카카오 사용자 정보를 확인하지 못했습니다.");
  }

  const firebaseToken = await getAuth().createCustomToken(`kakao_${providerUserId}`);
  return {
    firebaseToken,
    profile: {
      providerUserId,
      name: extractKakaoName(kakaoProfile),
      email: extractKakaoEmail(kakaoProfile, providerUserId),
      phone: extractKakaoPhone(kakaoProfile),
    },
  };
});

const naverCustomToken = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  const accessToken = `${request.data?.accessToken ?? ""}`.trim();
  const role = `${request.data?.role ?? ""}`.trim();

  if (!CLIENT_CREATABLE_ROLES.has(role)) {
    throw invalidArgument("허용되지 않은 사용자 역할입니다.");
  }

  if (!accessToken) {
    throw invalidArgument("네이버 access token이 필요합니다.");
  }

  const naverProfileResponse = await fetchNaverProfile(accessToken);
  const providerUserId = `${naverProfileResponse?.response?.id ?? ""}`.trim();
  if (!providerUserId) {
    throw unauthenticated("네이버 사용자 정보를 확인하지 못했습니다.");
  }

  const firebaseToken = await getAuth().createCustomToken(`naver_${providerUserId}`);
  return {
    firebaseToken,
    profile: {
      providerUserId,
      name: extractNaverName(naverProfileResponse),
      email: extractNaverEmail(naverProfileResponse, providerUserId),
      phone: extractNaverPhone(naverProfileResponse),
    },
  };
});

const resolveLinkedParticipant = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  const uid = sanitizeText(request.auth?.uid);
  if (!uid) {
    throw unauthenticated("로그인이 필요합니다.");
  }

  const callerSnapshot = await getFirestore().collection("users").doc(uid).get();
  const callerRole = sanitizeText(callerSnapshot.get("role"));
  if (!["PATIENT", "GUARDIAN", "ADMIN"].includes(callerRole)) {
    throw new HttpsError("permission-denied", "연결 계정 조회 권한이 없습니다.");
  }

  const expectedRole = sanitizeText(request.data?.expectedRole);
  if (!["PATIENT", "GUARDIAN"].includes(expectedRole)) {
    throw invalidArgument("연결할 사용자 유형이 올바르지 않습니다.");
  }

  const email = normalizeComparableEmail(request.data?.email);
  const phone = normalizeProfilePhone(request.data?.phone);
  if (!email && !phone) {
    return {participant: null};
  }

  const firestore = getFirestore();
  let matchedUser = null;
  if (email) {
    const emailSnapshot = await firestore.collection("users")
        .where("role", "==", expectedRole)
        .where("email", "==", email)
        .limit(1)
        .get();
    matchedUser = emailSnapshot.docs.length > 0 ? emailSnapshot.docs[0] : null;
  }

  if (!matchedUser && phone) {
    const phoneSnapshot = await firestore.collection("users")
        .where("role", "==", expectedRole)
        .where("phone", "==", phone)
        .limit(1)
        .get();
    matchedUser = phoneSnapshot.docs.length > 0 ? phoneSnapshot.docs[0] : null;
  }

  if (!matchedUser) {
    return {participant: null};
  }

  return {
    participant: {
      userId: matchedUser.id,
      role: sanitizeText(matchedUser.get("role")),
      name: sanitizeText(matchedUser.get("name")),
      email: sanitizeText(matchedUser.get("email")),
      phone: sanitizeText(matchedUser.get("phone")),
    },
  };
});

const findSocialDuplicateEmailProvider = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  const uid = sanitizeText(request.auth?.uid);
  if (!uid) {
    throw unauthenticated("로그인이 필요합니다.");
  }

  const signInProvider = sanitizeText(request.auth?.token?.firebase?.sign_in_provider);
  if (!signInProvider || signInProvider === "password") {
    throw new HttpsError("permission-denied", "소셜 로그인 계정만 중복 확인을 요청할 수 있습니다.");
  }

  const email = normalizeComparableEmail(request.data?.email);
  if (!email) {
    throw invalidArgument("이메일이 필요합니다.");
  }

  const duplicateSnapshot = await getFirestore().collection("users")
      .where("email", "==", email)
      .limit(5)
      .get();

  const duplicateDocument = duplicateSnapshot.docs.find((documentSnapshot) =>
    documentSnapshot.id !== uid,
  );

  if (!duplicateDocument) {
    return {duplicate: null};
  }

  return {
    duplicate: {
      userId: duplicateDocument.id,
      provider: sanitizeText(duplicateDocument.get("provider")) || "EMAIL",
    },
  };
});

const resolveAssignedManagerProfile = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  const uid = sanitizeText(request.auth?.uid);
  if (!uid) {
    throw unauthenticated("로그인이 필요합니다.");
  }

  const requestId = sanitizeText(request.data?.requestId);
  if (!requestId) {
    throw invalidArgument("예약 요청 ID가 필요합니다.");
  }

  const firestore = getFirestore();
  const callerSnapshot = await firestore.collection("users").doc(uid).get();
  const callerRole = sanitizeText(callerSnapshot.get("role"));
  if (!["PATIENT", "GUARDIAN", "MANAGER", "ADMIN"].includes(callerRole)) {
    throw new HttpsError("permission-denied", "매니저 정보를 조회할 권한이 없습니다.");
  }

  const requestSnapshot = await firestore.collection("appointmentRequests").doc(requestId).get();
  if (!requestSnapshot.exists) {
    throw invalidArgument("예약 요청 정보를 확인하지 못했습니다.");
  }

  const patientUserId = sanitizeText(requestSnapshot.get("patientUserId"));
  const guardianUserId = sanitizeText(requestSnapshot.get("guardianUserId"));
  const managerUserId = sanitizeText(requestSnapshot.get("managerUserId"));
  const isParticipant = uid === patientUserId || uid === guardianUserId || uid === managerUserId;
  if (!isParticipant && callerRole !== "ADMIN") {
    throw new HttpsError("permission-denied", "매니저 정보를 조회할 권한이 없습니다.");
  }

  if (!managerUserId) {
    return {manager: null};
  }

  const managerSnapshot = await firestore.collection("users").doc(managerUserId).get();
  if (!managerSnapshot.exists || sanitizeText(managerSnapshot.get("role")) !== "MANAGER") {
    return {manager: null};
  }

  return {
    manager: {
      userId: managerSnapshot.id,
      role: "MANAGER",
      name: sanitizeText(managerSnapshot.get("name")),
      email: sanitizeText(managerSnapshot.get("email")),
      phone: sanitizeText(managerSnapshot.get("phone")),
    },
  };
});

async function fetchKakaoProfile(accessToken) {
  const response = await fetch("https://kapi.kakao.com/v2/user/me", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/x-www-form-urlencoded;charset=utf-8",
    },
  });

  let responseBody = {};
  try {
    responseBody = await response.json();
  } catch (error) {
    logger.error("카카오 응답 JSON 파싱 실패", error);
  }

  if (!response.ok) {
    logger.error("카카오 사용자 정보 조회 실패", {
      status: response.status,
      body: responseBody,
    });
    throw unauthenticated("카카오 로그인 정보가 유효하지 않거나 만료되었습니다.");
  }

  return responseBody;
}

async function fetchNaverProfile(accessToken) {
  const response = await fetch("https://openapi.naver.com/v1/nid/me", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  let responseBody = {};
  try {
    responseBody = await response.json();
  } catch (error) {
    logger.error("네이버 응답 JSON 파싱 실패", error);
  }

  if (!response.ok) {
    logger.error("네이버 사용자 정보 조회 실패", {
      status: response.status,
      body: responseBody,
    });
    throw unauthenticated("네이버 로그인 정보가 유효하지 않거나 만료되었습니다.");
  }

  if (`${responseBody?.resultcode ?? ""}` !== "00") {
    logger.error("네이버 사용자 정보 응답 오류", responseBody);
    throw unauthenticated("네이버 사용자 정보를 확인하지 못했습니다.");
  }

  return responseBody;
}

function extractKakaoName(kakaoProfile) {
  const nickname = kakaoProfile?.kakao_account?.profile?.nickname;
  if (typeof nickname === "string" && nickname.trim()) {
    return nickname.trim();
  }
  return "카카오 사용자";
}

function extractKakaoEmail(kakaoProfile, providerUserId) {
  const email = kakaoProfile?.kakao_account?.email;
  if (typeof email === "string" && email.trim()) {
    return email.trim();
  }
  return `kakao_${providerUserId}@bodeul.local`;
}

function extractKakaoPhone(kakaoProfile) {
  const phone = kakaoProfile?.kakao_account?.phone_number;
  if (typeof phone === "string" && phone.trim()) {
    return phone.trim();
  }
  return "";
}

function extractNaverName(naverProfileResponse) {
  const name = naverProfileResponse?.response?.name;
  if (typeof name === "string" && name.trim()) {
    return name.trim();
  }

  const nickname = naverProfileResponse?.response?.nickname;
  if (typeof nickname === "string" && nickname.trim()) {
    return nickname.trim();
  }
  return "네이버 사용자";
}

function extractNaverEmail(naverProfileResponse, providerUserId) {
  const email = naverProfileResponse?.response?.email;
  if (typeof email === "string" && email.trim()) {
    return email.trim();
  }
  return `naver_${providerUserId}@bodeul.local`;
}

function extractNaverPhone(naverProfileResponse) {
  const mobileE164 = naverProfileResponse?.response?.mobile_e164;
  if (typeof mobileE164 === "string" && mobileE164.trim()) {
    return mobileE164.trim();
  }

  const mobile = naverProfileResponse?.response?.mobile;
  if (typeof mobile === "string" && mobile.trim()) {
    return mobile.trim();
  }
  return "";
}

function normalizeComparableEmail(value) {
  return sanitizeText(value).toLowerCase();
}

function normalizeProfilePhone(value) {
  const normalizedValue = sanitizeText(value);
  if (!normalizedValue) {
    return "";
  }

  let digits = normalizedValue.replace(/\D+/g, "");
  if (!digits) {
    return "";
  }

  if (digits.startsWith("82") && digits.length >= 11) {
    digits = `0${digits.slice(2)}`;
  }
  return digits;
}

function sanitizeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

function invalidArgument(message) {
  return new HttpsError("invalid-argument", message, {message});
}

function unauthenticated(message) {
  return new HttpsError("unauthenticated", message, {message});
}

module.exports = {
  kakaoCustomToken,
  naverCustomToken,
  resolveLinkedParticipant,
  findSocialDuplicateEmailProvider,
  resolveAssignedManagerProfile,
};
