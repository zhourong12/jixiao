-- 清理 performance_record 中「被考核人不在 employee_hierarchy」的脏数据
-- 执行前请先跑「1. 预览」，确认条数与样例后再执行「2. 软删除」或「3. 物理删除」
-- 建议：优先用软删除（与后台「删除绩效」一致，deleted_at 非空后列表不可见）

-- ---------------------------------------------------------------------------
-- 1. 预览：将被清理的绩效（未软删）
-- ---------------------------------------------------------------------------
SELECT
  pr.id,
  pr.employee_id,
  pr.period,
  pr.status,
  pr.manager_id,
  pr.dotted_manager_id,
  pr._created_at
FROM performance_record pr
LEFT JOIN employee_hierarchy eh ON eh.employee_id = pr.employee_id
WHERE pr.deleted_at IS NULL
  AND eh.employee_id IS NULL
ORDER BY pr.period DESC, pr._created_at DESC;

SELECT COUNT(*) AS orphan_active_count
FROM performance_record pr
LEFT JOIN employee_hierarchy eh ON eh.employee_id = pr.employee_id
WHERE pr.deleted_at IS NULL
  AND eh.employee_id IS NULL;

-- 可选：上级 / 虚线上级也不存在于员工表（仅查看，默认不删）
-- SELECT pr.id, pr.employee_id, pr.period, pr.status, pr.manager_id, pr.dotted_manager_id
-- FROM performance_record pr
-- LEFT JOIN employee_hierarchy m ON m.employee_id = pr.manager_id
-- WHERE pr.deleted_at IS NULL AND m.employee_id IS NULL;

-- ---------------------------------------------------------------------------
-- 2. 软删除（推荐）
-- ---------------------------------------------------------------------------
-- START TRANSACTION;
-- UPDATE performance_record pr
-- LEFT JOIN employee_hierarchy eh ON eh.employee_id = pr.employee_id
-- SET pr.deleted_at = NOW(),
--     pr._updated_by = 'sql-cleanup-orphan-employee'
-- WHERE pr.deleted_at IS NULL
--   AND eh.employee_id IS NULL;
-- SELECT ROW_COUNT() AS soft_deleted_rows;
-- COMMIT;

-- ---------------------------------------------------------------------------
-- 3. 物理删除（慎用：会级联删除 performance_record_flow_log、performance_feishu_task 等）
-- ---------------------------------------------------------------------------
-- START TRANSACTION;
-- DELETE pr FROM performance_record pr
-- LEFT JOIN employee_hierarchy eh ON eh.employee_id = pr.employee_id
-- WHERE pr.deleted_at IS NULL
--   AND eh.employee_id IS NULL;
-- SELECT ROW_COUNT() AS hard_deleted_rows;
-- COMMIT;

-- ---------------------------------------------------------------------------
-- 4. 若还要清已软删但仍无员工的历史脏数据
-- ---------------------------------------------------------------------------
-- SELECT COUNT(*) FROM performance_record pr
-- LEFT JOIN employee_hierarchy eh ON eh.employee_id = pr.employee_id
-- WHERE pr.deleted_at IS NOT NULL AND eh.employee_id IS NULL;
