-- 迁移：将 template_selection 状态统一改为 goal_setting
-- 背景：考核流程不再有独立的"选择模板"步骤，模板选择合并到目标设定阶段
-- 可重复执行（无副作用）

UPDATE performance_record
SET status = 'goal_setting'
WHERE status = 'template_selection'
  AND deleted_at IS NULL;
