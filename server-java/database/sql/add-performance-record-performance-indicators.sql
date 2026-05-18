-- 绩效记录：绩效指标 JSON 快照（可选；为空时仍从所选 performance 模板 indicators 读取）
-- 用于目标阶段在模板基础上增删改指标，或完全不选模板自定义指标。

SET @db = DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'performance_record' AND COLUMN_NAME = 'performance_indicators'
    ),
    'SELECT ''skip: performance_record.performance_indicators exists'' AS _msg',
    'ALTER TABLE `performance_record` ADD COLUMN `performance_indicators` json NULL COMMENT ''绩效指标快照（目标阶段可编辑；为空则从模板读）'' AFTER `template_id`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
