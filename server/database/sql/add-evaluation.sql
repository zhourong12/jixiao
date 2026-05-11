-- 已有库增量：评选周期 / 奖项 / 获奖记录（替代 statistics_month）
-- 可重复执行：表使用 IF NOT EXISTS；奖项种子使用 INSERT IGNORE
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `award_type` (
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `scope` varchar(16) NOT NULL,
  `max_winners` int(11) DEFAULT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `is_system` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `award_type` (`code`, `name`, `scope`, `max_winners`, `sort_order`, `is_system`) VALUES
  ('monthly_excellence', '月度优秀奖', 'month', NULL, 10, 1),
  ('quarter_star', '季度之星', 'quarter', 1, 20, 1),
  ('altruism', '利他奖', 'both', NULL, 30, 1),
  ('excellent', '优秀', 'both', NULL, 40, 1);

CREATE TABLE IF NOT EXISTS `evaluation_period` (
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

CREATE TABLE IF NOT EXISTS `period_award` (
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

SET @sm_exists := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'statistics_month'
);
SET @migrate_sql := IF(@sm_exists > 0,
  'INSERT IGNORE INTO `evaluation_period` (`id`, `period_type`, `period_key`, `name`, `sort_order`, `status`, `_created_at`, `_updated_at`) SELECT `id`, ''month'', `year_month`, `name`, `sort_order`, ''open'', `_created_at`, `_updated_at` FROM `statistics_month`',
  'SELECT 1');
PREPARE migrate_stmt FROM @migrate_sql;
EXECUTE migrate_stmt;
DEALLOCATE PREPARE migrate_stmt;

SET @drop_sql := IF(@sm_exists > 0, 'DROP TABLE `statistics_month`', 'SELECT 1');
PREPARE drop_stmt FROM @drop_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;

UPDATE `menu` SET `name` = '周期与评选' WHERE `menu_key` = 'admin_statistics_months';
