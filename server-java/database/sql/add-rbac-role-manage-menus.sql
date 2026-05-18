-- 增量：角色管理菜单 + 绩效细粒度权限项（可重复执行）
SET NAMES utf8mb4;

INSERT IGNORE INTO `menu` (`menu_key`, `name`, `sort_order`) VALUES
  ('performance_list_all', '查看全员绩效', 41),
  ('performance_batch_create', '批量创建绩效', 42),
  ('performance_review_admin', '绩效终审与校准', 43),
  ('admin_roles', '角色管理', 9);

INSERT IGNORE INTO `role_menu` (`role_key`, `menu_key`, `allowed`) VALUES
  ('super_admin', 'performance_list_all', 1),
  ('super_admin', 'performance_batch_create', 1),
  ('super_admin', 'performance_review_admin', 1),
  ('super_admin', 'admin_roles', 1),
  ('admin', 'performance_list_all', 1),
  ('admin', 'performance_batch_create', 0),
  ('admin', 'performance_export', 0),
  ('admin', 'performance_review_admin', 1),
  ('admin', 'admin_roles', 0),
  ('employee', 'performance_list_all', 0),
  ('employee', 'performance_batch_create', 0),
  ('employee', 'performance_review_admin', 0),
  ('employee', 'admin_roles', 0);
