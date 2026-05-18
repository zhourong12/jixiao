-- 飞书绩效待办总开关（默认开启）；已有库可重复执行
INSERT INTO `system_config` (`config_key`, `config_value`) VALUES
  ('performance_feishu_task_enabled', '1')
ON DUPLICATE KEY UPDATE `config_key` = `config_key`;
