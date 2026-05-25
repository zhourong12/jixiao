-- 绩效节点截止时间（JSON）与校准负责人；已有库执行一次
ALTER TABLE `performance_record`
  ADD COLUMN `node_deadlines` json DEFAULT NULL COMMENT '各流程节点截止日期 {"goal":"yyyy-MM-dd","scoring":"yyyy-MM-dd",...}' AFTER `period`,
  ADD COLUMN `calibration_owner_id` varchar(255) DEFAULT NULL COMMENT '创建该绩效的管理员，默认负责校准' AFTER `final_reviewed_at`;
