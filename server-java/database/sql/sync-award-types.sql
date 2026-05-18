-- 已有库：同步奖项类型为 利他奖 / 月度优秀奖 / 季度优秀奖 / 创新奖 / 年度优秀奖
-- 须先写入 award_type，再改 period_award（外键 fk_period_award_award）
SET NAMES utf8mb4;

INSERT INTO `award_type` (`code`, `name`, `scope`, `max_winners`, `sort_order`, `is_system`) VALUES
  ('altruism', '利他奖', 'both', NULL, 10, 1),
  ('monthly_excellence', '月度优秀奖', 'month', NULL, 20, 1),
  ('quarter_excellence', '季度优秀奖', 'quarter', NULL, 30, 1),
  ('innovation', '创新奖', 'both', NULL, 40, 1),
  ('annual_excellence', '年度优秀奖', 'quarter', NULL, 50, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `scope` = VALUES(`scope`),
  `max_winners` = VALUES(`max_winners`),
  `sort_order` = VALUES(`sort_order`),
  `is_system` = VALUES(`is_system`);

UPDATE `period_award` SET `award_code` = 'quarter_excellence' WHERE `award_code` = 'quarter_star';

DELETE FROM `period_award` WHERE `award_code` IN ('excellent', 'quarter_star');

DELETE FROM `award_type` WHERE `code` IN ('quarter_star', 'excellent');
