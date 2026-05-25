-- 飞书工作台应用角标总开关（默认开启）；已有库可重复执行
INSERT INTO `system_config` (`config_key`, `config_value`) VALUES
  ('feishu_app_badge_enabled', '1')
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;
