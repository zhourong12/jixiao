-- 文化价值观维度：配置在绩效模板；创建绩效时写入记录快照（模板后续修改不影响已创建记录）。
SET @db = DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'performance_template' AND COLUMN_NAME = 'culture_dimensions'
    ),
    'SELECT ''skip: performance_template.culture_dimensions exists'' AS _msg',
    'ALTER TABLE `performance_template` ADD COLUMN `culture_dimensions` json NULL COMMENT ''文化价值观维度定义'' AFTER `indicators`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql2 := (
  SELECT IF(
    EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'performance_record' AND COLUMN_NAME = 'culture_dimensions'
    ),
    'SELECT ''skip: performance_record.culture_dimensions exists'' AS _msg',
    'ALTER TABLE `performance_record` ADD COLUMN `culture_dimensions` json NULL COMMENT ''创建时从模板复制的文化价值观维度快照'' AFTER `template_id`'
  )
);
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
