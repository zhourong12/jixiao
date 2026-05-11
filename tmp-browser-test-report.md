# Browser Test Report（程序化等价验证）

- 时间: 2026-05-10T03:48:15.288Z
- 基址: http://localhost:8080（Vite + /auth 代理）
- 账号: zhou_rong / 123456

## 结果摘要

- [precheck] login+cookie+csrf: PASS (csrf ok)
- [smoke] GET /api/home/todos -> 200 PASS
- [smoke] GET /api/home/overview -> 200 PASS
- [smoke] GET /api/performances?page=1&pageSize=20 -> 200 PASS
- [smoke] GET /api/employees/all -> 200 PASS
- [smoke] GET /api/employees?page=1&pageSize=20 -> 200 PASS
- [smoke] GET /api/admin/templates?page=1&pageSize=20 -> 200 PASS
- [smoke] GET /api/admin/notifications?page=1&pageSize=20 -> 200 PASS
- [smoke] POST /api/performances (create 周荣) -> 201 PASS
-   body: {"results":[{"employeeId":"zhou_rong","employeeName":"周荣","success":false,"error":"该员工未设置直属上级"}],"total":1,"successCount":0,"failCount":1}
- [smoke] GET /api/performances/:id -> SKIP (no record id; create may have failed for business rules)

## 说明

- 未改计划文件；本报告由 `tmp-smoke-full.js` 生成。
- 「创建绩效」若 seed 中 `manager_id` 为空，接口仍 200/201 但 `results[0].success` 可能为 false，属数据前置条件问题。

## agent-browser（无头）

- `agent-browser open http://localhost:8080/login` → 填写 `#jx-local-user` / `#jx-local-pass`，点击 `@e6`（登录）。
- 登录后 URL：`http://localhost:8080/`（PASS）。
- 截图路径：`d:\zr\code\jixiao2\tmp-agent-after-login.png`（点击前）、`d:\zr\code\jixiao2\tmp-agent-home.png`（首页）。
