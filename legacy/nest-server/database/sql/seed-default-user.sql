-- 仅追加默认账号与演示上级（库已存在表结构时使用，可重复执行）。
-- demo_manager：无登录，仅作周荣直属上级，满足创建绩效等校验。
SET NAMES utf8mb4;

INSERT INTO `employee_hierarchy` (
  `id`,
  `employee_id`,
  `manager_id`,
  `department_id`,
  `department_name`,
  `name`,
  `position`
) VALUES (
  '22222222-2222-4222-8222-222222222222',
  'demo_manager',
  NULL,
  'dept-default',
  '管理部',
  '演示经理',
  '经理'
)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `department_name` = VALUES(`department_name`),
  `position` = VALUES(`position`),
  `manager_id` = VALUES(`manager_id`);

INSERT INTO `employee_hierarchy` (
  `id`,
  `employee_id`,
  `manager_id`,
  `department_id`,
  `department_name`,
  `name`,
  `position`
) VALUES (
  '11111111-1111-4111-8111-111111111111',
  'zhou_rong',
  'demo_manager',
  'dept-default',
  '管理部',
  '周荣',
  '员工'
)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `department_name` = VALUES(`department_name`),
  `position` = VALUES(`position`),
  `manager_id` = VALUES(`manager_id`);

INSERT INTO `user_role` (`user_id`, `role_key`) VALUES ('zhou_rong', 'super_admin')
ON DUPLICATE KEY UPDATE `role_key` = VALUES(`role_key`);

-- 演示绩效模板
INSERT INTO `performance_template` (`id`, `name`, `position`, `indicators`, `status`) VALUES (
  'tpl-demo-001',
  '通用绩效考核模板',
  '员工',
  '[{"name":"业绩目标","weight":40,"description":"核心业务指标完成情况"},{"name":"团队协作","weight":30,"description":"跨部门沟通与协作能力"},{"name":"专业能力","weight":30,"description":"岗位技能与学习成长"}]',
  'enabled'
)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `indicators` = VALUES(`indicators`),
  `status` = VALUES(`status`);

-- 评审角色权重（上级 70%，虚线 30%）
INSERT INTO `system_config` (`config_key`, `config_value`) VALUES
  ('manager_review_weight', '0.7'),
  ('dotted_manager_review_weight', '0.3')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);
