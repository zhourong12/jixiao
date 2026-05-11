-- 已有库增量：从旧版 admin_config / role_menu_permission 迁到 RBAC 四表（可重复执行）
-- 新库请直接用 initial.sql，无需执行本文件。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `role` (
  `role_key` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  `is_system` tinyint(1) NOT NULL DEFAULT 1,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `menu` (
  `menu_key` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`menu_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_role` (
  `user_id` varchar(255) NOT NULL,
  `role_key` varchar(50) NOT NULL,
  PRIMARY KEY (`user_id`, `role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role_menu` (
  `role_key` varchar(50) NOT NULL,
  `menu_key` varchar(64) NOT NULL,
  `allowed` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`role_key`, `menu_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 角色 / 菜单种子（INSERT IGNORE，不覆盖已有）
INSERT IGNORE INTO `role` (`role_key`, `name`, `is_system`, `sort_order`) VALUES
  ('employee', '员工', 1, 1),
  ('admin', '管理员', 1, 2),
  ('super_admin', '超级管理员', 1, 3);

INSERT IGNORE INTO `menu` (`menu_key`, `name`, `sort_order`) VALUES
  ('todo', '待办', 1),
  ('home', '工作台', 2),
  ('performance_list', '绩效列表', 3),
  ('performance_export', '导出绩效数据', 4),
  ('my_performance', '我的绩效', 5),
  ('admin_templates', '模板管理', 6),
  ('admin_notifications', '通知管理', 7),
  ('admin_employees', '员工管理', 8),
  ('admin_roles', '角色管理', 9),
  ('admin_permissions', '权限管理', 10),
  ('admin_statistics_months', '周期与评选', 11),
  ('admin_system_config', '系统配置', 12),
  ('performance_list_all', '查看全员绩效', 41),
  ('performance_batch_create', '批量创建绩效', 42),
  ('performance_review_admin', '绩效终审与校准', 43),
  ('admin_performance_calibration', '绩效校准（上级评分）', 44);

-- 若仍存在旧表：迁移数据（表不存在时下面语句会失败，可注释掉对应段落）
INSERT IGNORE INTO `user_role` (`user_id`, `role_key`)
  SELECT `user_id`, `role` FROM `admin_config`;

INSERT IGNORE INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
  SELECT `role`, `menu_key`, `allowed` FROM `role_menu_permission`;

-- 默认矩阵：仅当某角色在 role_menu 仍无任何行时补齐（避免覆盖线上已调过的矩阵）
INSERT INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
SELECT 'super_admin', m.`menu_key`, 1 FROM `menu` m
WHERE NOT EXISTS (SELECT 1 FROM `role_menu` rm WHERE rm.`role_key` = 'super_admin' LIMIT 1);

INSERT INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
SELECT 'admin', m.`menu_key`, CASE WHEN m.`menu_key` IN ('todo', 'admin_permissions', 'admin_roles', 'performance_export', 'performance_batch_create', 'admin_performance_calibration') THEN 0 ELSE 1 END
FROM `menu` m
WHERE NOT EXISTS (SELECT 1 FROM `role_menu` rm WHERE rm.`role_key` = 'admin' LIMIT 1);

INSERT IGNORE INTO `role_menu` (`role_key`, `menu_key`, `allowed`) VALUES
  ('employee', 'todo', 1),
  ('employee', 'home', 0),
  ('employee', 'performance_list', 1),
  ('employee', 'performance_export', 0),
  ('employee', 'performance_list_all', 0),
  ('employee', 'performance_batch_create', 0),
  ('employee', 'performance_review_admin', 0),
  ('employee', 'admin_performance_calibration', 0),
  ('employee', 'my_performance', 1),
  ('employee', 'admin_templates', 0),
  ('employee', 'admin_notifications', 0),
  ('employee', 'admin_employees', 0),
  ('employee', 'admin_roles', 0),
  ('employee', 'admin_permissions', 0),
  ('employee', 'admin_statistics_months', 0),
  ('employee', 'admin_system_config', 0);

UPDATE `role_menu` SET `allowed` = 0
WHERE `menu_key` IN ('performance_export', 'performance_batch_create', 'admin_performance_calibration')
  AND `role_key` <> 'super_admin';

CREATE TABLE IF NOT EXISTS `statistics_month` (
  `id` varchar(36) NOT NULL,
  `year_month` varchar(7) NOT NULL,
  `name` varchar(255) NOT NULL DEFAULT '',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_statistics_month_year_month` (`year_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
