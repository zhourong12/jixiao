package com.jixiao2.server.evaluation;

import com.jixiao2.server.menu.MenuPermissionService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvaluationService {

  private static final Pattern YEAR_MONTH = Pattern.compile("^(\\d{4})-(\\d{2})$");
  private static final Pattern QUARTER = Pattern.compile("^\\d{4}-Q[1-4]$");

  private final JdbcTemplate jdbc;
  private final MenuPermissionService menuPermissionService;

  public EvaluationService(JdbcTemplate jdbc, MenuPermissionService menuPermissionService) {
    this.jdbc = jdbc;
    this.menuPermissionService = menuPermissionService;
  }

  private void assertAllowed(String userId) {
    menuPermissionService.assertMenuAllowed(userId, "admin_statistics_months");
  }

  private static String toIso(Timestamp ts) {
    return ts == null ? "" : ts.toInstant().toString();
  }

  private static int[] parseYearMonth(String ym) {
    Matcher m = YEAR_MONTH.matcher(ym == null ? "" : ym.trim());
    if (!m.matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月度 key 须为 YYYY-MM 格式");
    }
    int year = Integer.parseInt(m.group(1));
    int month = Integer.parseInt(m.group(2));
    if (month < 1 || month > 12) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月度 key 无效");
    }
    return new int[] {year, month};
  }

  private static String parsePerformanceQuarterPeriod(String p) {
    String t = p == null ? "" : p.trim();
    if (t.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定绩效季度 key");
    }
    if (!QUARTER.matcher(t).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "季度 key 须为 YYYY-Q1～Q4 格式");
    }
    return t;
  }

  private static String normalizePeriodKey(String periodType, String raw) {
    if ("month".equals(periodType)) {
      int[] ym = parseYearMonth(raw);
      return String.format("%04d-%02d", ym[0], ym[1]);
    }
    return parsePerformanceQuarterPeriod(raw);
  }

  private static boolean awardScopeMatches(String awardScope, String periodType) {
    return "both".equals(awardScope) || awardScope.equals(periodType);
  }

  private String parentPeriodKeyForRow(String parentPeriodId) {
    if (parentPeriodId == null || parentPeriodId.isEmpty()) {
      return null;
    }
    List<String> keys =
        jdbc.query(
            "SELECT period_key FROM evaluation_period WHERE id = ? LIMIT 1",
            new Object[] {parentPeriodId},
            (rs, rn) -> rs.getString("period_key"));
    return keys.isEmpty() ? null : keys.get(0);
  }

  private String resolveMonthParentId(String raw) {
    String t = raw == null ? "" : raw.trim();
    if (t.isEmpty()) {
      return null;
    }
    List<Map<String, Object>> parent =
        jdbc.query(
            "SELECT id, period_type FROM evaluation_period WHERE id = ? LIMIT 1",
            new Object[] {t},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("periodType", rs.getString("period_type"));
              return m;
            });
    if (parent.isEmpty() || !"quarter".equals(String.valueOf(parent.get(0).get("periodType")))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所属季度无效");
    }
    return t;
  }

  private Map<String, Object> mapPeriodRow(
      String id,
      String periodType,
      String periodKey,
      String name,
      int sortOrder,
      String status,
      String parentPeriodId,
      Timestamp createdAt,
      Timestamp updatedAt,
      String parentPeriodKey) {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("id", id);
    item.put("periodType", periodType);
    item.put("periodKey", periodKey);
    item.put("name", name);
    item.put("sortOrder", sortOrder);
    item.put("status", status);
    item.put("parentPeriodId", parentPeriodId);
    item.put("parentPeriodKey", parentPeriodKey);
    item.put("createdAt", toIso(createdAt));
    item.put("updatedAt", toIso(updatedAt));
    return item;
  }

  public Map<String, Object> listPeriods(String userId, String periodType) {
    assertAllowed(userId);
    List<Object> args = new ArrayList<Object>();
    String sql =
        "SELECT ep.id, ep.period_type, ep.period_key, ep.name, ep.sort_order, ep.status, ep.parent_period_id, ep._created_at, ep._updated_at, parent.period_key AS parent_period_key "
            + "FROM evaluation_period ep LEFT JOIN evaluation_period parent ON ep.parent_period_id = parent.id";
    if (periodType != null && !periodType.isEmpty()) {
      sql += " WHERE ep.period_type = ?";
      args.add(periodType);
    }
    sql += " ORDER BY ep.sort_order ASC, ep.period_key DESC";
    List<Map<String, Object>> rows =
        jdbc.query(
            sql,
            args.toArray(),
            (rs, rn) ->
                mapPeriodRow(
                    rs.getString("id"),
                    rs.getString("period_type"),
                    rs.getString("period_key"),
                    rs.getString("name"),
                    rs.getInt("sort_order"),
                    rs.getString("status"),
                    rs.getString("parent_period_id"),
                    rs.getTimestamp("_created_at"),
                    rs.getTimestamp("_updated_at"),
                    rs.getString("parent_period_key")));
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", rows);
    return out;
  }

  public Map<String, Object> createPeriod(String userId, Map<String, Object> body) {
    assertAllowed(userId);
    String pt = String.valueOf(body.get("periodType"));
    if (!"month".equals(pt) && !"quarter".equals(pt)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodType 须为 month 或 quarter");
    }
    String periodKey = normalizePeriodKey(pt, String.valueOf(body.get("periodKey")));
    Integer dup =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM evaluation_period WHERE period_type = ? AND period_key = ?",
            Integer.class,
            pt,
            periodKey);
    if (dup != null && dup > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "周期 " + pt + " / " + periodKey + " 已存在");
    }
    if ("quarter".equals(pt) && body.get("parentPeriodId") != null && !String.valueOf(body.get("parentPeriodId")).trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "季度周期不能设置所属季度");
    }
    String parentId = "month".equals(pt) ? resolveMonthParentId(String.valueOf(body.get("parentPeriodId"))) : null;
    String id = UUID.randomUUID().toString();
    int sortOrder = body.get("sortOrder") instanceof Number ? ((Number) body.get("sortOrder")).intValue() : 0;
    String status = body.get("status") == null ? "open" : String.valueOf(body.get("status")).trim();
    if (status.isEmpty()) {
      status = "open";
    }
    String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
    jdbc.update(
        "INSERT INTO evaluation_period (id, period_type, period_key, name, sort_order, status, parent_period_id) VALUES (?,?,?,?,?,?,?)",
        id,
        pt,
        periodKey,
        name,
        sortOrder,
        status,
        parentId);
    return getPeriodById(id);
  }

  private Map<String, Object> getPeriodById(String id) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT id, period_type, period_key, name, sort_order, status, parent_period_id, _created_at, _updated_at FROM evaluation_period WHERE id = ?",
            new Object[] {id},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("periodType", rs.getString("period_type"));
              m.put("periodKey", rs.getString("period_key"));
              m.put("name", rs.getString("name"));
              m.put("sortOrder", rs.getInt("sort_order"));
              m.put("status", rs.getString("status"));
              m.put("parentPeriodId", rs.getString("parent_period_id"));
              m.put("createdAt", toIso(rs.getTimestamp("_created_at")));
              m.put("updatedAt", toIso(rs.getTimestamp("_updated_at")));
              return m;
            });
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评选周期不存在");
    }
    Map<String, Object> row = rows.get(0);
    row.put(
        "parentPeriodKey",
        parentPeriodKeyForRow((String) row.get("parentPeriodId")));
    return row;
  }

  public Map<String, Object> updatePeriod(String userId, String id, Map<String, Object> body) {
    assertAllowed(userId);
    Map<String, Object> existing = getPeriodById(id);
    String periodType = String.valueOf(existing.get("periodType"));
    if (body.containsKey("periodType")) {
      periodType = String.valueOf(body.get("periodType"));
      if (!"month".equals(periodType) && !"quarter".equals(periodType)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodType 须为 month 或 quarter");
      }
    }
    String periodKey = String.valueOf(existing.get("periodKey"));
    if (body.containsKey("periodKey") && !String.valueOf(body.get("periodKey")).trim().isEmpty()) {
      periodKey = normalizePeriodKey(periodType, String.valueOf(body.get("periodKey")));
    }
    if (!periodKey.equals(existing.get("periodKey")) || !periodType.equals(existing.get("periodType"))) {
      Integer dup =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM evaluation_period WHERE period_type = ? AND period_key = ? AND id <> ?",
              Integer.class,
              periodType,
              periodKey,
              id);
      if (dup != null && dup > 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "周期 " + periodType + " / " + periodKey + " 已被占用");
      }
    }
    if ("quarter".equals(periodType) && body.get("parentPeriodId") != null && !String.valueOf(body.get("parentPeriodId")).trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "季度周期不能设置所属季度");
    }
    List<String> sets = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();
    if (body.containsKey("periodType")) {
      sets.add("period_type = ?");
      args.add(periodType);
    }
    if (body.containsKey("periodKey") && !String.valueOf(body.get("periodKey")).trim().isEmpty()) {
      sets.add("period_key = ?");
      args.add(periodKey);
    }
    if (body.containsKey("name")) {
      sets.add("name = ?");
      args.add(String.valueOf(body.get("name")).trim());
    }
    if (body.containsKey("sortOrder")) {
      sets.add("sort_order = ?");
      args.add(((Number) body.get("sortOrder")).intValue());
    }
    if (body.containsKey("status")) {
      String st = String.valueOf(body.get("status")).trim();
      sets.add("status = ?");
      args.add(st.isEmpty() ? "open" : st);
    }
    if ("quarter".equals(periodType)) {
      sets.add("parent_period_id = NULL");
    } else if (body.containsKey("parentPeriodId")) {
      sets.add("parent_period_id = ?");
      args.add(resolveMonthParentId(String.valueOf(body.get("parentPeriodId"))));
    }
    if (!sets.isEmpty()) {
      args.add(id);
      jdbc.update(
          "UPDATE evaluation_period SET " + String.join(", ", sets) + " WHERE id = ?",
          args.toArray());
    }
    return getPeriodById(id);
  }

  public void removePeriod(String userId, String id) {
    assertAllowed(userId);
    Integer count =
        jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_period WHERE id = ?", Integer.class, id);
    if (count == null || count == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评选周期不存在");
    }
    jdbc.update("DELETE FROM evaluation_period WHERE id = ?", id);
  }

  public Map<String, Object> listAwardTypes(String userId) {
    assertAllowed(userId);
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT code, name, scope, max_winners, sort_order, is_system FROM award_type ORDER BY sort_order",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("code", rs.getString("code"));
              m.put("name", rs.getString("name"));
              m.put("scope", rs.getString("scope"));
              m.put("maxWinners", rs.getObject("max_winners"));
              m.put("sortOrder", rs.getInt("sort_order"));
              m.put("isSystem", rs.getInt("is_system") != 0);
              return m;
            });
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  public Map<String, Object> listPerformancePeriods(String userId) {
    assertAllowed(userId);
    List<String> items =
        jdbc.query(
            "SELECT period FROM performance_record GROUP BY period ORDER BY period DESC",
            (rs, rn) -> rs.getString("period"));
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  public Map<String, Object> getLeaderboard(
      String userId, String scope, String key, List<String> departmentIds) {
    assertAllowed(userId);
    if (!"month".equals(scope) && !"quarter".equals(scope)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope 须为 month 或 quarter");
    }
    String keyNorm = normalizePeriodKey(scope, key);
    StringBuilder sql =
        new StringBuilder(
            "SELECT pr.id AS record_id, pr.employee_id, pr.total_score, pr.period, eh.name AS employee_name, eh.department_id, eh.department_name "
                + "FROM performance_record pr INNER JOIN employee_hierarchy eh ON pr.employee_id = eh.employee_id "
                + "WHERE pr.status = 'completed' AND pr.total_score IS NOT NULL");
    List<Object> args = new ArrayList<Object>();
    if ("month".equals(scope)) {
      int[] ym = parseYearMonth(keyNorm);
      LocalDate start = YearMonth.of(ym[0], ym[1]).atDay(1);
      LocalDate end = YearMonth.of(ym[0], ym[1]).plusMonths(1).atDay(1);
      sql.append(" AND pr._updated_at >= ? AND pr._updated_at < ?");
      args.add(Timestamp.valueOf(start.atStartOfDay()));
      args.add(Timestamp.valueOf(end.atStartOfDay()));
    } else {
      sql.append(" AND pr.period = ?");
      args.add(keyNorm);
    }
    if (departmentIds != null && !departmentIds.isEmpty()) {
      sql.append(" AND eh.department_id IN (");
      sql.append(String.join(",", Collections.nCopies(departmentIds.size(), "?")));
      sql.append(")");
      args.addAll(departmentIds);
    }
    sql.append(" ORDER BY pr.total_score DESC");
    List<Map<String, Object>> rows =
        jdbc.query(
            sql.toString(),
            args.toArray(),
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("recordId", rs.getString("record_id"));
              m.put("employeeId", rs.getString("employee_id"));
              m.put("totalScore", rs.getBigDecimal("total_score"));
              m.put("period", rs.getString("period"));
              m.put("employeeName", rs.getString("employee_name"));
              m.put("departmentId", rs.getString("department_id"));
              m.put("departmentName", rs.getString("department_name"));
              return m;
            });
    Map<String, Map<String, Object>> bestByEmployee = new HashMap<String, Map<String, Object>>();
    for (Map<String, Object> r : rows) {
      BigDecimal score = (BigDecimal) r.get("totalScore");
      if (score == null) {
        continue;
      }
      String eid = String.valueOf(r.get("employeeId"));
      Map<String, Object> prev = bestByEmployee.get(eid);
      if (prev == null || score.compareTo((BigDecimal) prev.get("totalScore")) > 0) {
        bestByEmployee.put(eid, r);
      }
    }
    List<Map<String, Object>> sorted = new ArrayList<Map<String, Object>>(bestByEmployee.values());
    Collections.sort(
        sorted,
        (a, b) -> ((BigDecimal) b.get("totalScore")).compareTo((BigDecimal) a.get("totalScore")));
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < sorted.size(); i++) {
      Map<String, Object> r = sorted.get(i);
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("rank", i + 1);
      item.put("employeeId", r.get("employeeId"));
      String name = r.get("employeeName") == null ? String.valueOf(r.get("employeeId")) : String.valueOf(r.get("employeeName"));
      item.put("employeeName", name);
      item.put("departmentId", r.get("departmentId"));
      item.put("departmentName", r.get("departmentName"));
      item.put("totalScore", r.get("totalScore"));
      item.put("performancePeriod", r.get("period"));
      item.put("recordId", r.get("recordId"));
      items.add(item);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("scope", scope);
    out.put("key", keyNorm);
    return out;
  }

  public Map<String, Object> listPeriodAwards(String userId, String periodId) {
    assertAllowed(userId);
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM evaluation_period WHERE id = ?", Integer.class, periodId);
    if (count == null || count == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评选周期不存在");
    }
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT pa.id, pa.period_id, pa.award_code, at.name AS award_name, pa.employee_id, eh.name AS employee_name, pa.performance_record_id, pa.remark, pa.created_by, pa._created_at "
                + "FROM period_award pa INNER JOIN award_type at ON pa.award_code = at.code LEFT JOIN employee_hierarchy eh ON pa.employee_id = eh.employee_id "
                + "WHERE pa.period_id = ? ORDER BY at.sort_order ASC, pa._created_at ASC",
            new Object[] {periodId},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("periodId", rs.getString("period_id"));
              m.put("awardCode", rs.getString("award_code"));
              m.put("awardName", rs.getString("award_name"));
              m.put("employeeId", rs.getString("employee_id"));
              m.put("employeeName", rs.getString("employee_name"));
              m.put("performanceRecordId", rs.getString("performance_record_id"));
              m.put("remark", rs.getString("remark"));
              m.put("createdBy", rs.getString("created_by"));
              m.put("createdAt", toIso(rs.getTimestamp("_created_at")));
              return m;
            });
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  public Map<String, Object> createPeriodAward(String userId, Map<String, Object> body) {
    assertAllowed(userId);
    String periodId = String.valueOf(body.get("periodId"));
    List<Map<String, Object>> periodRows =
        jdbc.query(
            "SELECT period_type FROM evaluation_period WHERE id = ?",
            new Object[] {periodId},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("periodType", rs.getString("period_type"));
              return m;
            });
    if (periodRows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评选周期不存在");
    }
    String periodType = String.valueOf(periodRows.get(0).get("periodType"));
    String awardCode = String.valueOf(body.get("awardCode"));
    List<Map<String, Object>> awardRows =
        jdbc.query(
            "SELECT scope, max_winners FROM award_type WHERE code = ?",
            new Object[] {awardCode},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("scope", rs.getString("scope"));
              m.put("maxWinners", rs.getObject("max_winners"));
              return m;
            });
    if (awardRows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "奖项类型不存在");
    }
    String scope = String.valueOf(awardRows.get(0).get("scope"));
    if (!awardScopeMatches(scope, periodType)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该奖项与当前周期类型不匹配");
    }
    Object maxWinners = awardRows.get(0).get("maxWinners");
    if (maxWinners != null) {
      Integer n =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM period_award WHERE period_id = ? AND award_code = ?",
              Integer.class,
              periodId,
              awardCode);
      if (n != null && n >= ((Number) maxWinners).intValue()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该奖项获奖人数已达上限");
      }
    }
    Object performanceRecordId = body.get("performanceRecordId");
    String employeeId = String.valueOf(body.get("employeeId"));
    if (performanceRecordId != null && !String.valueOf(performanceRecordId).isEmpty()) {
      List<String> recEmp =
          jdbc.query(
              "SELECT employee_id FROM performance_record WHERE id = ?",
              new Object[] {String.valueOf(performanceRecordId)},
              (rs, rn) -> rs.getString("employee_id"));
      if (recEmp.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "绩效记录不存在");
      }
      if (!employeeId.equals(recEmp.get(0))) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绩效记录与员工不匹配");
      }
    }
    String id = UUID.randomUUID().toString();
    try {
      jdbc.update(
          "INSERT INTO period_award (id, period_id, award_code, employee_id, performance_record_id, remark, created_by) VALUES (?,?,?,?,?,?,?)",
          id,
          periodId,
          awardCode,
          employeeId,
          performanceRecordId,
          body.get("remark"),
          userId);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该员工在此奖项下可能已存在记录");
    }
    Map<String, Object> list = listPeriodAwards(userId, periodId);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) list.get("items");
    for (Map<String, Object> item : items) {
      if (id.equals(item.get("id"))) {
        return item;
      }
    }
    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建获奖记录失败");
  }

  public void removePeriodAward(String userId, String awardRowId) {
    assertAllowed(userId);
    Integer count =
        jdbc.queryForObject("SELECT COUNT(*) FROM period_award WHERE id = ?", Integer.class, awardRowId);
    if (count == null || count == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "获奖记录不存在");
    }
    jdbc.update("DELETE FROM period_award WHERE id = ?", awardRowId);
  }
}
