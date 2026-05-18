-- 多飞书主体：已有库执行一次（MySQL 5.7+）
-- mysql -h HOST -u USER -p DATABASE < server-java/database/sql/add-feishu-multi-tenant.sql

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- feishu_subject
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `feishu_subject` (
  `id` varchar(36) NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `notify_frontend_base_url` varchar(512) DEFAULT NULL,
  `notify_feishu_web_app_url` varchar(512) DEFAULT NULL,
  `web_app_link_app_id` varchar(128) DEFAULT NULL,
  `performance_notify_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_feishu_subject_code` (`code`),
  KEY `idx_feishu_subject_enabled` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- feishu_app
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `feishu_app` (
  `id` varchar(36) NOT NULL,
  `feishu_subject_id` varchar(36) NOT NULL,
  `name` varchar(128) NOT NULL DEFAULT '',
  `app_id` varchar(128) NOT NULL,
  `app_secret` varchar(512) NOT NULL,
  `redirect_uri` varchar(1024) NOT NULL,
  `is_login_app` tinyint(1) NOT NULL DEFAULT 0,
  `is_im_app` tinyint(1) NOT NULL DEFAULT 0,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_feishu_app_subject` (`feishu_subject_id`, `enabled`, `sort_order`),
  CONSTRAINT `fk_feishu_app_subject` FOREIGN KEY (`feishu_subject_id`) REFERENCES `feishu_subject` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- employee_hierarchy：主体 + 飞书 open_id
-- ---------------------------------------------------------------------------
ALTER TABLE `employee_hierarchy`
  ADD COLUMN `feishu_subject_id` varchar(36) DEFAULT NULL AFTER `employee_id`,
  ADD COLUMN `feishu_open_id` varchar(255) DEFAULT NULL AFTER `feishu_subject_id`,
  ADD KEY `idx_employee_hierarchy_feishu_subject` (`feishu_subject_id`),
  ADD KEY `idx_employee_hierarchy_feishu_subject_open` (`feishu_subject_id`, `feishu_open_id`);

ALTER TABLE `employee_hierarchy`
  ADD CONSTRAINT `fk_employee_hierarchy_feishu_subject`
  FOREIGN KEY (`feishu_subject_id`) REFERENCES `feishu_subject` (`id`) ON DELETE SET NULL;

-- 默认主体（请按环境 UPDATE feishu_app 的 app_id/app_secret/redirect_uri）
INSERT INTO `feishu_subject` (
  `id`, `code`, `name`, `sort_order`, `enabled`,
  `notify_frontend_base_url`, `notify_feishu_web_app_url`, `web_app_link_app_id`, `performance_notify_enabled`
) VALUES (
  'a0000001-0000-4000-8000-000000000001',
  'default',
  '默认主体',
  0,
  1,
  NULL,
  NULL,
  NULL,
  1
) ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 占位第二条主体（需自行配置应用后再 enabled=1）
INSERT INTO `feishu_subject` (
  `id`, `code`, `name`, `sort_order`, `enabled`,
  `notify_frontend_base_url`, `notify_feishu_web_app_url`, `web_app_link_app_id`, `performance_notify_enabled`
) VALUES (
  'a0000002-0000-4000-8000-000000000002',
  'corp_b',
  '主体B',
  1,
  0,
  NULL,
  NULL,
  NULL,
  1
) ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 默认主体应用：请替换 app_secret、redirect_uri 为飞书控制台实际值
INSERT INTO `feishu_app` (
  `id`, `feishu_subject_id`, `name`, `app_id`, `app_secret`, `redirect_uri`,
  `is_login_app`, `is_im_app`, `sort_order`, `enabled`
) VALUES (
  'b0000001-0000-4000-8000-000000000001',
  'a0000001-0000-4000-8000-000000000001',
  '默认自建应用',
  'REPLACE_APP_ID',
  'REPLACE_APP_SECRET',
  'REPLACE_REDIRECT_URI',
  1,
  1,
  0,
  1
) ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- ---------------------------------------------------------------------------
-- 同一主体「多应用」说明（feishu_app 多行挂同一 feishu_subject_id）
-- ---------------------------------------------------------------------------
-- is_login_app = 1：OAuth 授权与换 user_access_token（每主体至少一条启用的登录应用）。
-- is_im_app    = 1：换 tenant_access_token，用于通讯录同步、发飞书消息/卡片。
-- 代码选 IM 行：ORDER BY is_im_app DESC, sort_order ASC LIMIT 1（见 FeishuRegistryService）。
--
-- 常见 A：一行兼任登录 + IM（本文件上方默认 INSERT：is_login_app=1 AND is_im_app=1）。
--
-- 常见 B：拆成两条——登录应用只 OAuth；机器人只发消息。拆分时请只让「一条」is_im_app=1，
--         并把原合并行的 is_im_app 改为 0，避免两条 is_im_app=1 时仅靠 sort_order 抢行。
--
-- 示例（请取消注释并替换 app_id / app_secret / redirect_uri 后执行）：
--
-- INSERT INTO `feishu_app` (
--   `id`, `feishu_subject_id`, `name`, `app_id`, `app_secret`, `redirect_uri`,
--   `is_login_app`, `is_im_app`, `sort_order`, `enabled`
-- ) VALUES (
--   'b0000002-0000-4000-8000-000000000001',
--   'a0000001-0000-4000-8000-000000000001',
--   'IM 机器人应用',
--   'REPLACE_IM_APP_ID',
--   'REPLACE_IM_APP_SECRET',
--   'https://你的域名/auth/feishu/callback',
--   0,
--   1,
--   0,
--   1
-- ) ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `app_id` = VALUES(`app_id`);
-- UPDATE `feishu_app` SET `is_im_app` = 0 WHERE `id` = 'b0000001-0000-4000-8000-000000000001';
--
-- 为「主体B」(id=a0000002-...) 启用飞书时，需再 INSERT 至少一条 feishu_app（is_login_app=1），
-- 并把对应 feishu_subject.enabled 改为 1。

-- 回填：飞书 open_id 形态写入 feishu_open_id 并挂默认主体
UPDATE `employee_hierarchy`
SET
  `feishu_subject_id` = 'a0000001-0000-4000-8000-000000000001',
  `feishu_open_id` = `employee_id`
WHERE (`employee_id` LIKE 'ou\_%' ESCAPE '\\' OR `employee_id` LIKE 'on\_%' ESCAPE '\\')
  AND (`feishu_open_id` IS NULL OR `feishu_open_id` = '');
