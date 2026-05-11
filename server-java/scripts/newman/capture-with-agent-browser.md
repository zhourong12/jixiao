# 用 agent-browser 抓取真实 API 参数

本地先启动后端（并保证 `SESSION_JWT_SECRET` 已配置，否则账密登录无法签发 Cookie）。

## 1. 录制 HAR

```bash
npx agent-browser open http://127.0.0.1:3000/
npx agent-browser network har start
# 在浏览器中完成：账密登录 → 点击各菜单触发 XHR
npx agent-browser network har stop ./scripts/newman/capture.har
```

在 Postman / Insomnia 中导入 HAR，可复制 URL、Method、Body、Query 到集合；或对照 HAR 里 `postData.text` 修正 `scripts/newman/build-collection.mjs` 后重新 `node scripts/newman/build-collection.mjs`。

## 2. 查看单次请求详情

```bash
npx agent-browser network requests --filter api
npx agent-browser network request <requestId>
```

## 3. 与 Newman 对齐

- 登录接口：`POST /auth/password/login`，JSON `username` / `password`；响应 `Set-Cookie: jx_session=...`。
- 其余业务接口：请求头携带 `Cookie: jx_session=...`（集合中由登录用例的 Tests 脚本写入变量 `jx_session`）。

飞书 OAuth（`/auth/feishu/*`）为浏览器重定向流，不适合 Newman 直连，请继续用账密或从已登录浏览器导出 Cookie。
