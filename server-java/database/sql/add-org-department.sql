-- 组织部门主数据（按飞书主体）；筛选项与「部门管理」菜单使用。
-- 已有库执行本脚本后，在「部门管理」页点击「从员工档案同步」或调用 POST /api/admin/departments/sync-from-employees。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `org_department` (
  `id` varchar(36) NOT NULL,
  `feishu_subject_id` varchar(36) NOT NULL,
  `parent_id` varchar(36) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `lark_department_id` varchar(255) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_org_dept_subject_name` (`feishu_subject_id`, `name`),
  KEY `idx_org_dept_subject` (`feishu_subject_id`, `enabled`, `sort_order`),
  KEY `idx_org_dept_parent` (`parent_id`),
  CONSTRAINT `fk_org_dept_subject` FOREIGN KEY (`feishu_subject_id`) REFERENCES `feishu_subject` (`id`),
  CONSTRAINT `fk_org_dept_parent` FOREIGN KEY (`parent_id`) REFERENCES `org_department` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
