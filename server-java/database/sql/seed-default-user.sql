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
  `position`,
  `assessment_rule_id`
) VALUES (
  '22222222-2222-4222-8222-222222222222',
  'demo_manager',
  NULL,
  'dept-default',
  '管理部',
  '演示经理',
  '经理',
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
  '11111111-1111-4111-8111-111111111111',
  'zhou_rong',
  'demo_manager',
  'dept-default',
  '管理部',
  '周荣',
  '员工',
  'arule-default-001'
)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `department_name` = VALUES(`department_name`),
  `position` = VALUES(`position`),
  `manager_id` = VALUES(`manager_id`),
  `assessment_rule_id` = COALESCE(`assessment_rule_id`, VALUES(`assessment_rule_id`));

INSERT INTO `user_role` (`user_id`, `role_key`) VALUES ('zhou_rong', 'super_admin')
ON DUPLICATE KEY UPDATE `role_key` = VALUES(`role_key`);

INSERT IGNORE INTO `assessment_rule` (`id`, `name`, `manager_weight`, `dotted_manager_weight`, `status`) VALUES
  ('arule-default-001', '默认（上级70% / 虚线30%）', 0.7, 0.3, 'enabled');

-- 演示绩效模板
INSERT INTO `performance_template` (`id`, `name`, `position`, `indicators`, `culture_dimensions`, `assessment_rule_id`, `status`) VALUES (
  'tpl-demo-001',
  '通用绩效考核模板',
  '员工',
  '[{"name":"业绩目标","weight":32,"description":"核心业务指标完成情况"},{"name":"团队协作","weight":24,"description":"跨部门沟通与协作能力"},{"name":"专业能力","weight":24,"description":"岗位技能与学习成长"}]',
  '[{"name":"利他","maxScore":7,"description":"1. 客户为先，不断完善产品与服务，为用户创造价值。\\n2. 分享与互助，不断提升能力与效率。\\n3. 让和你合作的人成功，相互成就。","criteria":"优秀(7分)，合格(4-6分)，不合格(0分)"},{"name":"本分","maxScore":7,"description":"1. 以诚相待，高效沟通。\\n2. 实事求是，说到做到。\\n3. 敢于质疑，敢于挑战，抓住事物本质。\\n4. 坚持自我批判，不断超越自我。","criteria":"优秀(7分)，合格(4-6分)，不合格(0分)"},{"name":"结果导向","maxScore":6,"description":"1. 以结果来驱动行为，对结果负责。\\n2. 不找借口，突破客观条件限制，整合资源，不惜一切达成结果。\\n3. 关注团队结果，团队成功才有个人价值。","criteria":"优秀(6分)，合格(3-5分)，不合格(0分)"}]',
  NULL,
  'enabled'
)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `indicators` = VALUES(`indicators`),
  `culture_dimensions` = COALESCE(VALUES(`culture_dimensions`), `culture_dimensions`),
  `status` = VALUES(`status`);

-- 评审角色权重（上级 70%，虚线 30%）
INSERT INTO `system_config` (`config_key`, `config_value`) VALUES
  ('manager_review_weight', '0.7'),
  ('dotted_manager_review_weight', '0.3')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);
