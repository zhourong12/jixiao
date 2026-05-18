-- 绩效飞书待办：节点截止配置 + 每条待办记录（已有库可重复执行）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `performance_feishu_task_node_config` (
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
  ('confirm', '员工确认', 7, 6)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `sort_order` = VALUES(`sort_order`);

CREATE TABLE IF NOT EXISTS `performance_feishu_task` (
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

INSERT INTO `menu` (`menu_key`, `name`, `sort_order`) VALUES
  ('admin_performance_feishu_task', '飞书绩效待办', 15)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `sort_order` = VALUES(`sort_order`);

INSERT IGNORE INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
SELECT 'super_admin', m.`menu_key`, 1 FROM `menu` m WHERE m.`menu_key` = 'admin_performance_feishu_task';

INSERT IGNORE INTO `role_menu` (`role_key`, `menu_key`, `allowed`) VALUES
  ('admin', 'admin_performance_feishu_task', 1),
  ('employee', 'admin_performance_feishu_task', 0);
