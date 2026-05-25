-- 绩效截止提醒去重 + 回退锚点（已有库执行一次）
-- 若某列已存在会报 Duplicate column，跳过对应语句即可

ALTER TABLE `performance_record`
  ADD COLUMN `deadline_notify_sent` json DEFAULT NULL COMMENT '截止日飞书提醒已推送记录' AFTER `node_deadlines`;

ALTER TABLE `performance_record`
  ADD COLUMN `deadline_flow_anchor` varchar(50) DEFAULT NULL COMMENT '自动推进前状态，供计划执行/校准回退恢复' AFTER `deadline_notify_sent`;
