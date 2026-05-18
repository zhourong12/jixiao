-- 绩效记录：按加权总分写入评分等级 S/A/B/C（>95 S，>90 A，>70 B，否则 C）

SET @db = DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'performance_record' AND COLUMN_NAME = 'score_grade'
    ),
    'SELECT ''skip: performance_record.score_grade exists'' AS _msg',
    'ALTER TABLE `performance_record` ADD COLUMN `score_grade` varchar(4) DEFAULT NULL COMMENT ''评分等级 S/A/B/C'' AFTER `total_score`'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `performance_record`
SET `score_grade` = CASE
  WHEN `total_score` IS NULL THEN NULL
  WHEN `total_score` > 95 THEN 'S'
  WHEN `total_score` > 90 THEN 'A'
  WHEN `total_score` > 70 THEN 'B'
  ELSE 'C'
END
WHERE `deleted_at` IS NULL;
