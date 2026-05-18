ALTER TABLE `performance_record`
  ADD COLUMN `manager_summary` TEXT NULL AFTER `personal_summary`,
  ADD COLUMN `dotted_manager_summary` TEXT NULL AFTER `manager_summary`;
