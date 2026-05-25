SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `api_token` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `token` varchar(64) NOT NULL COMMENT '随机 hex token',
  `name` varchar(100) NOT NULL COMMENT '标签名称',
  `user_id` varchar(100) NOT NULL COMMENT '创建者 user_id，Token 继承其角色',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime DEFAULT NULL COMMENT 'NULL=永不过期',
  `last_used_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_token_token` (`token`),
  KEY `idx_api_token_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
