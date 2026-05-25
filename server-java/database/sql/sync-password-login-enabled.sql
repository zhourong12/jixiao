-- 账密登录总开关（默认关闭）；已有库可重复执行，不覆盖已手动修改的值
INSERT INTO `system_config` (`config_key`, `config_value`) VALUES
  ('password_login_enabled', '0')
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;
