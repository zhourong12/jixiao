# dev-team 绩效全链路回归检查表

在缺陷修复或发版前按阶段勾选；与 `npm test`、`npm run test:integration`（需 MySQL + `JIXIAO_INTEGRATION=1`）结果一并归档。

## 环境

- [x] 已执行迁移与种子：`seed-default-user.sql`、`seed-demo-employees-extra.sql`、`seed-devteam-test-personas.sql`、`sync-menu-table.sql`
- [x] 前后端可访问；集成测试库与本地服务使用同一套 `MYSQL_*`

## 阶段 A（API / 隔离）

- [x] `GET /api/performances`：A2 列表不含 A1 记录；超管含 `canExport` / `canBatchCreate`；`qa_admin` 二者为 false
- [x] `GET /api/performances/:id`：无关用户 403
- [x] `qa_admin`：`POST /api/performances`、`GET .../export`、`GET .../calibration/supervisor-queue` 均为 403
- [x] 超管导出 200

## 阶段 B（状态机）

- [x] 无虚线：至 `completed`
- [x] 有虚线：`self` 后 `dual_manager_review`，双方评分后 `final_review`
- [x] 目标驳回：拒绝后再提交回到 `goal_pending_review`

## 阶段 C（UI 手工）

- [x] 按角色 E→D→B/C→A 轮次点击侧栏与计划中的关键按钮；无白屏、无未处理控制台错误
- [ ] （可选）构建后访问 `client-vue` 产物：主要路由返回静态 HTML，会话经 Java `jx_session` Cookie

## 签字

| 轮次 | 执行人 | 日期 | 备注 |
|------|--------|------|------|
| dev-team 全量 | 自动化 | 2026-05-12 | `npm run test:devteam`（API + Newman + Playwright） |
