-- Newman / 自动化回归前可重复执行：清理历史跑批数据
SET NAMES utf8mb4;

DELETE FROM period_award
WHERE period_id IN (
  SELECT id FROM evaluation_period
  WHERE period_key REGEXP '^209[6-9]-' AND name LIKE 'Newman%'
);

DELETE FROM evaluation_period
WHERE period_key REGEXP '^209[6-9]-' AND name LIKE 'Newman%';

DELETE FROM performance_record
WHERE period LIKE '2099-12-newman%'
   OR period LIKE '2099-nm-%';

DELETE FROM `role` WHERE role_key = 'newman_demo_role';
