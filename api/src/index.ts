import {getServerConfig} from "./config.js";
import {createApiServer} from "./server.js";

const config = getServerConfig(process.env);
const server = createApiServer({env: process.env});

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
    process.exit();
  });
}

process.once("SIGINT", shutdown);
process.once("SIGTERM", shutdown);
