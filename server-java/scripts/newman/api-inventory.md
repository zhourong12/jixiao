# jixiao2 后端 API 清单（JSON / 会话）

> 与 `server/modules/**/*controller.ts` 一致；HTML 视图与飞书 OAuth 未列入 Newman 默认跑法。

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/auth/password/login` | 无 | Body: `{ username, password }`，Set-Cookie `jx_session` |
| GET | `/auth/feishu/login` | 无 | 飞书跳转，浏览器用 |
| GET | `/auth/feishu/callback` | 无 | OAuth 回调 |
| GET | `/auth/feishu/logout` | 无 | 退出飞书会话 |
| GET | `/api/session/me` | Cookie | 当前用户 + 菜单 |
| POST | `/api/session/logout` | Cookie | 清除 Cookie |
| GET | `/api/home/todos` | Cookie | Query: `year`, `month` 可选 |
| GET | `/api/home/overview` | Cookie | 同上 |
| GET | `/api/home/action-counts` | Cookie | 同上 |
| GET | `/api/menu-permissions/me` | Cookie | 有效菜单 |
| GET | `/api/admin/menu-permissions/matrix` | Cookie | 权限矩阵 |
| PUT | `/api/admin/menu-permissions` | Cookie | Body: `UpdateMenuPermissionsBody` |
| GET | `/api/admin/menu-permissions/roles` | Cookie | RBAC 角色列表 |
| POST | `/api/admin/menu-permissions/roles` | Cookie | Body: `CreateRbacRoleRequest` |
| PATCH | `/api/admin/menu-permissions/roles/:roleKey` | Cookie | Body: `UpdateRbacRoleRequest` |
| DELETE | `/api/admin/menu-permissions/roles/:roleKey` | Cookie | 删除自定义角色 |
| GET | `/api/employees` | Cookie | Query: `page`, `pageSize`, `keyword` |
| POST | `/api/employees` | Cookie | Body: `CreateEmployeeRequest` |
| PUT | `/api/employees/:id` | Cookie | Body: `UpdateEmployeeRequest` |
| DELETE | `/api/employees/:id` | Cookie | |
| PUT | `/api/employees/:id/hierarchy` | Cookie | Body: `managerId`, `dottedManagerId`, `departmentId`, `departmentName` |
| GET | `/api/employees/departments` | Cookie | |
| GET | `/api/employees/department-options` | Cookie | |
| GET | `/api/employees/role-options` | Cookie | |
| GET | `/api/employees/all` | Cookie | |
| POST | `/api/employees/sync-from-lark` | Cookie | Body: `{ clearExisting: boolean }` |
| GET | `/api/performances` | Cookie | Query: `status`, `focus`, `period`, `departmentId`, `employeeName`, `page`, `pageSize` |
| GET | `/api/performances/create/month-periods` | Cookie | 批量创建用月度 |
| GET | `/api/performances/export` | Cookie | 导出，Query 同列表筛选 |
| GET | `/api/performances/calibration/supervisor-queue` | Cookie | Query: `period`, `departmentId`, `employeeName`, `page`, `pageSize` |
| GET | `/api/performances/:id` | Cookie | 详情 |
| PATCH | `/api/performances/:id` | Cookie | 草稿 Body: `reviewType`, `content`, `personalSummary?` |
| POST | `/api/performances` | Cookie | 批量创建 `CreatePerformanceRequest` |
| POST | `/api/performances/:id/select-template` | Cookie | `SelectTemplateRequest` |
| POST | `/api/performances/:id/submit` | Cookie | 提交 `reviewType` + `content` |
| POST | `/api/performances/:id/reject` | Cookie | `{ reason }` |
| POST | `/api/performances/:id/approve-goal` | Cookie | `ApproveGoalRequest` |
| POST | `/api/performances/:id/final-review` | Cookie | `FinalReviewRequest` |
| POST | `/api/performances/:id/calibrate` | Cookie | `CalibrationReviewRequest` |
| GET | `/api/admin/templates` | 无（列表） | Query: `page`, `pageSize` |
| GET | `/api/admin/templates/:id` | 无 | |
| POST | `/api/admin/templates` | Cookie | 创建模板 |
| PATCH | `/api/admin/templates/:id` | Cookie | |
| POST | `/api/admin/templates/:id/toggle-status` | Cookie | |
| POST | `/api/admin/templates/:id/copy` | Cookie | |
| DELETE | `/api/admin/templates/:id` | Cookie | |
| GET | `/api/admin/notifications` | 无（列表） | Query: `page`, `pageSize` |
| POST | `/api/admin/notifications` | Cookie | `SendNotificationRequest` |
| GET | `/api/admin/system-config` | Cookie | |
| PATCH | `/api/admin/system-config` | Cookie | `{ configs: [{ key, value }] }` |
| GET | `/api/admin/evaluation-periods` | Cookie | Query: `period_type` |
| POST | `/api/admin/evaluation-periods` | Cookie | `CreateEvaluationPeriodRequest` |
| PUT | `/api/admin/evaluation-periods/:id` | Cookie | `UpdateEvaluationPeriodRequest` |
| DELETE | `/api/admin/evaluation-periods/:id` | Cookie | |
| GET | `/api/admin/award-types` | Cookie | |
| GET | `/api/admin/evaluation/performance-periods` | Cookie | |
| GET | `/api/admin/evaluation/leaderboard` | Cookie | Query: `scope`, `key`, `departmentIds` |
| GET | `/api/admin/evaluation/awards` | Cookie | Query: `periodId` |
| POST | `/api/admin/evaluation/awards` | Cookie | `CreatePeriodAwardRequest` |
| DELETE | `/api/admin/evaluation/awards/:id` | Cookie | |
| GET | `/*` | 无 | SPA `index.html`（非 JSON） |

## Newman 集合覆盖说明

- 集合由 `build-collection.mjs` 生成，示例 Body 对齐 `shared/api.interface.ts`。
- `performance_id`、`evaluation_period_id`、`award_id` 等需在环境中填写，或由前置请求的 Tests 脚本赋值（当前集合对 `performance_id` 在「列表」接口自动取第一条）。
- 写库 / 删除类请求（员工、角色、评选周期等）可能因权限或数据冲突失败，属预期；可改用 `--folder` 只跑只读接口。
