# jixiao2 后端 API 清单（JSON / 会话）

> 实现位于 `server-java` 各 `*Controller.java`；与 Newman 集合对齐。运行中完整路由表见 **`GET /actuator/mappings`**（需 actuator 已暴露 `mappings`，见 `application.yml`）。

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/actuator/mappings` | 无 | Spring 已注册的全部 `RequestMapping`（自检用；生产可关闭） |
| GET | `/actuator/health` | 无 | 健康检查 |
| POST | `/auth/password/login` | 无 | Body: `{ username, password }`，Set-Cookie `jx_session`；须 `system_config.password_login_enabled=1` |
| GET | `/auth/feishu/subjects` | 无 | `{ items: { code, name }[], passwordLoginEnabled }`（无密钥） |
| GET | `/auth/feishu/login` | 无 | Query: **`subjectCode`**（有值时跳转飞书授权）、`next`（可选站内路径）；缺 `subjectCode` 时 **302** → `/login?login_error=...` |
| GET | `/auth/feishu/callback` | 无 | OAuth 回调（后端 redirect_uri 时使用） |
| GET | `/auth/feishu/exchange` | 无 | Query: `code`, `state`；浏览器经代理访问，与登录同源 Cookie 换票写会话 |
| GET | `/auth/feishu/logout` | 无 | 退出飞书会话 |
| GET | `/api/session/me` | Cookie/Bearer | 当前用户 + 菜单 |
| POST | `/api/session/logout` | Cookie | 清除 Cookie |
| GET | `/api/home/todos` | Cookie | Query: `year`, `month` 可选；本人/团队相关待办，另含创建人负责的 **`plan_execution`**、**`final_review`**（流程待办）；飞书角标计数**不含**后两类 |
| GET | `/api/menu-permissions/me` | Cookie | 有效菜单 |
| GET | `/api/admin/menu-permissions/matrix` | Cookie | 权限矩阵 |
| PUT | `/api/admin/menu-permissions` | Cookie | Body: `UpdateMenuPermissionsBody` |
| GET | `/api/admin/menu-permissions/roles` | Cookie | RBAC 角色列表 |
| POST | `/api/admin/menu-permissions/roles` | Cookie | Body: `CreateRbacRoleRequest` |
| PATCH | `/api/admin/menu-permissions/roles/:roleKey` | Cookie | Body: `UpdateRbacRoleRequest` |
| DELETE | `/api/admin/menu-permissions/roles/:roleKey` | Cookie | 删除自定义角色 |
| GET | `/api/admin/api-tokens` | Cookie/Bearer | 须 `admin_api_tokens`；列出当前用户创建的 API Token（不返回明文） |
| POST | `/api/admin/api-tokens` | Cookie/Bearer | 须 `admin_api_tokens`；Body `{ name, expiresAt? }`，返回明文 `token`（仅一次） |
| DELETE | `/api/admin/api-tokens/:id` | Cookie/Bearer | 须 `admin_api_tokens`；删除当前用户创建的 Token |
| GET | `/api/admin/auth-config` | Cookie | **兼容**；须 `admin_performance_feishu_task`；`{ passwordLoginEnabled }` |
| PATCH | `/api/admin/auth-config` | Cookie | **兼容**；须 `admin_performance_feishu_task`；`{ passwordLoginEnabled: boolean }` |
| GET | `/api/employees` | Cookie | Query: `page`, `pageSize`, `keyword`；**`subjectCode`**、**`departmentId`**（可选，与 `/all` 相同主体/部门筛选语义）；列表项含 `assessmentRuleId` / `assessmentRuleName`（员工档案绑定的考核规则） |
| POST | `/api/employees` | Cookie | Body: `CreateEmployeeRequest`（可选 **`assessmentRuleId`**，须为启用中的规则） |
| PUT | `/api/employees/:id` | Cookie | Body: `UpdateEmployeeRequest`（可选 **`assessmentRuleId`**，传空字符串可清空绑定） |
| DELETE | `/api/employees/:id` | Cookie | |
| PUT | `/api/employees/:id/hierarchy` | Cookie | Body: `managerId`, `dottedManagerId`, `departmentId`, `departmentName` |
| GET | `/api/employees/departments` | Cookie | |
| GET | `/api/employees/department-options` | Cookie | Query: **`subjectCode`**（可选）；传入时仅返回该飞书主体下部门（`code=default` 时含 `feishu_subject_id` 为空的旧数据） |
| GET | `/api/employees/department-tree` | Cookie | 按飞书主体分组的部门树（筛选用，数据来自 `org_department`）；`items[]` 含 `subjectCode`、`subjectName`、`departments[]`（`id`/`name`） |
| GET | `/api/admin/departments` | Cookie | 须 `admin_departments`；Query: `page`, `pageSize`, `subjectCode`, `keyword` |
| POST | `/api/admin/departments` | Cookie | 须 `admin_departments`；Body: `subjectCode`, `name`, `sortOrder`（可选） |
| POST | `/api/admin/departments/sync-from-employees` | Cookie | 须 `admin_departments`；从员工档案汇总写入 `org_department` |
| PATCH | `/api/admin/departments/:id` | Cookie | 须 `admin_departments`；Body: `name`, `sortOrder` |
| DELETE | `/api/admin/departments/:id` | Cookie | 须 `admin_departments`；软删除（`enabled=0`） |
| GET | `/api/employees/role-options` | Cookie | |
| GET | `/api/employees/all` | Cookie | Query: **`subjectCode`**（可选）；传入时仅返回该飞书主体下员工（`code=default` 时含 `feishu_subject_id` 为空的旧数据）；`items[]` 含 `assessmentRuleId` / `assessmentRuleName` |
| GET | `/api/employees/calibration-assignees` | Cookie | 须 `admin_employees`；返回 `items[]`：`{ employeeId, name }`（系统配置的绩效校准负责人，按配置顺序） |
| PUT | `/api/employees/calibration-assignees` | Cookie | 须 `admin_employees`；Body: `{ employeeIds: string[] }`（可为空数组，表示未指定负责人） |
| GET | `/api/employees/feishu-user-options` | Cookie | Query: **`subjectCode`**；需 `admin_employees`；一次性返回通讯录用户列表（响应含 `total`、`truncated` 可选）；`items[]` 每条含 `feishuOpenId`、`name`、`feishuSubjectCode`，可选 `employeeNo`、`departmentName`；前端本地按姓名/open_id 过滤 |
| GET | `/api/employees/feishu-user-profile` | Cookie | Query: **`subjectCode`**、**`openId`**（飞书 open_id）；需 `admin_employees`；返回 `employeeNo`、`departmentName`、`mobile` 字符串（无则空），用于选人后补全表单 |
| POST | `/api/employees/sync-from-lark` | Cookie | Body: `{ clearExisting: boolean, subjectCode?: string, feishuSubjectCode?: string }`；需 `admin_employees`；**须指定主体 code**（仅新增，已存在跳过） |
| POST | `/api/employees/sync-from-feishu` | Cookie | Body: `{ subjectCodes?: string[] }`（可选，缺省同步全部已配置主体）；需 `admin_employees`；先拉通讯录，再按 Directory `employees/filter` 的 `base_info.subscription_ids` 过滤有席位人员后 upsert；响应含 `totalCount`、`seatAssignedCount`、`skippedNoSeatCount`；应用须开通 `directory:employee:list` 与 `directory:employee.base.subscription_ids:read` |
| GET | `/api/performances` | Cookie | Query: `status`（可多选，逗号分隔，如 `manager_review,self_review`）、`focus`、`period`、**`subjectCode`**（飞书主体）、`departmentId`、`employeeName`、`page`、`pageSize`；`items[]` 含 `totalScore`、`scoreGrade`（S/A/B/C，有总分时）、`canIssueSelfReview`；响应 `canBatchIssueSelfReview` |
| GET | `/api/performances/create/month-periods` | Cookie | 批量创建用月度 |
| GET | `/api/performances/filter/month-periods` | Cookie | 列表/校准筛选用月度；需 `performance_list` 或 `admin_performance_calibration` |
| GET | `/api/performances/export` | Cookie | 导出，Query 同列表筛选（含 **`subjectCode`**）；`items[]` 含 `totalScore`、`scoreGrade` |
| GET | `/api/performances/calibration/supervisor-queue` | Cookie | 需 **`performance_review_admin`** 或 **`performance_batch_create`**；Query: `period`, **`subjectCode`**, `departmentId`, `employeeName`, `page`, `pageSize`；**仅 `status=final_review` 且当前用户为创建人/校准负责人**；`items[]` 含 `totalScore`、`scoreGrade` |
| GET | `/api/performances/:id` | Cookie | 详情；含 `totalScore`、`scoreGrade`（与列表规则一致）；含被考核人 **`feishuSubjectName`** / **`feishuSubjectCode`**（飞书主体）；含 **`calibrationAssignees`**：`{ employeeId, name }[]`（员工管理中配置的绩效校准负责人，供流程节点展示）；含 **`canIssueSelfReview`**（计划执行中且当前用户为该条创建人时可下发员工自评）；含 **`canCalibrate`**（`final_review` 且当前用户为该条创建人/校准负责人时可校准） |
| DELETE | `/api/performances/:id` | Cookie | 软删除绩效记录 |
| PATCH | `/api/performances/:id` | Cookie | 草稿 Body: `reviewType`, `content`, `personalSummary?` |
| POST | `/api/performances` | Cookie | 批量创建 `CreatePerformanceRequest`；**须 `subjectCode`（或 `feishuSubjectCode`）**、**`period`**、**`scoringSchemeId`**；**`assessmentRuleId` 已废弃**（忽略请求体中的值），每名待创建员工使用其 **`employee_hierarchy.assessment_rule_id`**（须非空且为启用中的规则）；**`employeeIds`（飞书 user_id）** 与 **`employeeNames`**、**`departmentName`** 可组合去重后以 `employee_id` 创建；**仅按姓名且同主体下有多人同名时该条失败**（须改用 `employeeIds`）；可选 **`templateId`**；员工解析限定在该主体 |
| POST | `/api/performances/:id/select-template` | Cookie | `SelectTemplateRequest` |
| POST | `/api/performances/:id/submit` | Cookie | 提交 `reviewType` + `content` |
| POST | `/api/performances/:id/reject` | Cookie | `{ reason }`；直属/虚线上级驳回下属自评 |
| POST | `/api/performances/ops/reject-self-review` | Cookie | 需 **super_admin**；Body `{ recordId, reason }`：超管驳回员工自评（总结不合格），退回 `self_review` |
| POST | `/api/performances/:id/approve-goal` | Cookie | `ApproveGoalRequest` |
| POST | `/api/performances/:id/final-review` | Cookie | `FinalReviewRequest` |
| POST | `/api/performances/:id/calibrate` | Cookie | `CalibrationReviewRequest`；须为该条创建人/校准负责人 |
| POST | `/api/performances/:id/confirm-result` | Cookie | — |
| POST | `/api/performances/ops/deadline-reminders` | Cookie | 需 **super_admin** 或 **`performance_review_admin`**（截止日飞书提醒，运维用） |
| POST | `/api/performances/ops/deadline-auto` | Cookie | 同上；Body **`{ "recordId", "forceAdvance": true }`**：不校验截止日，按当前状态推进一步（含 **final_review→issued**、issued→completed）；`forceAdvance` 为 false 或未传时与定时器一致，仅 **asOfDate 晚于** 节点截止日才流转（goal/scoring/final/confirm）；响应 `changes[]` |
| POST | `/api/performances/:id/rollback-plan-anchor` | Cookie | 仅 `plan_execution`；回退到 `deadline_flow_anchor`；Body 可选 `{ "deadline": "yyyy-MM-dd" }` 更新回退节点截止日 |
| POST | `/api/performances/:id/start-self-review` | Cookie | 已禁用：`plan_execution` 下员工不可自行开始自评（须创建人下发） |
| POST | `/api/performances/:id/issue-self-review` | Cookie | 仅该条绩效**创建人**；`plan_execution` → `self_review`（下发员工自评） |
| POST | `/api/performances/batch-issue-self-review` | Cookie | Body `{ recordIds: string[] }`；逐条校验权限；响应 `successCount`、`failed[]` |
| GET | `/api/admin/assessment-rules` | Cookie | 须具备「考核规则」或「模板管理」菜单；Query: `page`, `pageSize` |
| GET | `/api/admin/assessment-rules/:id` | Cookie | 同上 |
| POST | `/api/admin/assessment-rules` | Cookie | 创建规则；须「考核规则」菜单；`managerWeight` + `dottedManagerWeight` = **1** |
| PATCH | `/api/admin/assessment-rules/:id` | Cookie | |
| DELETE | `/api/admin/assessment-rules/:id` | Cookie | 有模板引用时不可删 |
| GET | `/api/admin/templates` | 无（列表） | Query: `page`, `pageSize` |
| GET | `/api/admin/templates/:id` | 无 | |
| POST | `/api/admin/templates` | Cookie | 创建模板；`indicators[].weight` 合计须为 **80**（考核规则在创建绩效时选择，不再绑定模板） |
| PATCH | `/api/admin/templates/:id` | Cookie | 若含 `indicators`，权重合计须为 **80** |
| POST | `/api/admin/templates/:id/toggle-status` | Cookie | |
| POST | `/api/admin/templates/:id/copy` | Cookie | |
| DELETE | `/api/admin/templates/:id` | Cookie | |
| GET | `/api/admin/notifications` | Cookie | 须 `admin_employees`；Query: `page`, `pageSize` |
| POST | `/api/admin/notifications` | Cookie | 须 `admin_employees`；`SendNotificationRequest`；**须 `subjectCode`（或 `feishuSubjectCode`）**；收件人仅该主体（`default` 含 `feishu_subject_id` 为空）；按员工主体分组换 tenant token 后发飞书私聊；响应含 `feishuSent` / `feishuFailed` |
| GET | `/api/admin/system-config` | Cookie | 须 `admin_employees` |
| PATCH | `/api/admin/system-config` | Cookie | 须 `admin_employees`；`{ configs: [{ key, value }] }` |
| GET | `/api/admin/platform-settings` | Cookie | 须 `admin_performance_feishu_task`；`appBadgeEnabled`、`feishuTaskEnabled`、`passwordLoginEnabled`、`feishuTaskItems[]` |
| PATCH | `/api/admin/platform-settings` | Cookie | `{ appBadgeEnabled?, feishuTaskEnabled?, passwordLoginEnabled?, feishuTaskItems?: [{ nodeKey, dueDays }] }` |
| GET | `/api/admin/performance-feishu-task-config` | Cookie | **兼容**；同 platform-settings 的待办部分：`enabled`、`items[]` |
| PATCH | `/api/admin/performance-feishu-task-config` | Cookie | **兼容**；`{ enabled?, items? }` |
| GET | `/api/feishu/app-badge/enabled` | Cookie | `{ enabled }`；系统配置角标总开关 |
| GET | `/api/feishu/jssdk-config` | Cookie | Query: **`url`**（当前页 URL，不含 hash）；返回 H5 JSSDK 鉴权 `appId`, `timestamp`, `nonceStr`, `signature` |
| POST | `/api/feishu/app-badge/sync` | Cookie | 按当前用户待办数同步飞书工作台角标（**不含**创建人 `plan_execution` / `final_review`）；开关关闭时 `{ success: false, badgeNum: 0, skipped: true }` |
| POST | `/api/feishu/badge-client-log` | Cookie | Body: `{ lines: string[] }`；写入服务端 `logs/feishu-badge-client.log`（H5 角标调试） |
| GET | `/api/admin/evaluation-periods` | Cookie | Query: `period_type` |
| POST | `/api/admin/evaluation-periods` | Cookie | `CreateEvaluationPeriodRequest`；**月度须 `parentPeriodId`（季度周期 id）** 且 `periodKey`（`YYYY-MM`）须落在该季度三个月内；季度勿传 `parentPeriodId` |
| PUT | `/api/admin/evaluation-periods/:id` | Cookie | `UpdateEvaluationPeriodRequest` |
| DELETE | `/api/admin/evaluation-periods/:id` | Cookie | |
| GET | `/api/admin/award-types` | Cookie | |
| GET | `/api/admin/evaluation/performance-periods` | Cookie | |
| GET | `/api/admin/evaluation/leaderboard` | Cookie | Query: `scope`, `key`, `departmentIds` |
| GET | `/api/admin/evaluation/leaderboard/quarter-detail` | Cookie | Query: `key`, `employeeId` |
| GET | `/api/admin/evaluation/awards` | Cookie | Query: `periodId` |
| POST | `/api/admin/evaluation/awards` | Cookie | `CreatePeriodAwardRequest` |
| DELETE | `/api/admin/evaluation/awards/:id` | Cookie | |
| GET | `/*` | 无 | SPA `index.html`（非 JSON） |

## Newman 集合覆盖说明

- 集合由 `build-collection.mjs` 生成，示例 Body 对齐 `client-vue/src/types/api.interface.ts`。
- `performance_id`、`evaluation_period_id`、`award_id` 等需在环境中填写，或由前置请求的 Tests 脚本赋值（当前集合对 `performance_id` 在「列表」接口自动取第一条）。
- 写库 / 删除类请求（员工、角色、评选周期等）可能因权限或数据冲突失败，属预期；可改用 `--folder` 只跑只读接口。
