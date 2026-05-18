CREATE TABLE IF NOT EXISTS `performance_record_flow_log` (
  `id` varchar(36) NOT NULL,
  `performance_record_id` varchar(36) NOT NULL,
  `from_status` varchar(50) NOT NULL,
  `to_status` varchar(50) NOT NULL,
  `action` varchar(64) NOT NULL,
  `actor_user_id` varchar(255) NOT NULL,
  `remark` varchar(2000) DEFAULT NULL,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_perf_flow_record_created` (`performance_record_id`, `_created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
