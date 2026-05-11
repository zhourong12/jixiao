-- 已有 evaluation_period 表、但缺少 parent_period_id 时执行一次（可重复执行需自行判断）
-- 月度周期通过 parent_period_id 归属季度；删除季度时子月 parent 置 NULL。
SET NAMES utf8mb4;

ALTER TABLE `evaluation_period`
  ADD COLUMN `parent_period_id` varchar(36) DEFAULT NULL AFTER `status`;

ALTER TABLE `evaluation_period`
  ADD KEY `idx_evaluation_period_parent` (`parent_period_id`);

ALTER TABLE `evaluation_period`
  ADD CONSTRAINT `fk_evaluation_period_parent` FOREIGN KEY (`parent_period_id`) REFERENCES `evaluation_period` (`id`) ON DELETE SET NULL;
