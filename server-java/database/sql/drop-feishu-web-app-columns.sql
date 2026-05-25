-- 已有库：移除独立网页应用凭证与 feishu_web_open_id（登录应用兼网页应用后不再需要）
ALTER TABLE `feishu_app`
  DROP COLUMN IF EXISTS `web_app_secret`,
  DROP COLUMN IF EXISTS `web_app_id`;

ALTER TABLE `employee_hierarchy`
  DROP COLUMN IF EXISTS `feishu_web_open_id`;
