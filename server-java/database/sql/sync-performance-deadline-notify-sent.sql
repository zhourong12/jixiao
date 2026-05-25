-- 绩效节点截止日飞书提醒去重（按节点 key 记录已推送的截止日 yyyy-MM-dd）；已有库执行一次
ALTER TABLE `performance_record`
  ADD COLUMN `deadline_notify_sent` json DEFAULT NULL COMMENT '截止日提醒已推送 {"goal":"yyyy-MM-dd","scoring":"yyyy-MM-dd",...}' AFTER `node_deadlines`;
