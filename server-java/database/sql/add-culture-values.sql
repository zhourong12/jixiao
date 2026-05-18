-- 绩效记录增加文化价值观评分字段
ALTER TABLE `performance_record`
  ADD COLUMN `culture_self_review` json DEFAULT NULL AFTER `dotted_manager_review`,
  ADD COLUMN `culture_manager_review` json DEFAULT NULL AFTER `culture_self_review`,
  ADD COLUMN `culture_dotted_manager_review` json DEFAULT NULL AFTER `culture_manager_review`;
