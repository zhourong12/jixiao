ALTER TABLE `performance_record`
  ADD COLUMN `deleted_at` datetime DEFAULT NULL AFTER `final_reviewed_at`,
  ADD KEY `idx_performance_record_deleted_at` (`deleted_at`);
