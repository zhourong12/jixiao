-- dev-team 全量验证：A–E 类测试身份（可重复执行）
-- 依赖：initial.sql / sync-menu-table.sql、seed-default-user.sql 已执行
-- 本地登录：employee_id 或 name + 密码 123456
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- C：虚线上级账号（登录 demo_dotted_mgr）
-- ---------------------------------------------------------------------------
INSERT INTO `employee_hierarchy` (
  `id`,
  `employee_id`,
  `manager_id`,
  `dotted_manager_id`,
  `department_id`,
  `department_name`,
  `name`,
  `position`
) VALUES (
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  'demo_dotted_mgr',
  NULL,
  NULL,
  'dept-default',
  '管理部',
  '演示虚线上级',
  '总监'
)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `department_name` = VALUES(`department_name`),
  `position` = VALUES(`position`);

-- 带虚线上级的被考核人（仅隐式 employee：不写入 user_role 或见下方清理）
INSERT INTO `employee_hierarchy` (
  `id`,
  `employee_id`,
  `manager_id`,
  `dotted_manager_id`,
  `department_id`,
  `department_name`,
  `name`,
  `position`
) VALUES (
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  'qa_emp_dotted',
  'demo_manager',
  'demo_dotted_mgr',
  'dept-default',
  '管理部',
  '虚线链路员工',
  '员工'
)
ON DUPLICATE KEY UPDATE
  `manager_id` = VALUES(`manager_id`),
  `dotted_manager_id` = VALUES(`dotted_manager_id`),
  `name` = VALUES(`name`);

-- ---------------------------------------------------------------------------
-- D：仅 admin（无 super_admin），用于导出/批量创建/校准 403 与侧栏隐藏
-- ---------------------------------------------------------------------------
INSERT INTO `employee_hierarchy` (
  `id`,
  `employee_id`,
  `manager_id`,
  `dotted_manager_id`,
  `department_id`,
  `department_name`,
  `name`,
  `position`
) VALUES (
  'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  'qa_admin',
  'demo_manager',
  NULL,
  'dept-default',
  '管理部',
  'QA管理员',
  '主管'
)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `manager_id` = VALUES(`manager_id`);

DELETE FROM `user_role` WHERE `user_id` = 'qa_admin';
INSERT INTO `user_role` (`user_id`, `role_key`) VALUES ('qa_admin', 'admin');

-- ---------------------------------------------------------------------------
-- A / A2：纯员工（无 user_role 行 → 后端视为 employee）
-- ---------------------------------------------------------------------------
DELETE FROM `user_role` WHERE `user_id` IN ('demo_emp_01', 'demo_emp_02');

-- E：超管周荣见 seed-default-user.sql（zhou_rong + super_admin）
