-- =============================================================================
-- 迁移：评分方案 + 模板类型 + 绩效记录扩展列（已有库执行）
-- =============================================================================
-- 内容概览：
--   1. 新建表 scoring_scheme
--   2. performance_template 增加 type 列及索引；position 与 culture/learning 模板对齐
--   3. performance_record 增加 learning_dimensions、scoring_scheme_id、scoring_weights、
--      culture_template_id、learning_template_id 及学习相关评审 JSON 列
--   4. 插入默认评分方案、默认 culture / learning 模板（INSERT IGNORE，不覆盖已有数据）
--
-- 不包含：菜单与 role_menu（请另行执行 sync-menu-table.sql，以补全 admin_scoring_schemes 等）
--
-- 环境：MySQL 8.0；建议使用 8.0.29+（支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS）。
--       若版本较旧，遇「语法不支持」时请去掉 IF NOT EXISTS 后手工跳过已存在列的错误再执行。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. scoring_scheme
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scoring_scheme` (
  `id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `performance_weight` decimal(5,2) NOT NULL COMMENT '绩效占比 %',
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

INSERT IGNORE INTO `scoring_scheme` (`id`, `name`, `performance_weight`, `culture_weight`, `learning_weight`, `status`) VALUES
  ('scheme-default-001', '默认方案（绩效60% / 文化20% / 学习20%）', 60.00, 20.00, 20.00, 'enabled');

-- ---------------------------------------------------------------------------
-- 2. performance_template：type 列 + 索引；position 默认值（culture/learning 无岗位）
-- ---------------------------------------------------------------------------
ALTER TABLE `performance_template`
  ADD COLUMN IF NOT EXISTS `type` varchar(32) NOT NULL DEFAULT 'performance' COMMENT 'performance|culture|learning'
  AFTER `name`;

SET @idx_tpl_type := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'performance_template'
    AND index_name = 'idx_performance_template_type_status'
);
SET @sql_idx_tpl_type := IF(
  @idx_tpl_type = 0,
  'ALTER TABLE `performance_template` ADD INDEX `idx_performance_template_type_status` (`type`, `status`)',
  'SELECT 1'
);
PREPARE stmt_idx_tpl_type FROM @sql_idx_tpl_type;
EXECUTE stmt_idx_tpl_type;
DEALLOCATE PREPARE stmt_idx_tpl_type;

ALTER TABLE `performance_template`
  MODIFY COLUMN `position` varchar(255) NOT NULL DEFAULT '';

-- ---------------------------------------------------------------------------
-- 3. performance_record：快照与评审列
-- ---------------------------------------------------------------------------
ALTER TABLE `performance_record`
  ADD COLUMN IF NOT EXISTS `learning_dimensions` json DEFAULT NULL COMMENT '创建时从学习模板复制的指标快照' AFTER `culture_dimensions`,
  ADD COLUMN IF NOT EXISTS `scoring_scheme_id` varchar(36) DEFAULT NULL COMMENT '评分方案ID' AFTER `learning_dimensions`,
  ADD COLUMN IF NOT EXISTS `scoring_weights` json DEFAULT NULL COMMENT '评分方案权重快照 {"performance":60,"culture":20,"learning":20}' AFTER `scoring_scheme_id`,
  ADD COLUMN IF NOT EXISTS `culture_template_id` varchar(36) DEFAULT NULL COMMENT '创建时使用的文化模板ID' AFTER `scoring_weights`,
  ADD COLUMN IF NOT EXISTS `learning_template_id` varchar(36) DEFAULT NULL COMMENT '创建时使用的学习模板ID' AFTER `culture_template_id`,
  ADD COLUMN IF NOT EXISTS `learning_self_review` json DEFAULT NULL AFTER `culture_dotted_manager_review`,
  ADD COLUMN IF NOT EXISTS `learning_manager_review` json DEFAULT NULL AFTER `learning_self_review`,
  ADD COLUMN IF NOT EXISTS `learning_dotted_manager_review` json DEFAULT NULL AFTER `learning_manager_review`;

-- ---------------------------------------------------------------------------
-- 4. 默认 culture / learning 模板（无则插入）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `performance_template` (`id`, `name`, `type`, `position`, `indicators`, `culture_dimensions`, `assessment_rule_id`, `status`) VALUES (
  'tpl-culture-001',
  '文化价值观',
  'culture',
  '',
  '[]',
  '[{"name":"利他","maxScore":7,"description":"1. 客户为先，不断完善产品与服务，为用户创造价值。\\n2. 分享与互助，不断提升能力与效率。\\n3. 让和你合作的人成功，相互成就。","criteria":"优秀(7分)，合格(4-6分)，不合格(0分)"},{"name":"本分","maxScore":7,"description":"1. 以诚相待，高效沟通。\\n2. 实事求是，说到做到。\\n3. 敢于质疑，敢于挑战，抓住事物本质。\\n4. 坚持自我批判，不断超越自我。","criteria":"优秀(7分)，合格(4-6分)，不合格(0分)"},{"name":"结果导向","maxScore":6,"description":"1. 以结果来驱动行为，对结果负责。\\n2. 不找借口，突破客观条件限制，整合资源，不惜一切达成结果。\\n3. 关注团队结果，团队成功才有个人价值。","criteria":"优秀(6分)，合格(3-5分)，不合格(0分)"}]',
  NULL,
  'enabled'
);

INSERT IGNORE INTO `performance_template` (`id`, `name`, `type`, `position`, `indicators`, `culture_dimensions`, `assessment_rule_id`, `status`) VALUES (
  'tpl-learning-001',
  '学习与成长',
  'learning',
  '',
  '[{"name":"学习能力","weight":50,"description":"主动学习新知识、新技能的意愿和效果"},{"name":"成长表现","weight":50,"description":"个人能力提升与职业发展的进步情况"}]',
  NULL,
  NULL,
  'enabled'
);
