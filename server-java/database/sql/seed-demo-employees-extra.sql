-- 追加两名演示员工（库已有表结构时使用，可重复执行）。
-- 直属上级：demo_manager（须已存在，见 seed-default-user.sql / initial.sql）。
-- 本地登录：用户名填 employee_id 或 name，密码 123456（无 user_role 时默认为 employee）。
SET NAMES utf8mb4;

INSERT INTO `employee_hierarchy` (
  `id`,
  `employee_id`,
  `manager_id`,
  `department_id`,
  `department_name`,
  `name`,
  `position`,
  `assessment_rule_id`
) VALUES (
  '33333333-3333-4333-8333-333333333333',
  'demo_emp_01',
  'demo_manager',
  'dept-default',
  '管理部',
  '张三',
  '员工',
  'arule-default-001'
)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `department_name` = VALUES(`department_name`),
  `position` = VALUES(`position`),
  `manager_id` = VALUES(`manager_id`),
  `assessment_rule_id` = COALESCE(`assessment_rule_id`, VALUES(`assessment_rule_id`));

INSERT INTO `employee_hierarchy` (
  `id`,
  `employee_id`,
  `manager_id`,
  `department_id`,
  `department_name`,
  `name`,
  `position`,
  `assessment_rule_id`
) VALUES (
  '44444444-4444-4444-8444-444444444444',
  'demo_emp_02',
  'demo_manager',
  'dept-dev',
  '研发部',
  '李四',
  '员工',
  'arule-default-001'
)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `department_name` = VALUES(`department_name`),
  `position` = VALUES(`position`),
  `manager_id` = VALUES(`manager_id`),
  `assessment_rule_id` = COALESCE(`assessment_rule_id`, VALUES(`assessment_rule_id`));
