package com.jixiao2.server.evaluation;

import com.jixiao2.server.menu.MenuPermissionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvaluationService {

  private static final Pattern YEAR_MONTH = Pattern.compile("^(\\d{4})-(\\d{1,2})$");
  private static final Pattern QUARTER = Pattern.compile("^\\d{4}-Q[1-4]$", Pattern.CASE_INSENSITIVE);

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

  /** 统一自然月 period 为 YYYY-MM，兼容库中 2027-7 等写法。 */
  private static String normalizeMonthPeriodKey(String raw) {
    if (raw == null) {
      return "";
    }
    String t = raw.trim();
    if (t.isEmpty()) {
      return "";
    }
    Matcher m = YEAR_MONTH.matcher(t);
    if (m.matches()) {
      int year = Integer.parseInt(m.group(1));
      int month = Integer.parseInt(m.group(2));
      if (month >= 1 && month <= 12) {
        return String.format("%04d-%02d", year, month);
      }
    }
    return t;
  }

  private static List<String> monthPeriodVariants(String keyNorm) {
    int[] ym = parseYearMonth(keyNorm);
    List<String> variants = new ArrayList<String>();
    variants.add(String.format("%04d-%02d", ym[0], ym[1]));
    variants.add(ym[0] + "-" + ym[1]);
    return variants;
  }

  private static void appendPeriodInClause(StringBuilder sql, List<Object> args, List<String> periodKeys) {
    if (periodKeys.isEmpty()) {
      return;
    }
    sql.append(" AND pr.period IN (");
    sql.append(String.join(",", Collections.nCopies(periodKeys.size(), "?")));
    sql.append(")");
    args.addAll(periodKeys);
  }

  private List<String> configuredMonthPeriodKeys() {
    return jdbc.query(
        "SELECT period_key FROM evaluation_period WHERE period_type = 'month' ORDER BY period_key ASC",
        (rs, rn) -> rs.getString("period_key"));
  }

  private List<String> expandMonthPeriodVariants(List<String> monthKeys) {
    Set<String> expanded = new LinkedHashSet<String>();
    for (String mk : monthKeys) {
      try {
        expanded.addAll(monthPeriodVariants(normalizePeriodKey("month", mk)));
      } catch (ResponseStatusException ignored) {
        String norm = normalizeMonthPeriodKey(mk);
        if (!norm.isEmpty()) {
          expanded.add(norm);
        }
      }
    }
    return new ArrayList<String>(expanded);
  }

  private static boolean isNormalizedMonthPeriod(String norm) {
    return YEAR_MONTH.matcher(norm == null ? "" : norm).matches();
  }

  /** 月度排行榜：仅保留自然月 period，并按选定月份精确过滤。 */
  private List<Map<String, Object>> filterMonthLeaderboardRows(
      List<Map<String, Object>> rows, boolean allKeys, String keyNorm) {
    String target = allKeys ? "" : normalizeMonthPeriodKey(keyNorm);
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> r : rows) {
      String norm = normalizeMonthPeriodKey(String.valueOf(r.get("period")));
      if (!isNormalizedMonthPeriod(norm)) {
        continue;
      }
      if (!allKeys && !norm.equals(target)) {
        continue;
      }
      Map<String, Object> copy = new LinkedHashMap<String, Object>(r);
      copy.put("period", norm);
      out.add(copy);
    }
    return out;
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

  private static List<String> monthKeysInQuarter(String quarterKey) {
    Matcher m = Pattern.compile("^(\\d{4})-Q([1-4])$").matcher(quarterKey);
    if (!m.matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "季度 key 须为 YYYY-Q1～Q4 格式");
    }
    int year = Integer.parseInt(m.group(1));
    int quarter = Integer.parseInt(m.group(2));
    int startMonth = (quarter - 1) * 3 + 1;
    List<String> keys = new ArrayList<String>();
    for (int i = 0; i < 3; i++) {
      keys.add(String.format("%04d-%02d", year, startMonth + i));
    }
    return keys;
  }

  private static String normalizePeriodKey(String periodType, String raw) {
    if ("month".equals(periodType)) {
      int[] ym = parseYearMonth(raw);
      return String.format("%04d-%02d", ym[0], ym[1]);
    }
    return parsePerformanceQuarterPeriod(raw);
  }

  private List<String> listMonthKeysForQuarter(String quarterKey) {
    List<String> configured =
        jdbc.query(
            "SELECT ep.period_key FROM evaluation_period ep INNER JOIN evaluation_period parent ON ep.parent_period_id = parent.id WHERE parent.period_key = ? AND ep.period_type = 'month' ORDER BY ep.period_key ASC",
            new Object[] {quarterKey},
            (rs, rn) -> rs.getString("period_key"));
    if (!configured.isEmpty() && configured.size() >= 3) {
      return configured;
    }
    return monthKeysInQuarter(quarterKey);
  }

  private static BigDecimal averageScore(List<BigDecimal> scores) {
    if (scores.isEmpty()) {
      return null;
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (BigDecimal score : scores) {
      sum = sum.add(score);
    }
    return sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
  }

  private Map<String, Map<String, Object>> aggregateQuarterLeaderboard(
      List<Map<String, Object>> rows, String quarterKey) {
    Map<String, Map<String, Map<String, Object>>> byEmployeeMonth =
        new LinkedHashMap<String, Map<String, Map<String, Object>>>();
    for (Map<String, Object> row : rows) {
      BigDecimal score = (BigDecimal) row.get("totalScore");
      if (score == null) {
        continue;
      }
      String employeeId = String.valueOf(row.get("employeeId"));
      String period = normalizeMonthPeriodKey(String.valueOf(row.get("period")));
      Map<String, Map<String, Object>> months =
          byEmployeeMonth.computeIfAbsent(employeeId, k -> new LinkedHashMap<String, Map<String, Object>>());
      Map<String, Object> prev = months.get(period);
      if (prev == null || score.compareTo((BigDecimal) prev.get("totalScore")) > 0) {
        months.put(period, row);
      }
    }
    Map<String, Map<String, Object>> bestByEmployee = new LinkedHashMap<String, Map<String, Object>>();
    for (Map.Entry<String, Map<String, Map<String, Object>>> entry : byEmployeeMonth.entrySet()) {
      List<BigDecimal> scores = new ArrayList<BigDecimal>();
      Map<String, Object> sample = null;
      for (Map<String, Object> monthRow : entry.getValue().values()) {
        scores.add((BigDecimal) monthRow.get("totalScore"));
        if (sample == null) {
          sample = monthRow;
        }
      }
      BigDecimal average = averageScore(scores);
      if (average == null || sample == null) {
        continue;
      }
      Map<String, Object> aggregated = new LinkedHashMap<String, Object>(sample);
      aggregated.put("totalScore", average);
      aggregated.put("period", quarterKey);
      aggregated.put("recordId", null);
      bestByEmployee.put(entry.getKey(), aggregated);
    }
    return bestByEmployee;
  }

  /** 季度「全部」：按每个配置季度分别汇总，一行对应一个员工在一个季度的均分。 */
  private List<Map<String, Object>> aggregateQuarterLeaderboardAllQuarters(List<Map<String, Object>> rows) {
    List<String> quarterKeys =
        jdbc.query(
            "SELECT period_key FROM evaluation_period WHERE period_type = 'quarter' ORDER BY period_key ASC",
            (rs, rn) -> rs.getString("period_key"));
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (String quarterKey : quarterKeys) {
      List<String> monthKeys = listMonthKeysForQuarter(quarterKey);
      Set<String> monthSet = new HashSet<String>();
      for (String mk : monthKeys) {
        monthSet.add(normalizeMonthPeriodKey(mk));
        try {
          monthSet.addAll(monthPeriodVariants(normalizePeriodKey("month", mk)));
        } catch (ResponseStatusException ignored) {
          /* keep normalized key only */
        }
      }
      List<Map<String, Object>> quarterRows = new ArrayList<Map<String, Object>>();
      for (Map<String, Object> row : rows) {
        String period = normalizeMonthPeriodKey(String.valueOf(row.get("period")));
        if (monthSet.contains(period)) {
          quarterRows.add(row);
        }
      }
      if (quarterRows.isEmpty()) {
        continue;
      }
      items.addAll(aggregateQuarterLeaderboard(quarterRows, quarterKey).values());
    }
    return items;
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

  private String quarterPeriodKeyById(String quarterId) {
    if (quarterId == null || quarterId.isEmpty()) {
      return null;
    }
    try {
      return jdbc.queryForObject(
          "SELECT period_key FROM evaluation_period WHERE id = ? AND period_type = 'quarter'",
          String.class,
          quarterId);
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所属季度不存在或类型不是季度");
    }
  }

  private void assertMonthBelongsToQuarterKey(String monthKey, String quarterKey) {
    if (quarterKey == null || quarterKey.isEmpty()) {
      return;
    }
    List<String> months = monthKeysInQuarter(quarterKey);
    if (!months.contains(monthKey)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "月度 "
              + monthKey
              + " 与所选季度 "
              + quarterKey
              + " 不匹配（该季度包含："
              + String.join("、", months)
              + "）");
    }
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
    String parentId = null;
    if ("month".equals(pt)) {
      Object ppo = body.get("parentPeriodId");
      if (ppo == null || String.valueOf(ppo).trim().isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月度周期须选择归属季度");
      }
      parentId = resolveMonthParentId(String.valueOf(ppo));
      String quarterKey = quarterPeriodKeyById(parentId);
      assertMonthBelongsToQuarterKey(periodKey, quarterKey);
    }
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
    if ("month".equals(periodType)) {
      String parentForValidation = null;
      if (body.containsKey("parentPeriodId")) {
        String raw = String.valueOf(body.get("parentPeriodId")).trim();
        if (raw.isEmpty()) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月度周期须选择归属季度");
        }
        parentForValidation = resolveMonthParentId(String.valueOf(body.get("parentPeriodId")));
      } else {
        Object existingParent = existing.get("parentPeriodId");
        if (existingParent != null && !String.valueOf(existingParent).trim().isEmpty()) {
          parentForValidation = String.valueOf(existingParent).trim();
        }
      }
      if (parentForValidation != null) {
        assertMonthBelongsToQuarterKey(periodKey, quarterPeriodKeyById(parentForValidation));
      }
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
            "SELECT period FROM performance_record WHERE deleted_at IS NULL GROUP BY period ORDER BY period DESC",
            (rs, rn) -> rs.getString("period"));
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  private static Map<String, Object> toLeaderboardItem(Map<String, Object> r, int rank) {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("rank", rank);
    item.put("employeeId", r.get("employeeId"));
    String name =
        r.get("employeeName") == null ? String.valueOf(r.get("employeeId")) : String.valueOf(r.get("employeeName"));
    item.put("employeeName", name);
    item.put("departmentId", r.get("departmentId"));
    item.put("departmentName", r.get("departmentName"));
    item.put("totalScore", r.get("totalScore"));
    item.put("performancePeriod", r.get("period"));
    item.put("recordId", r.get("recordId"));
    return item;
  }

  public Map<String, Object> getLeaderboard(
      String userId, String scope, String key, List<String> departmentIds) {
    assertAllowed(userId);
    if (!"month".equals(scope) && !"quarter".equals(scope)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope 须为 month 或 quarter");
    }
    boolean allKeys = key == null || key.trim().isEmpty();
    String keyNorm = allKeys ? "" : normalizePeriodKey(scope, key);
    StringBuilder sql =
        new StringBuilder(
            "SELECT pr.id AS record_id, pr.employee_id, pr.total_score, pr.period, eh.name AS employee_name, eh.department_id, eh.department_name "
                + "FROM performance_record pr INNER JOIN employee_hierarchy eh ON pr.employee_id = eh.employee_id "
                + "WHERE pr.deleted_at IS NULL AND pr.status = 'completed' AND pr.total_score IS NOT NULL");
    List<Object> args = new ArrayList<Object>();
    if (!allKeys) {
      if ("month".equals(scope)) {
        appendPeriodInClause(sql, args, monthPeriodVariants(keyNorm));
      } else {
        List<String> monthKeys = listMonthKeysForQuarter(keyNorm);
        appendPeriodInClause(sql, args, expandMonthPeriodVariants(monthKeys));
      }
    } else if ("month".equals(scope)) {
      sql.append(" AND pr.period REGEXP ?");
      args.add("^[0-9]{4}-[0-9]{1,2}$");
    } else if ("quarter".equals(scope)) {
      List<String> monthKeys = configuredMonthPeriodKeys();
      appendPeriodInClause(sql, args, expandMonthPeriodVariants(monthKeys));
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
    if ("month".equals(scope)) {
      rows = filterMonthLeaderboardRows(rows, allKeys, keyNorm);
    }
    List<Map<String, Object>> sorted;
    if ("quarter".equals(scope)) {
      if (allKeys) {
        sorted = aggregateQuarterLeaderboardAllQuarters(rows);
      } else {
        sorted = new ArrayList<Map<String, Object>>(aggregateQuarterLeaderboard(rows, keyNorm).values());
      }
    } else {
      sorted = new ArrayList<Map<String, Object>>(rows);
    }
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    if ("month".equals(scope) && allKeys) {
      Map<String, List<Map<String, Object>>> byPeriod = new LinkedHashMap<String, List<Map<String, Object>>>();
      for (Map<String, Object> r : sorted) {
        String pk = normalizeMonthPeriodKey(String.valueOf(r.get("period")));
        byPeriod.computeIfAbsent(pk, k -> new ArrayList<Map<String, Object>>()).add(r);
      }
      List<String> periodOrder = new ArrayList<String>(byPeriod.keySet());
      Collections.sort(periodOrder, Collections.reverseOrder());
      for (String pk : periodOrder) {
        List<Map<String, Object>> group = byPeriod.get(pk);
        Collections.sort(
            group,
            (a, b) -> ((BigDecimal) b.get("totalScore")).compareTo((BigDecimal) a.get("totalScore")));
        for (int i = 0; i < group.size(); i++) {
          items.add(toLeaderboardItem(group.get(i), i + 1));
        }
      }
    } else {
      Collections.sort(
          sorted,
          (a, b) -> ((BigDecimal) b.get("totalScore")).compareTo((BigDecimal) a.get("totalScore")));
      for (int i = 0; i < sorted.size(); i++) {
        items.add(toLeaderboardItem(sorted.get(i), i + 1));
      }
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("scope", scope);
    out.put("key", keyNorm);
    return out;
  }

  public Map<String, Object> getLeaderboardQuarterDetail(String userId, String key, String employeeId) {
    assertAllowed(userId);
    if (employeeId == null || employeeId.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请指定员工");
    }
    String keyNorm = normalizePeriodKey("quarter", key);
    String eid = employeeId.trim();

    List<String> names =
        jdbc.query(
            "SELECT name FROM employee_hierarchy WHERE employee_id = ? LIMIT 1",
            new Object[] {eid},
            (rs, rn) -> rs.getString("name"));
    String employeeName = names.isEmpty() ? eid : names.get(0);

    List<Map<String, Object>> monthDefs =
        jdbc.query(
            "SELECT ep.period_key, ep.name FROM evaluation_period ep INNER JOIN evaluation_period parent ON ep.parent_period_id = parent.id WHERE parent.period_key = ? AND ep.period_type = 'month' ORDER BY ep.period_key ASC",
            new Object[] {keyNorm},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("periodKey", rs.getString("period_key"));
              m.put("periodName", rs.getString("name"));
              return m;
            });

    List<String> monthKeys = new ArrayList<String>();
    Map<String, String> monthNames = new LinkedHashMap<String, String>();
    for (Map<String, Object> def : monthDefs) {
      String mk = String.valueOf(def.get("periodKey"));
      monthKeys.add(mk);
      monthNames.put(mk, String.valueOf(def.get("periodName")));
    }
    if (monthKeys.isEmpty()) {
      for (String mk : monthKeysInQuarter(keyNorm)) {
        monthKeys.add(mk);
        monthNames.put(mk, mk);
      }
    }

    List<Map<String, Object>> monthlyItems = new ArrayList<Map<String, Object>>();
    for (String mk : monthKeys) {
      List<String> periodVariants;
      try {
        periodVariants = monthPeriodVariants(normalizePeriodKey("month", mk));
      } catch (ResponseStatusException ex) {
        periodVariants = Collections.singletonList(normalizeMonthPeriodKey(mk));
      }
      StringBuilder monthSql =
          new StringBuilder(
              "SELECT id, total_score, status FROM performance_record WHERE employee_id = ? AND deleted_at IS NULL AND period IN (");
      monthSql.append(String.join(",", Collections.nCopies(periodVariants.size(), "?")));
      monthSql.append(") ORDER BY _updated_at DESC LIMIT 1");
      List<Object> monthArgs = new ArrayList<Object>();
      monthArgs.add(eid);
      monthArgs.addAll(periodVariants);
      List<Map<String, Object>> monthRows =
          jdbc.query(
              monthSql.toString(),
              monthArgs.toArray(),
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("recordId", rs.getString("id"));
                m.put("totalScore", rs.getBigDecimal("total_score"));
                m.put("status", rs.getString("status"));
                return m;
              });
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("periodKey", mk);
      item.put("periodName", monthNames.get(mk));
      if (!monthRows.isEmpty()) {
        Map<String, Object> row = monthRows.get(0);
        item.put("recordId", row.get("recordId"));
        item.put("totalScore", row.get("totalScore"));
        item.put("status", row.get("status"));
      }
      monthlyItems.add(item);
    }

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("quarterKey", keyNorm);
    out.put("employeeId", eid);
    out.put("employeeName", employeeName);
    List<BigDecimal> monthScores = new ArrayList<BigDecimal>();
    boolean allCompleted = !monthlyItems.isEmpty();
    for (Map<String, Object> item : monthlyItems) {
      BigDecimal score = (BigDecimal) item.get("totalScore");
      if (score != null) {
        monthScores.add(score);
      }
      if (!"completed".equals(String.valueOf(item.get("status")))) {
        allCompleted = false;
      }
    }
    BigDecimal quarterTotalScore = averageScore(monthScores);
    if (quarterTotalScore != null) {
      out.put("quarterTotalScore", quarterTotalScore);
      if (allCompleted && monthScores.size() == monthlyItems.size()) {
        out.put("quarterStatus", "completed");
      }
    }
    out.put("monthlyItems", monthlyItems);
    return out;
  }

  public Map<String, Object> listPeriodAwards(String userId, String periodId, String periodType) {
    assertAllowed(userId);
    boolean allPeriods = periodId == null || periodId.trim().isEmpty();
    if (!allPeriods) {
      String pid = periodId.trim();
      Integer count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM evaluation_period WHERE id = ?", Integer.class, pid);
      if (count == null || count == 0) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评选周期不存在");
      }
      List<Map<String, Object>> items =
          jdbc.query(
              "SELECT pa.id, pa.period_id, ep.period_key, ep.name AS period_name, pa.award_code, at.name AS award_name, pa.employee_id, eh.name AS employee_name, pa.performance_record_id, pa.remark, pa.created_by, pa._created_at "
                  + "FROM period_award pa INNER JOIN evaluation_period ep ON pa.period_id = ep.id INNER JOIN award_type at ON pa.award_code = at.code LEFT JOIN employee_hierarchy eh ON pa.employee_id = eh.employee_id "
                  + "WHERE pa.period_id = ? ORDER BY at.sort_order ASC, pa._created_at ASC",
              new Object[] {pid},
              this::mapPeriodAwardRow);
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("items", items);
      return out;
    }
    String pt = periodType == null ? "" : periodType.trim();
    if (!"month".equals(pt) && !"quarter".equals(pt)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodType 须为 month 或 quarter");
    }
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT pa.id, pa.period_id, ep.period_key, ep.name AS period_name, pa.award_code, at.name AS award_name, pa.employee_id, eh.name AS employee_name, pa.performance_record_id, pa.remark, pa.created_by, pa._created_at "
                + "FROM period_award pa INNER JOIN evaluation_period ep ON pa.period_id = ep.id INNER JOIN award_type at ON pa.award_code = at.code LEFT JOIN employee_hierarchy eh ON pa.employee_id = eh.employee_id "
                + "WHERE ep.period_type = ? ORDER BY ep.period_key ASC, at.sort_order ASC, pa._created_at ASC",
            new Object[] {pt},
            this::mapPeriodAwardRow);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  private Map<String, Object> mapPeriodAwardRow(java.sql.ResultSet rs, int rn) throws java.sql.SQLException {
    Map<String, Object> m = new LinkedHashMap<String, Object>();
    m.put("id", rs.getString("id"));
    m.put("periodId", rs.getString("period_id"));
    m.put("periodKey", rs.getString("period_key"));
    m.put("periodName", rs.getString("period_name"));
    m.put("awardCode", rs.getString("award_code"));
    m.put("awardName", rs.getString("award_name"));
    m.put("employeeId", rs.getString("employee_id"));
    m.put("employeeName", rs.getString("employee_name"));
    m.put("performanceRecordId", rs.getString("performance_record_id"));
    m.put("remark", rs.getString("remark"));
    m.put("createdBy", rs.getString("created_by"));
    m.put("createdAt", toIso(rs.getTimestamp("_created_at")));
    return m;
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
              "SELECT employee_id FROM performance_record WHERE id = ? AND deleted_at IS NULL",
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
    Map<String, Object> list = listPeriodAwards(userId, periodId, null);
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
