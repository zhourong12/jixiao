-- 已有库增量：考核规则表 + 绩效模板关联；可重复执行（表/列已存在时跳过对应语句）。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `assessment_rule` (
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

INSERT IGNORE INTO `assessment_rule` (`id`, `name`, `manager_weight`, `dotted_manager_weight`, `status`) VALUES
  ('arule-default-001', '默认（上级70% / 虚线30%）', 0.7, 0.3, 'enabled');

SET @db := DATABASE();
SET @has_col := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'performance_template' AND COLUMN_NAME = 'assessment_rule_id'
);
SET @sql_add := IF(
  @has_col = 0,
  'ALTER TABLE `performance_template` ADD COLUMN `assessment_rule_id` varchar(36) NULL AFTER `indicators`',
  'SELECT ''skip: assessment_rule_id exists'' AS _msg'
);
PREPARE _stmt FROM @sql_add;
EXECUTE _stmt;
DEALLOCATE PREPARE _stmt;

UPDATE `performance_template`
SET `assessment_rule_id` = 'arule-default-001'
WHERE (`assessment_rule_id` IS NULL OR `assessment_rule_id` = '')
  AND EXISTS (SELECT 1 FROM `assessment_rule` r WHERE r.id = 'arule-default-001');
