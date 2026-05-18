-- 修复文化模板和绩效记录中 culture_dimensions 被缩放到合计100的满分值
-- 可重复执行，幂等。

-- 1. 先修复文化模板本身（35/35/30 → 7/7/6）
UPDATE performance_template
SET culture_dimensions = '[{"name":"利他","maxScore":7,"description":"1. 客户为先，不断完善产品与服务，为用户创造价值。\\n2. 分享与互助，不断提升能力与效率。\\n3. 让和你合作的人成功，相互成就。","criteria":"优秀(7分)，合格(4-6分)，不合格(0分)"},{"name":"本分","maxScore":7,"description":"1. 以诚相待，高效沟通。\\n2. 实事求是，说到做到。\\n3. 敢于质疑，敢于挑战，抓住事物本质。\\n4. 坚持自我批判，不断超越自我。","criteria":"优秀(7分)，合格(4-6分)，不合格(0分)"},{"name":"结果导向","maxScore":6,"description":"1. 以结果来驱动行为，对结果负责。\\n2. 不找借口，突破客观条件限制，整合资源，不惜一切达成结果。\\n3. 关注团队结果，团队成功才有个人价值。","criteria":"优秀(6分)，合格(3-5分)，不合格(0分)"}]'
WHERE type = 'culture' AND status = 'enabled';

-- 2. 再把所有绩效记录的 culture_dimensions 快照同步为模板数据
UPDATE performance_record pr
JOIN performance_template pt
  ON pt.id = COALESCE(pr.culture_template_id,
    (SELECT id FROM performance_template WHERE type = 'culture' AND status = 'enabled' ORDER BY _updated_at DESC LIMIT 1))
SET pr.culture_dimensions = pt.culture_dimensions
WHERE pr.deleted_at IS NULL
  AND pt.culture_dimensions IS NOT NULL
  AND JSON_LENGTH(pt.culture_dimensions) > 0;
