-- 与 shared/api.interface.ts 中 MENU_PERMISSION_KEYS、侧栏 Layout.tsx 保持一致。
-- 可重复执行：缺失则插入，已存在则更新名称与排序。
SET NAMES utf8mb4;

INSERT INTO `menu` (`menu_key`, `name`, `sort_order`) VALUES
  ('todo', '待办', 1),
  ('home', '工作台', 2),
  ('performance_list', '绩效列表', 3),
  ('performance_export', '导出绩效数据', 4),
  ('my_performance', '我的绩效', 5),
  ('admin_templates', '模板管理', 6),
  ('admin_notifications', '通知管理', 7),
  ('admin_employees', '员工管理', 8),
  ('admin_roles', '角色管理', 9),
  ('admin_permissions', '权限管理', 10),
  ('admin_statistics_months', '周期与评选', 11),
  ('admin_system_config', '系统配置', 12),
  ('performance_list_all', '查看全员绩效', 41),
  ('performance_batch_create', '批量创建绩效', 42),
  ('performance_review_admin', '绩效终审与校准', 43),
  ('admin_performance_calibration', '绩效校准（上级评分）', 44)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `sort_order` = VALUES(`sort_order`);

-- ---------------------------------------------------------------------------
-- 角色-菜单授权：只补「尚未存在」的 (role_key, menu_key)，不覆盖已有开关
-- （与 server/database/sql/initial.sql 中 employee/admin 默认策略一致）
-- ---------------------------------------------------------------------------

INSERT IGNORE INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
SELECT 'super_admin', m.`menu_key`, 1
FROM `menu` m;

INSERT IGNORE INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
SELECT
  'admin',
  m.`menu_key`,
  CASE
    WHEN m.`menu_key` IN ('todo', 'admin_permissions', 'admin_roles', 'performance_export', 'performance_batch_create', 'admin_performance_calibration') THEN 0
    ELSE 1
  END
FROM `menu` m;

INSERT IGNORE INTO `role_menu` (`role_key`, `menu_key`, `allowed`)
SELECT
  'employee',
  m.`menu_key`,
  CASE
    WHEN m.`menu_key` IN ('todo', 'performance_list', 'my_performance') THEN 1
    ELSE 0
  END
FROM `menu` m;

-- 历史库纠偏：导出/批量创建/上级评分校准队列仅 super_admin（与后端 assertSuperAdmin 一致）
UPDATE `role_menu` SET `allowed` = 0
WHERE `menu_key` IN ('performance_export', 'performance_batch_create', 'admin_performance_calibration')
  AND `role_key` <> 'super_admin';

-- 路由对照（侧栏可见项；其余为能力项，无独立路由）：
-- todo                 -> /todo
-- home                 -> /
-- my_performance       -> /my-performance
-- performance_list     -> /performances
-- admin_templates      -> /admin/templates
-- admin_notifications  -> /admin/notifications
-- admin_employees      -> /admin/employees
-- admin_roles          -> /admin/roles
-- admin_permissions    -> /admin/permissions
-- admin_statistics_months -> /admin/statistics-months
-- admin_system_config  -> /admin/system-config
-- performance_list_all / performance_review_admin -> 绩效列表等页内能力
-- admin_performance_calibration -> /admin/performance-calibration（仅 super_admin）
