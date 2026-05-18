-- 将 employee_hierarchy 中「仅有 department_name、无 org 部门 id」的记录，
-- 按「飞书主体 + 部门名称」对齐到 org_department.id（与保存员工时 applyDepartmentBinding 一致）。
--
-- 前置：已执行 add-org-department.sql，且 org_department 有数据（部门管理页「从员工档案同步」或手工维护）。
-- 用法：在目标库执行本脚本；可先只跑「一、预览」确认再执行「二、回填」。

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 一、预览（不修改数据）
-- ---------------------------------------------------------------------------

-- 1.1 将被回填的行（有部门名、department_id 为空，且能唯一匹配 org_department）
SELECT
  eh.employee_id,
  eh.name AS employee_name,
  fs.code AS subject_code,
  fs.name AS subject_name,
  eh.department_name,
  od.id AS new_department_id,
  od.name AS org_department_name
FROM employee_hierarchy eh
INNER JOIN org_department od
  ON od.enabled = 1
  AND TRIM(od.name) = TRIM(eh.department_name)
  AND (
    eh.feishu_subject_id = od.feishu_subject_id
    OR (
      eh.feishu_subject_id IS NULL
      AND EXISTS (
        SELECT 1
        FROM feishu_subject fs2
        WHERE fs2.id = od.feishu_subject_id
          AND fs2.code = 'default'
          AND fs2.enabled = 1
      )
    )
  )
LEFT JOIN feishu_subject fs ON fs.id = COALESCE(eh.feishu_subject_id, od.feishu_subject_id)
WHERE TRIM(COALESCE(eh.department_name, '')) <> ''
  AND TRIM(COALESCE(eh.department_id, '')) = ''
ORDER BY fs.code, eh.department_name, eh.name;

-- 1.2 有部门名但主数据里找不到（需先在部门管理补全或同步）
SELECT
  eh.employee_id,
  eh.name AS employee_name,
  fs.code AS subject_code,
  eh.department_name
FROM employee_hierarchy eh
LEFT JOIN feishu_subject fs ON fs.id = eh.feishu_subject_id
WHERE TRIM(COALESCE(eh.department_name, '')) <> ''
  AND TRIM(COALESCE(eh.department_id, '')) = ''
  AND NOT EXISTS (
    SELECT 1
    FROM org_department od
    WHERE od.enabled = 1
      AND TRIM(od.name) = TRIM(eh.department_name)
      AND (
        eh.feishu_subject_id = od.feishu_subject_id
        OR (
          eh.feishu_subject_id IS NULL
          AND EXISTS (
            SELECT 1
            FROM feishu_subject fs2
            WHERE fs2.id = od.feishu_subject_id
              AND fs2.code = 'default'
              AND fs2.enabled = 1
          )
        )
      )
  )
ORDER BY fs.code, eh.department_name, eh.name;

-- ---------------------------------------------------------------------------
-- 二、回填 department_id（并规范 department_name 为主数据名称）
-- ---------------------------------------------------------------------------

UPDATE employee_hierarchy eh
INNER JOIN org_department od
  ON od.enabled = 1
  AND TRIM(od.name) = TRIM(eh.department_name)
  AND (
    eh.feishu_subject_id = od.feishu_subject_id
    OR (
      eh.feishu_subject_id IS NULL
      AND EXISTS (
        SELECT 1
        FROM feishu_subject fs2
        WHERE fs2.id = od.feishu_subject_id
          AND fs2.code = 'default'
          AND fs2.enabled = 1
      )
    )
  )
SET
  eh.department_id = od.id,
  eh.department_name = od.name
WHERE TRIM(COALESCE(eh.department_name, '')) <> ''
  AND TRIM(COALESCE(eh.department_id, '')) = '';

-- ---------------------------------------------------------------------------
-- 三、（可选）department_id 存的是飞书 lark_department_id 时，改为 org_department.id
-- ---------------------------------------------------------------------------

UPDATE employee_hierarchy eh
INNER JOIN org_department od
  ON od.enabled = 1
  AND TRIM(COALESCE(eh.department_id, '')) <> ''
  AND eh.department_id = od.lark_department_id
  AND TRIM(COALESCE(od.lark_department_id, '')) <> ''
  AND (
    eh.feishu_subject_id = od.feishu_subject_id
    OR (
      eh.feishu_subject_id IS NULL
      AND EXISTS (
        SELECT 1
        FROM feishu_subject fs2
        WHERE fs2.id = od.feishu_subject_id
          AND fs2.code = 'default'
          AND fs2.enabled = 1
      )
    )
  )
SET
  eh.department_id = od.id,
  eh.department_name = od.name
WHERE eh.department_id <> od.id;
