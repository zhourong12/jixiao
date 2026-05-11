# Newman API 回归

**端口**：`local.postman_environment.json` 里的 `baseUrl` 必须是**后端 API**地址，默认 `http://localhost:3000`（与开发环境 `0.0.0.0` 监听一致，避免仅绑定 IPv6 时 `127.0.0.1` 连不上）。前端开发服务器（如 Vite **8080**）只负责页面与代理，**不要把 Newman 的 baseUrl 改成 8080**。

1. 启动后端：`npm run dev:server` 或生产 `npm start`（脚本内已带 `FORCE_AUTHN_INNERAPI_DOMAIN`，并加载根目录 `.env`），确认日志出现 **`Server running on`** 后再跑 Newman。并配置可用的数据库连接（与 `.env` 中 MySQL 等一致）。
2. 配置 `SESSION_JWT_SECRET`（账密登录签发 Cookie 需要）。默认账密见 `local.postman_environment.json`（`zhou_rong` / `123456`）。
3. 生成/更新集合：`npm run newman:build`
4. 跑全部请求：`npm run test:newman`  
   - 服务未启动时会因连接失败退出码非 0，属正常。
5. 只跑某目录：`npx newman run scripts/newman/jixiao2-api.postman_collection.json -e scripts/newman/local.postman_environment.json --folder "02 Home"`
6. 用 agent-browser 从真实操作校准 Body/Query：`capture-with-agent-browser.md`
7. 全量路径表：`api-inventory.md`
