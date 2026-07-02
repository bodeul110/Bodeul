import {getServerConfig} from "./config.js";
import {createPostgresClient} from "./database.js";
import {createFirebaseAdminVerifier} from "./firebase-admin.js";
import {createPostgresAdminRoleAuthorizer} from "./authorization.js";
import {createPostgresHospitalGuideReader} from "./hospital-guides.js";
import {createApiServer} from "./server.js";

const config = getServerConfig(process.env);
const firebaseVerifier = createVerifierOrExit(process.env);
const postgresClient = createPostgresClient(process.env);
const adminRoleAuthorizer = createPostgresAdminRoleAuthorizer(postgresClient);
const hospitalGuideReader = createPostgresHospitalGuideReader(postgresClient);
const server = createApiServer({env: process.env, firebaseVerifier, adminRoleAuthorizer, hospitalGuideReader});

if (!postgresClient) {
  console.log("DATABASE_URL 설정이 없어 PostgreSQL client를 초기화하지 않습니다.");
}

server.listen(config.port, config.host, () => {
  console.log(`bodeul-api 서버가 http://${config.host}:${config.port} 에서 실행 중입니다.`);
});

function shutdown(signal: NodeJS.Signals): void {
  console.log(`${signal} 신호를 받아 bodeul-api 서버를 종료합니다.`);
  server.close((error) => {
    if (error) {
      console.error("bodeul-api 서버 종료 중 오류가 발생했습니다.", error);
      process.exitCode = 1;
    }
    void closePostgresAndExit();
  });
}

function createVerifierOrExit(env: NodeJS.ProcessEnv) {
  try {
    const verifier = createFirebaseAdminVerifier(env);
    if (!verifier) {
      console.log("Firebase Admin SDK 설정이 없어 관리자 API 인증 요청은 503으로 처리됩니다.");
    }
    return verifier;
  } catch (error) {
    const message = error instanceof Error ? error.message : "알 수 없는 오류";
    console.error("Firebase Admin SDK 설정을 초기화하지 못했습니다.", message);
    process.exit(1);
  }
}

async function closePostgresAndExit(): Promise<void> {
  try {
    await postgresClient?.close();
  } catch {
    console.error("PostgreSQL client 종료 중 오류가 발생했습니다.");
    process.exitCode = 1;
  } finally {
    process.exit();
  }
}

process.once("SIGINT", shutdown);
process.once("SIGTERM", shutdown);
