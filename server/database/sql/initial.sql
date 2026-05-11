-- jixiao2 数据库初始化脚本（MySQL 5.7+，InnoDB + utf8mb4）
-- 在目标库执行：mysql -h HOST -u USER -p DATABASE < server/database/sql/initial.sql
--
-- 说明：
-- - 业务表使用 DROP + CREATE，适合空库或开发环境全量重建。
-- - system_config 使用 IF NOT EXISTS，避免重复执行时清空已写入的飞书等配置。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- performance_template
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `performance_template`;
CREATE TABLE `performance_template` (
  `id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `position` varchar(255) NOT NULL,
  `indicators` json NOT NULL,
  `status` varchar(50) DEFAULT 'enabled',
  `version` int(11) DEFAULT 1,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_created_by` varchar(255) DEFAULT NULL,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `_updated_by` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_performance_template_status` (`status`)
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
DROP TABLE IF EXISTS `performance_record`;
CREATE TABLE `performance_record` (
  `id` varchar(36) NOT NULL,
  `employee_id` varchar(255) NOT NULL,
  `template_id` varchar(36) DEFAULT NULL,
  `period` varchar(50) NOT NULL,
  `status` varchar(50) DEFAULT 'template_selection',
  `manager_id` varchar(255) NOT NULL,
  `dotted_manager_id` varchar(255) DEFAULT NULL,
  `self_review` json DEFAULT NULL,
  `manager_review` json DEFAULT NULL,
  `dotted_manager_review` json DEFAULT NULL,
  `total_score` decimal(5,2) DEFAULT NULL,
  `rejection_reason` text,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_created_by` varchar(255) DEFAULT NULL,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `_updated_by` varchar(255) DEFAULT NULL,
  `goal_setting` json DEFAULT NULL,
  `goal_approved_by` varchar(255) DEFAULT NULL,
  `personal_summary` text DEFAULT NULL,
  `final_reviewer_id` varchar(255) DEFAULT NULL,
  `final_reviewed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_performance_record_employee` (`employee_id`),
  KEY `idx_performance_record_manager` (`manager_id`),
  KEY `idx_performance_record_period` (`period`),
  KEY `idx_performance_record_status` (`status`)
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
  ('home', '工作台', 2),
  ('performance_list', '绩效列表', 3),
  ('performance_export', '导出绩效数据', 4),
  ('performance_list_all', '查看全员绩效', 41),
  ('performance_batch_create', '批量创建绩效', 42),
  ('performance_review_admin', '绩效终审与校准', 43),
  ('admin_performance_calibration', '绩效校准（上级评分）', 44),
  ('my_performance', '我的绩效', 5),
  ('admin_templates', '模板管理', 6),
  ('admin_notifications', '通知管理', 7),
  ('admin_employees', '员工管理', 8),
  ('admin_roles', '角色管理', 9),
  ('admin_permissions', '权限管理', 10),
  ('admin_statistics_months', '周期与评选', 11),
  ('admin_system_config', '系统配置', 12);

INSERT INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
SELECT 'super_admin', `menu_key`, 1 FROM `menu`;

INSERT INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
SELECT 'admin', m.`menu_key`, CASE WHEN m.`menu_key` IN ('todo', 'admin_permissions', 'admin_roles', 'performance_export', 'performance_batch_create', 'admin_performance_calibration') THEN 0 ELSE 1 END
FROM `menu` m;

INSERT INTO `role_menu` (`role_key`, `menu_key`, `allowed`) VALUES
  ('employee', 'todo', 1),
  ('employee', 'home', 0),
  ('employee', 'performance_list', 1),
  ('employee', 'performance_export', 0),
  ('employee', 'performance_list_all', 0),
  ('employee', 'performance_batch_create', 0),
  ('employee', 'performance_review_admin', 0),
  ('employee', 'my_performance', 1),
  ('employee', 'admin_templates', 0),
  ('employee', 'admin_notifications', 0),
  ('employee', 'admin_employees', 0),
  ('employee', 'admin_roles', 0),
  ('employee', 'admin_permissions', 0),
  ('employee', 'admin_statistics_months', 0),
  ('employee', 'admin_system_config', 0),
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
  ('monthly_excellence', '月度优秀奖', 'month', NULL, 10, 1),
  ('quarter_star', '季度之星', 'quarter', 1, 20, 1),
  ('altruism', '利他奖', 'both', NULL, 30, 1),
  ('excellent', '优秀', 'both', NULL, 40, 1);

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
-- employee_hierarchy
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `employee_hierarchy`;
CREATE TABLE `employee_hierarchy` (
  `id` varchar(36) NOT NULL,
  `employee_id` varchar(255) NOT NULL,
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
  KEY `idx_employee_hierarchy_manager_id` (`manager_id`)
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
  `position`
) VALUES (
  '22222222-2222-4222-8222-222222222222',
  'demo_manager',
  NULL,
  'dept-default',
  '管理部',
  '演示经理',
  '经理'
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
  `position`
) VALUES (
  '11111111-1111-4111-8111-111111111111',
  'zhou_rong',
  'demo_manager',
  'dept-default',
  '管理部',
  '周荣',
  '员工'
);

INSERT INTO `user_role` (`user_id`, `role_key`) VALUES ('zhou_rong', 'super_admin');

-- ---------------------------------------------------------------------------
-- 演示绩效模板（3 个指标，权重之和 = 100）
-- ---------------------------------------------------------------------------
INSERT INTO `performance_template` (`id`, `name`, `position`, `indicators`, `status`) VALUES (
  'tpl-demo-001',
  '通用绩效考核模板',
  '员工',
  '[{"name":"业绩目标","weight":40,"description":"核心业务指标完成情况"},{"name":"团队协作","weight":30,"description":"跨部门沟通与协作能力"},{"name":"专业能力","weight":30,"description":"岗位技能与学习成长"}]',
  'enabled'
);

-- ---------------------------------------------------------------------------
-- 评审角色权重配置（manager 70%，dotted_manager 30%；删除此行则均值）
-- ---------------------------------------------------------------------------
INSERT INTO `system_config` (`config_key`, `config_value`) VALUES
  ('manager_review_weight', '0.7'),
  ('dotted_manager_review_weight', '0.3')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

SET FOREIGN_KEY_CHECKS = 1;
