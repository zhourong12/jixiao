-- 员工档案绑定考核规则；创建绩效时从 employee_hierarchy.assessment_rule_id 读取。
-- 在已有库执行一次。若列或外键已存在，请跳过对应语句。

SET NAMES utf8mb4;

ALTER TABLE `employee_hierarchy`
  ADD COLUMN `assessment_rule_id` varchar(36) DEFAULT NULL AFTER `feishu_open_id`,
  ADD KEY `idx_employee_hierarchy_assessment_rule` (`assessment_rule_id`),
  ADD CONSTRAINT `fk_employee_hierarchy_assessment_rule`
    FOREIGN KEY (`assessment_rule_id`) REFERENCES `assessment_rule` (`id`) ON DELETE SET NULL;

-- 演示/常见账号回填默认规则（仅 NULL 时写入，避免覆盖已在界面绑定的值）
UPDATE `employee_hierarchy`
SET `assessment_rule_id` = 'arule-default-001'
WHERE `assessment_rule_id` IS NULL
  AND `employee_id` IN (
    'zhou_rong',
    'demo_manager',
    'demo_emp_01',
    'demo_emp_02',
    'demo_dotted_mgr',
    'qa_emp_dotted',
    'qa_admin'
  );
