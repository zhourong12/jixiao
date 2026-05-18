-- jixiao2 数据库初始化脚本（MySQL 5.7+，InnoDB + utf8mb4）
-- 在目标库执行：mysql -h HOST -u USER -p DATABASE < server/database/sql/initial.sql
--
-- 说明：
-- - 业务表使用 DROP + CREATE，适合空库或开发环境全量重建。
-- - system_config 使用 IF NOT EXISTS，避免重复执行时清空已写入的飞书等配置。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- assessment_rule（上级 / 虚线上级评分合成权重；模板必选其一）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `performance_template`;
DROP TABLE IF EXISTS `assessment_rule`;
CREATE TABLE `assessment_rule` (
  `id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `manager_weight` decimal(8,6) NOT NULL,
  `dotted_manager_weight` decimal(8,6) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'enabled',
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_created_by` varchar(255) DEFAULT NULL,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `_updated_by` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_assessment_rule_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `assessment_rule` (`id`, `name`, `manager_weight`, `dotted_manager_weight`, `status`) VALUES
  ('arule-default-001', '默认（上级70% / 虚线30%）', 0.7, 0.3, 'enabled');

-- ---------------------------------------------------------------------------
-- scoring_scheme（评分方案：定义绩效/文化/学习三类占比）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `scoring_scheme`;
CREATE TABLE `scoring_scheme` (
  `id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `performance_weight` decimal(5,2) NOT NULL COMMENT '绩效模板占比 %',
  `culture_weight` decimal(5,2) NOT NULL COMMENT '文化价值观占比 %',
  `learning_weight` decimal(5,2) NOT NULL COMMENT '学习与成长占比 %',
  `status` varchar(32) NOT NULL DEFAULT 'enabled',
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_created_by` varchar(255) DEFAULT NULL,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `_updated_by` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_scoring_scheme_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `scoring_scheme` (`id`, `name`, `performance_weight`, `culture_weight`, `learning_weight`, `status`) VALUES
  ('scheme-default-001', '默认方案（绩效60% / 文化20% / 学习20%）', 60.00, 20.00, 20.00, 'enabled');

-- ---------------------------------------------------------------------------
-- performance_template
-- ---------------------------------------------------------------------------
CREATE TABLE `performance_template` (
  `id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `type` varchar(32) NOT NULL DEFAULT 'performance' COMMENT 'performance|culture|learning',
  `position` varchar(255) NOT NULL DEFAULT '',
  `indicators` json NOT NULL,
  `culture_dimensions` json DEFAULT NULL COMMENT '文化价值观维度定义（type=culture时使用，满分之和须为20）',
  `assessment_rule_id` varchar(36) DEFAULT NULL,
  `status` varchar(50) DEFAULT 'enabled',
  `version` int(11) DEFAULT 1,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_created_by` varchar(255) DEFAULT NULL,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `_updated_by` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_performance_template_status` (`status`),
  KEY `idx_performance_template_type_status` (`type`, `status`),
  KEY `idx_performance_template_rule` (`assessment_rule_id`),
  CONSTRAINT `fk_performance_template_assessment_rule` FOREIGN KEY (`assessment_rule_id`) REFERENCES `assessment_rule` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- notification
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id` varchar(36) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` text NOT NULL,
  `send_type` varchar(50) NOT NULL,
  `target_ids` json NOT NULL,
  `sender_id` varchar(255) NOT NULL,
  `read_count` int(11) DEFAULT 0,
  `total_count` int(11) DEFAULT 0,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_created_by` varchar(255) DEFAULT NULL,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `_updated_by` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_sender` (`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- performance_record
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `performance_record_flow_log`;
DROP TABLE IF EXISTS `performance_feishu_task`;
DROP TABLE IF EXISTS `performance_feishu_task_node_config`;
DROP TABLE IF EXISTS `performance_record`;
CREATE TABLE `performance_record` (
  `id` varchar(36) NOT NULL,
  `employee_id` varchar(255) NOT NULL,
  `template_id` varchar(36) DEFAULT NULL COMMENT '绩效模板ID（目标设定阶段由员工选择后更新）',
  `performance_indicators` json DEFAULT NULL COMMENT '绩效指标快照（目标阶段可编辑；为空则从模板读）',
  `culture_dimensions` json DEFAULT NULL COMMENT '创建时从文化模板复制的维度快照',
  `learning_dimensions` json DEFAULT NULL COMMENT '创建时从学习模板复制的指标快照',
  `scoring_scheme_id` varchar(36) DEFAULT NULL COMMENT '评分方案ID',
  `scoring_weights` json DEFAULT NULL COMMENT '评分方案权重快照 {"performance":60,"culture":20,"learning":20}',
  `culture_template_id` varchar(36) DEFAULT NULL COMMENT '创建时使用的文化模板ID',
  `learning_template_id` varchar(36) DEFAULT NULL COMMENT '创建时使用的学习模板ID',
  `assessment_rule_id` varchar(36) DEFAULT NULL,
  `period` varchar(50) NOT NULL,
  `status` varchar(50) DEFAULT 'goal_setting',
  `manager_id` varchar(255) NOT NULL,
  `dotted_manager_id` varchar(255) DEFAULT NULL,
  `self_review` json DEFAULT NULL,
  `manager_review` json DEFAULT NULL,
  `dotted_manager_review` json DEFAULT NULL,
  `culture_self_review` json DEFAULT NULL,
  `culture_manager_review` json DEFAULT NULL,
  `culture_dotted_manager_review` json DEFAULT NULL,
  `learning_self_review` json DEFAULT NULL,
  `learning_manager_review` json DEFAULT NULL,
  `learning_dotted_manager_review` json DEFAULT NULL,
  `total_score` decimal(5,2) DEFAULT NULL,
  `score_grade` varchar(4) DEFAULT NULL COMMENT '评分等级 S/A/B/C',
  `rejection_reason` text,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_created_by` varchar(255) DEFAULT NULL,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `_updated_by` varchar(255) DEFAULT NULL,
  `goal_setting` json DEFAULT NULL,
  `goal_approved_by` varchar(255) DEFAULT NULL,
  `personal_summary` text DEFAULT NULL,
  `manager_summary` text DEFAULT NULL,
  `dotted_manager_summary` text DEFAULT NULL,
  `final_reviewer_id` varchar(255) DEFAULT NULL,
  `final_reviewed_at` datetime DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_performance_record_employee` (`employee_id`),
  KEY `idx_performance_record_manager` (`manager_id`),
  KEY `idx_performance_record_period` (`period`),
  KEY `idx_performance_record_status` (`status`),
  KEY `idx_performance_record_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- performance_record_flow_log（绩效状态/节点流转日志）
-- ---------------------------------------------------------------------------
CREATE TABLE `performance_record_flow_log` (
  `id` varchar(36) NOT NULL,
  `performance_record_id` varchar(36) NOT NULL,
  `from_status` varchar(50) NOT NULL,
  `to_status` varchar(50) NOT NULL,
  `action` varchar(64) NOT NULL,
  `actor_user_id` varchar(255) NOT NULL,
  `remark` varchar(2000) DEFAULT NULL,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_perf_flow_record_created` (`performance_record_id`, `_created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 绩效飞书待办：节点截止天数 + 每条待办
-- ---------------------------------------------------------------------------
CREATE TABLE `performance_feishu_task_node_config` (
  `node_key` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL,
  `due_days` int(11) NOT NULL DEFAULT 7,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `performance_feishu_task_node_config` (`node_key`, `name`, `due_days`, `sort_order`) VALUES
  ('goal', '目标设定', 7, 1),
  ('goal_review', '目标审核', 7, 2),
  ('self', '员工自评', 7, 3),
  ('manager', '上级评分', 7, 4),
  ('final', '绩效校准', 7, 5),
  ('confirm', '员工确认', 7, 6);

CREATE TABLE `performance_feishu_task` (
  `id` varchar(36) NOT NULL,
  `performance_record_id` varchar(36) NOT NULL,
  `node_key` varchar(32) NOT NULL,
  `assignee_employee_id` varchar(255) NOT NULL,
  `feishu_task_guid` varchar(64) DEFAULT NULL,
  `summary` varchar(512) NOT NULL DEFAULT '',
  `description` mediumtext,
  `due_at` datetime NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending',
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pft_record_node_status` (`performance_record_id`, `node_key`, `status`),
  KEY `idx_pft_assignee` (`assignee_employee_id`),
  CONSTRAINT `fk_pft_performance_record` FOREIGN KEY (`performance_record_id`) REFERENCES `performance_record` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- RBAC：menu / role / user_role / role_menu（登录后按 user_role ∪ role_menu 计算菜单）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `role_menu`;
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `menu`;
DROP TABLE IF EXISTS `role`;
DROP TABLE IF EXISTS `role_menu_permission`;
DROP TABLE IF EXISTS `admin_config`;

CREATE TABLE `role` (
  `role_key` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  `is_system` tinyint(1) NOT NULL DEFAULT 1,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `menu` (
  `menu_key` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`menu_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_role` (
  `user_id` varchar(255) NOT NULL,
  `role_key` varchar(50) NOT NULL,
  PRIMARY KEY (`user_id`, `role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `role_menu` (
  `role_key` varchar(50) NOT NULL,
  `menu_key` varchar(64) NOT NULL,
  `allowed` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`role_key`, `menu_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `role` (`role_key`, `name`, `is_system`, `sort_order`) VALUES
  ('employee', '员工', 1, 1),
  ('admin', '管理员', 1, 2),
  ('super_admin', '超级管理员', 1, 3);

INSERT INTO `menu` (`menu_key`, `name`, `sort_order`) VALUES
  ('todo', '待办', 1),
  ('performance_list', '绩效列表', 3),
  ('performance_export', '导出绩效数据', 4),
  ('performance_list_all', '查看全员绩效', 41),
  ('performance_batch_create', '批量创建绩效', 42),
  ('performance_review_admin', '绩效终审与校准', 43),
  ('admin_performance_calibration', '绩效校准（上级评分）', 44),
  ('my_performance', '我的绩效', 5),
  ('admin_templates', '模板管理', 6),
  ('admin_scoring_schemes', '评分方案', 14),
  ('admin_assessment_rules', '考核规则', 13),
  ('admin_employees', '员工管理', 8),
  ('admin_departments', '部门管理', 7),
  ('admin_roles', '角色管理', 9),
  ('admin_permissions', '权限管理', 10),
  ('admin_statistics_months', '周期与评选', 11),
  ('admin_performance_feishu_task', '飞书绩效待办', 15);

INSERT INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
SELECT 'super_admin', `menu_key`, 1 FROM `menu`;

INSERT INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
SELECT 'admin', m.`menu_key`, CASE WHEN m.`menu_key` IN ('todo', 'admin_permissions', 'admin_roles', 'performance_export', 'performance_batch_create', 'admin_performance_calibration') THEN 0 ELSE 1 END
FROM `menu` m;

INSERT INTO `role_menu` (`role_key`, `menu_key`, `allowed`) VALUES
  ('employee', 'todo', 1),
  ('employee', 'performance_list', 1),
  ('employee', 'performance_export', 0),
  ('employee', 'performance_list_all', 0),
  ('employee', 'performance_batch_create', 0),
  ('employee', 'performance_review_admin', 0),
  ('employee', 'my_performance', 1),
  ('employee', 'admin_templates', 0),
  ('employee', 'admin_scoring_schemes', 0),
  ('employee', 'admin_assessment_rules', 0),
  ('employee', 'admin_employees', 0),
  ('employee', 'admin_roles', 0),
  ('employee', 'admin_permissions', 0),
  ('employee', 'admin_statistics_months', 0),
  ('employee', 'admin_performance_feishu_task', 0),
  ('employee', 'admin_performance_calibration', 0);

-- ---------------------------------------------------------------------------
-- 评选周期 / 奖项 / 获奖记录
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `period_award`;
DROP TABLE IF EXISTS `evaluation_period`;
DROP TABLE IF EXISTS `award_type`;

CREATE TABLE `award_type` (
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `scope` varchar(16) NOT NULL,
  `max_winners` int(11) DEFAULT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `is_system` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `award_type` (`code`, `name`, `scope`, `max_winners`, `sort_order`, `is_system`) VALUES
  ('altruism', '利他奖', 'both', NULL, 10, 1),
  ('monthly_excellence', '月度优秀奖', 'month', NULL, 20, 1),
  ('quarter_excellence', '季度优秀奖', 'quarter', NULL, 30, 1),
  ('innovation', '创新奖', 'both', NULL, 40, 1),
  ('annual_excellence', '年度优秀奖', 'quarter', NULL, 50, 1);

CREATE TABLE `evaluation_period` (
  `id` varchar(36) NOT NULL,
  `period_type` varchar(16) NOT NULL,
  `period_key` varchar(16) NOT NULL,
  `name` varchar(255) NOT NULL DEFAULT '',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'open',
  `parent_period_id` varchar(36) DEFAULT NULL,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_evaluation_period_type_key` (`period_type`, `period_key`),
  KEY `idx_evaluation_period_parent` (`parent_period_id`),
  CONSTRAINT `fk_evaluation_period_parent` FOREIGN KEY (`parent_period_id`) REFERENCES `evaluation_period` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `period_award` (
  `id` varchar(36) NOT NULL,
  `period_id` varchar(36) NOT NULL,
  `award_code` varchar(64) NOT NULL,
  `employee_id` varchar(255) NOT NULL,
  `performance_record_id` varchar(36) DEFAULT NULL,
  `remark` text,
  `created_by` varchar(255) DEFAULT NULL,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_period_award_period_award_emp` (`period_id`, `award_code`, `employee_id`),
  KEY `idx_period_award_period` (`period_id`),
  CONSTRAINT `fk_period_award_period` FOREIGN KEY (`period_id`) REFERENCES `evaluation_period` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_period_award_award` FOREIGN KEY (`award_code`) REFERENCES `award_type` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- feishu_subject / feishu_app（多主体；凭证由运维 SQL 维护，勿提交真实 secret）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `feishu_app`;
DROP TABLE IF EXISTS `feishu_subject`;
CREATE TABLE `feishu_subject` (
  `id` varchar(36) NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `notify_frontend_base_url` varchar(512) DEFAULT NULL,
  `notify_feishu_web_app_url` varchar(512) DEFAULT NULL,
  `web_app_link_app_id` varchar(128) DEFAULT NULL,
  `performance_notify_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_feishu_subject_code` (`code`),
  KEY `idx_feishu_subject_enabled` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `feishu_app` (
  `id` varchar(36) NOT NULL,
  `feishu_subject_id` varchar(36) NOT NULL,
  `name` varchar(128) NOT NULL DEFAULT '',
  `app_id` varchar(128) NOT NULL,
  `app_secret` varchar(512) NOT NULL,
  `redirect_uri` varchar(1024) NOT NULL,
  `is_login_app` tinyint(1) NOT NULL DEFAULT 0,
  `is_im_app` tinyint(1) NOT NULL DEFAULT 0,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_feishu_app_subject` (`feishu_subject_id`, `enabled`, `sort_order`),
  CONSTRAINT `fk_feishu_app_subject` FOREIGN KEY (`feishu_subject_id`) REFERENCES `feishu_subject` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `feishu_subject` (
  `id`, `code`, `name`, `sort_order`, `enabled`,
  `notify_frontend_base_url`, `notify_feishu_web_app_url`, `web_app_link_app_id`, `performance_notify_enabled`
) VALUES
  ('a0000001-0000-4000-8000-000000000001', 'default', '默认主体', 0, 1, NULL, NULL, NULL, 1),
  ('a0000002-0000-4000-8000-000000000002', 'corp_b', '主体B', 1, 0, NULL, NULL, NULL, 1);

INSERT INTO `feishu_app` (
  `id`, `feishu_subject_id`, `name`, `app_id`, `app_secret`, `redirect_uri`,
  `is_login_app`, `is_im_app`, `sort_order`, `enabled`
) VALUES (
  'b0000001-0000-4000-8000-000000000001',
  'a0000001-0000-4000-8000-000000000001',
  '默认自建应用',
  'REPLACE_APP_ID',
  'REPLACE_APP_SECRET',
  'REPLACE_REDIRECT_URI',
  1,
  1,
  0,
  1
);
-- 同一主体多应用：见迁移脚本 `add-feishu-multi-tenant.sql` 文末注释（拆分登录应用 / IM 应用示例）。
-- 新建库若需第二条应用，可复制该文件中的 INSERT 模板，`feishu_subject_id` 仍用默认主体 id。

-- ---------------------------------------------------------------------------
-- employee_hierarchy
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `employee_hierarchy`;
CREATE TABLE `employee_hierarchy` (
  `id` varchar(36) NOT NULL,
  `employee_id` varchar(255) NOT NULL,
  `feishu_subject_id` varchar(36) DEFAULT NULL,
  `feishu_open_id` varchar(255) DEFAULT NULL,
  `assessment_rule_id` varchar(36) DEFAULT NULL,
  `manager_id` varchar(255) DEFAULT NULL,
  `dotted_manager_id` varchar(255) DEFAULT NULL,
  `department_id` varchar(255) DEFAULT NULL,
  `department_name` varchar(255) DEFAULT NULL,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_created_by` varchar(255) DEFAULT NULL,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `_updated_by` varchar(255) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `employee_no` varchar(50) DEFAULT NULL,
  `employee_type` varchar(50) DEFAULT NULL,
  `position` varchar(100) DEFAULT NULL,
  `work_location` varchar(100) DEFAULT NULL,
  `join_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_employee_hierarchy_employee_id` (`employee_id`),
  KEY `idx_employee_hierarchy_department_id` (`department_id`),
  KEY `idx_employee_hierarchy_dotted_manager_id` (`dotted_manager_id`),
  KEY `idx_employee_hierarchy_manager_id` (`manager_id`),
  KEY `idx_employee_hierarchy_feishu_subject` (`feishu_subject_id`),
  KEY `idx_employee_hierarchy_feishu_subject_open` (`feishu_subject_id`, `feishu_open_id`),
  KEY `idx_employee_hierarchy_assessment_rule` (`assessment_rule_id`),
  CONSTRAINT `fk_employee_hierarchy_feishu_subject` FOREIGN KEY (`feishu_subject_id`) REFERENCES `feishu_subject` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_employee_hierarchy_assessment_rule` FOREIGN KEY (`assessment_rule_id`) REFERENCES `assessment_rule` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- system_config（飞书 app_id / app_secret 等；勿将密钥提交到 Git）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `system_config` (
  `config_key` varchar(64) NOT NULL,
  `config_value` text NOT NULL,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 可选：在库中写入飞书凭证（与代码中 FeishuCredentialsService 读取的 key 一致）
-- INSERT INTO system_config (config_key, config_value) VALUES ('feishu_app_id', 'cli_xxx')
--   ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
-- INSERT INTO system_config (config_key, config_value) VALUES ('feishu_app_secret', 'xxx')
--   ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- ---------------------------------------------------------------------------
-- 演示组织：直属上级 + 下属（便于「创建绩效」等业务校验 manager_id 非空）
-- demo_manager 仅存在于层级表，无账密；周荣的 manager_id 指向其 employee_id。
-- ---------------------------------------------------------------------------
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
);

-- ---------------------------------------------------------------------------
-- 默认演示账号：周荣（员工 + 超级管理员）
-- 账密登录：用户名填「zhou_rong」或「周荣」，密码 123456（见后端 DEFAULT_LOCAL_PASSWORD）
-- ---------------------------------------------------------------------------
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
);

INSERT INTO `user_role` (`user_id`, `role_key`) VALUES ('zhou_rong', 'super_admin');

-- ---------------------------------------------------------------------------
-- 演示绩效模板（3 个指标，权重之和 = 80，与绩效部分占总分 80% 对齐）
-- ---------------------------------------------------------------------------
-- 绩效类型模板
INSERT INTO `performance_template` (`id`, `name`, `type`, `position`, `indicators`, `culture_dimensions`, `assessment_rule_id`, `status`) VALUES (
  'tpl-demo-001',
  '通用绩效考核模板',
  'performance',
  '员工',
  '[{"name":"业绩目标","weight":32,"description":"核心业务指标完成情况"},{"name":"团队协作","weight":24,"description":"跨部门沟通与协作能力"},{"name":"专业能力","weight":24,"description":"岗位技能与学习成长"}]',
  NULL,
  NULL,
  'enabled'
);

-- 文化价值观类型模板（全局唯一启用）
INSERT INTO `performance_template` (`id`, `name`, `type`, `position`, `indicators`, `culture_dimensions`, `assessment_rule_id`, `status`) VALUES (
  'tpl-culture-001',
  '文化价值观',
  'culture',
  '',
  '[]',
  '[{"name":"利他","maxScore":7,"description":"1. 客户为先，不断完善产品与服务，为用户创造价值。\\n2. 分享与互助，不断提升能力与效率。\\n3. 让和你合作的人成功，相互成就。","criteria":"优秀(7分)，合格(4-6分)，不合格(0分)"},{"name":"本分","maxScore":7,"description":"1. 以诚相待，高效沟通。\\n2. 实事求是，说到做到。\\n3. 敢于质疑，敢于挑战，抓住事物本质。\\n4. 坚持自我批判，不断超越自我。","criteria":"优秀(7分)，合格(4-6分)，不合格(0分)"},{"name":"结果导向","maxScore":6,"description":"1. 以结果来驱动行为，对结果负责。\\n2. 不找借口，突破客观条件限制，整合资源，不惜一切达成结果。\\n3. 关注团队结果，团队成功才有个人价值。","criteria":"优秀(6分)，合格(3-5分)，不合格(0分)"}]',
  NULL,
  'enabled'
);

-- 学习与成长类型模板（全局唯一启用）
INSERT INTO `performance_template` (`id`, `name`, `type`, `position`, `indicators`, `culture_dimensions`, `assessment_rule_id`, `status`) VALUES (
  'tpl-learning-001',
  '学习与成长',
  'learning',
  '',
  '[{"name":"学习能力","weight":50,"description":"主动学习新知识、新技能的意愿和效果"},{"name":"成长表现","weight":50,"description":"个人能力提升与职业发展的进步情况"}]',
  NULL,
  NULL,
  'enabled'
);

-- ---------------------------------------------------------------------------
-- 评审角色权重配置（manager 70%，dotted_manager 30%；删除此行则均值）
-- ---------------------------------------------------------------------------
INSERT INTO `system_config` (`config_key`, `config_value`) VALUES
  ('manager_review_weight', '0.7'),
  ('dotted_manager_review_weight', '0.3'),
  ('performance_calibration_assignee_ids', '[]'),
  ('performance_feishu_task_enabled', '1')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

SET FOREIGN_KEY_CHECKS = 1;
