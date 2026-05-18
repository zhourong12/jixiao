-- 考核规则从模板解绑：记在绩效记录上；模板 assessment_rule_id 改为可空。
-- 可重复执行（列已存在时跳过加列）。
SET NAMES utf8mb4;

SET @db := DATABASE();
SET @has_pr_ar := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'performance_record' AND COLUMN_NAME = 'assessment_rule_id'
);
SET @sql_pr := IF(
  @has_pr_ar = 0,
  'ALTER TABLE `performance_record` ADD COLUMN `assessment_rule_id` varchar(36) NULL AFTER `template_id`',
  'SELECT ''skip: performance_record.assessment_rule_id exists'' AS _msg'
);
PREPARE _stmt_pr FROM @sql_pr;
EXECUTE _stmt_pr;
DEALLOCATE PREPARE _stmt_pr;

UPDATE `performance_record` pr
INNER JOIN `performance_template` pt ON pt.id = pr.template_id
SET pr.assessment_rule_id = pt.assessment_rule_id
WHERE pr.assessment_rule_id IS NULL
  AND pt.assessment_rule_id IS NOT NULL
  AND pt.assessment_rule_id <> '';

ALTER TABLE `performance_template` MODIFY COLUMN `assessment_rule_id` varchar(36) NULL;
