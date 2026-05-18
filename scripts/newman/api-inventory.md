# jixiao2 后端 API 清单（JSON / 会话）

> 实现位于 `server-java` 各 `*Controller.java`；与 Newman 集合对齐。运行中完整路由表见 **`GET /actuator/mappings`**（需 actuator 已暴露 `mappings`，见 `application.yml`）。

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/actuator/mappings` | 无 | Spring 已注册的全部 `RequestMapping`（自检用；生产可关闭） |
| GET | `/actuator/health` | 无 | 健康检查 |
| POST | `/auth/password/login` | 无 | Body: `{ username, password }`，Set-Cookie `jx_session` |
| GET | `/auth/feishu/subjects` | 无 | 返回可登录飞书主体列表 `{ items: { code, name }[] }`（无密钥） |
| GET | `/auth/feishu/login` | 无 | Query: **`subjectCode`**（有值时跳转飞书授权）、`next`（可选站内路径）；缺 `subjectCode` 时 **302** → `/login?login_error=...` |
| GET | `/auth/feishu/callback` | 无 | OAuth 回调（后端 redirect_uri 时使用） |
| GET | `/auth/feishu/exchange` | 无 | Query: `code`, `state`；浏览器经代理访问，与登录同源 Cookie 换票写会话 |
| GET | `/auth/feishu/logout` | 无 | 退出飞书会话 |
| GET | `/api/session/me` | Cookie | 当前用户 + 菜单 |
| POST | `/api/session/logout` | Cookie | 清除 Cookie |
| GET | `/api/home/todos` | Cookie | Query: `year`, `month` 可选；**仅与当前用户相关流程节点的绩效待办**（本人模板/目标/自评/结果确认、作为直属/虚线上级的目标验收与评分等）；`final_review` 仅当用户具备 **`performance_review_admin` 菜单**、**`super_admin` 角色**、或**员工管理中配置的绩效校准负责人**之一时，额外包含当月全部「待终审/校准」记录（全员列表见绩效列表）。不再因 `admin` 角色而返回全员活跃待办。 |
| GET | `/api/menu-permissions/me` | Cookie | 有效菜单 |
| GET | `/api/admin/menu-permissions/matrix` | Cookie | 权限矩阵 |
| PUT | `/api/admin/menu-permissions` | Cookie | Body: `UpdateMenuPermissionsBody` |
| GET | `/api/admin/menu-permissions/roles` | Cookie | RBAC 角色列表 |
| POST | `/api/admin/menu-permissions/roles` | Cookie | Body: `CreateRbacRoleRequest` |
| PATCH | `/api/admin/menu-permissions/roles/:roleKey` | Cookie | Body: `UpdateRbacRoleRequest` |
| DELETE | `/api/admin/menu-permissions/roles/:roleKey` | Cookie | 删除自定义角色 |
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
| POST | `/api/employees/sync-from-lark` | Cookie | Body: `{ clearExisting: boolean, subjectCode?: string, feishuSubjectCode?: string }`；需 `admin_employees`；**须指定主体 code** |
| GET | `/api/performances` | Cookie | Query: `status`（可多选，逗号分隔，如 `manager_review,self_review`）、`focus`、`period`、**`subjectCode`**（飞书主体）、`departmentId`、`employeeName`、`page`、`pageSize`；`items[]` 含 `totalScore`、`scoreGrade`（S/A/B/C，有总分时） |
| GET | `/api/performances/create/month-periods` | Cookie | 批量创建用月度 |
| GET | `/api/performances/filter/month-periods` | Cookie | 列表/校准筛选用月度；需 `performance_list` 或 `admin_performance_calibration` |
| GET | `/api/performances/export` | Cookie | 导出，Query 同列表筛选（含 **`subjectCode`**）；`items[]` 含 `totalScore`、`scoreGrade` |
| GET | `/api/performances/calibration/supervisor-queue` | Cookie | 仅 **super_admin**；Query: `period`, **`subjectCode`**, `departmentId`, `employeeName`, `page`, `pageSize`；**仅 `status=final_review`（待校准）**；`items[]` 含 `totalScore`、`scoreGrade` |
| GET | `/api/performances/:id` | Cookie | 详情；含 `totalScore`、`scoreGrade`（与列表规则一致）；含 **`calibrationAssignees`**：`{ employeeId, name }[]`（员工管理中配置的绩效校准负责人，供流程节点展示） |
| DELETE | `/api/performances/:id` | Cookie | 软删除绩效记录 |
| PATCH | `/api/performances/:id` | Cookie | 草稿 Body: `reviewType`, `content`, `personalSummary?` |
| POST | `/api/performances` | Cookie | 批量创建 `CreatePerformanceRequest`；**须 `subjectCode`（或 `feishuSubjectCode`）**、**`period`**、**`scoringSchemeId`**；**`assessmentRuleId` 已废弃**（忽略请求体中的值），每名待创建员工使用其 **`employee_hierarchy.assessment_rule_id`**（须非空且为启用中的规则）；**`employeeIds`（飞书 user_id）** 与 **`employeeNames`**、**`departmentName`** 可组合去重后以 `employee_id` 创建；**仅按姓名且同主体下有多人同名时该条失败**（须改用 `employeeIds`）；可选 **`templateId`**；员工解析限定在该主体 |
| POST | `/api/performances/:id/select-template` | Cookie | `SelectTemplateRequest` |
| POST | `/api/performances/:id/submit` | Cookie | 提交 `reviewType` + `content` |
| POST | `/api/performances/:id/reject` | Cookie | `{ reason }` |
| POST | `/api/performances/:id/approve-goal` | Cookie | `ApproveGoalRequest` |
| POST | `/api/performances/:id/final-review` | Cookie | `FinalReviewRequest` |
| POST | `/api/performances/:id/calibrate` | Cookie | `CalibrationReviewRequest` |
| POST | `/api/performances/:id/confirm-result` | Cookie | — |
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
| GET | `/api/admin/performance-feishu-task-config` | Cookie | 须 `admin_performance_feishu_task`；`enabled`（总开关）；`items[]`: `nodeKey`, `name`, `dueDays`, `sortOrder` |
| PATCH | `/api/admin/performance-feishu-task-config` | Cookie | `{ enabled?: boolean, items?: [{ nodeKey, dueDays }] }` |
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
