package com.jixiao2.server.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiao2.server.menu.MenuPermissionService;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PerformanceService {

  private static final Logger log = LoggerFactory.getLogger(PerformanceService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<Map<String, Object>>> REVIEW_LIST_TYPE =
      new TypeReference<List<Map<String, Object>>>() {};

  private final JdbcTemplate jdbc;
  private final MenuPermissionService menuPermissionService;

  public PerformanceService(JdbcTemplate jdbc, MenuPermissionService menuPermissionService) {
    this.jdbc = jdbc;
    this.menuPermissionService = menuPermissionService;
  }

  public String getUserRole(String userId) {
    if (userId == null || userId.isEmpty()) {
      return "employee";
    }
    try {
      return menuPermissionService.getUserRole(userId);
    } catch (Exception e) {
      log.error("获取用户角色失败", e);
      return "employee";
    }
  }

  private List<String> resolveRolesForUser(String userId) {
    if (userId == null || userId.isEmpty()) {
      return Collections.emptyList();
    }
    try {
      List<String> keys = menuPermissionService.getRoleKeysForUser(userId);
      return keys.isEmpty() ? Collections.singletonList("employee") : keys;
    } catch (Exception e) {
      log.error("获取用户角色失败", e);
      return Collections.emptyList();
    }
  }

  private static class MenuFlags {
    final boolean listAll;
    final boolean batchCreate;
    final boolean reviewAdmin;
    final boolean exportData;

    MenuFlags(boolean listAll, boolean batchCreate, boolean reviewAdmin, boolean exportData) {
      this.listAll = listAll;
      this.batchCreate = batchCreate;
      this.reviewAdmin = reviewAdmin;
      this.exportData = exportData;
    }
  }

  private MenuFlags performanceMenuFlags(String userId) {
    MenuPermissionService.MenuPermissionsMe perm =
        menuPermissionService.getEffectiveMenusForUser(userId);
    Map<String, Boolean> menus = perm.getMenus();
    return new MenuFlags(
        Boolean.TRUE.equals(menus.get("performance_list_all")),
        Boolean.TRUE.equals(menus.get("performance_batch_create")),
        Boolean.TRUE.equals(menus.get("performance_review_admin")),
        Boolean.TRUE.equals(menus.get("performance_export")));
  }

  private Map<String, Double> getReviewWeights() {
    try {
      List<Map<String, Object>> rows =
          jdbc.query(
              "SELECT config_key, config_value FROM system_config WHERE config_key IN (?, ?)",
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("configKey", rs.getString("config_key"));
                m.put("configValue", rs.getString("config_value"));
                return m;
              },
              "manager_review_weight",
              "dotted_manager_review_weight");
      Double managerWeight = null;
      Double dottedWeight = null;
      for (Map<String, Object> r : rows) {
        try {
          double v = Double.parseDouble(String.valueOf(r.get("configValue")));
          if (Double.isFinite(v)) {
            if ("manager_review_weight".equals(r.get("configKey"))) {
              managerWeight = v;
            } else if ("dotted_manager_review_weight".equals(r.get("configKey"))) {
              dottedWeight = v;
            }
          }
        } catch (NumberFormatException ignored) {
          // skip
        }
      }
      Map<String, Double> out = new HashMap<String, Double>();
      if (managerWeight != null) {
        out.put("managerWeight", managerWeight);
      }
      if (dottedWeight != null) {
        out.put("dottedWeight", dottedWeight);
      }
      return out;
    } catch (Exception e) {
      return Collections.emptyMap();
    }
  }

  private double calcWeightedScore(List<Map<String, Object>> review, List<Map<String, Object>> indicators) {
    if (review == null || review.isEmpty()) {
      return 0;
    }
    Map<String, Double> weightMap = new HashMap<String, Double>();
    if (indicators != null) {
      for (Map<String, Object> ind : indicators) {
        Object w = ind.get("weight");
        double weight = w instanceof Number ? ((Number) w).doubleValue() : 0;
        weightMap.put(String.valueOf(ind.get("name")), weight);
      }
    }
    double totalWeightedScore = 0;
    double totalWeight = 0;
    for (Map<String, Object> item : review) {
      String name = String.valueOf(item.get("indicatorName"));
      double w = weightMap.containsKey(name) ? weightMap.get(name) : 0;
      double score = scoreOf(item);
      totalWeightedScore += score * w;
      totalWeight += w;
    }
    if (totalWeight > 0) {
      return totalWeightedScore / totalWeight;
    }
    double sum = 0;
    for (Map<String, Object> item : review) {
      sum += scoreOf(item);
    }
    return sum / review.size();
  }

  private static double scoreOf(Map<String, Object> item) {
    Object s = item.get("score");
    if (s instanceof Number) {
      return ((Number) s).doubleValue();
    }
    return 0;
  }

  private List<Map<String, Object>> getTemplateIndicators(String templateId) {
    if (templateId == null || templateId.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> jsonRows =
        jdbc.query(
            "SELECT indicators FROM performance_template WHERE id = ? LIMIT 1",
            (rs, rn) -> rs.getString("indicators"),
            templateId);
    if (jsonRows.isEmpty() || jsonRows.get(0) == null) {
      return Collections.emptyList();
    }
    try {
      return MAPPER.readValue(jsonRows.get(0), REVIEW_LIST_TYPE);
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  private double computeTotalScore(
      String templateId,
      List<Map<String, Object>> managerReview,
      List<Map<String, Object>> dottedManagerReview,
      boolean hasDottedManager)
      throws Exception {
    List<Map<String, Object>> indicators = getTemplateIndicators(templateId);
    double mScore = managerReview != null ? calcWeightedScore(managerReview, indicators) : 0;
    if (!hasDottedManager || dottedManagerReview == null) {
      return round2(mScore);
    }
    double dScore = calcWeightedScore(dottedManagerReview, indicators);
    Map<String, Double> weights = getReviewWeights();
    double mW;
    double dW;
    if (weights.containsKey("managerWeight") && weights.containsKey("dottedWeight")) {
      mW = weights.get("managerWeight");
      dW = weights.get("dottedWeight");
    } else {
      mW = 0.5;
      dW = 0.5;
    }
    return round2(mScore * mW + dScore * dW);
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  private boolean isReviewComplete(List<Map<String, Object>> review, List<String> indicatorNames) {
    if (review == null || review.isEmpty() || indicatorNames.isEmpty()) {
      return false;
    }
    Map<String, Map<String, Object>> byName = new HashMap<String, Map<String, Object>>();
    for (Map<String, Object> i : review) {
      byName.put(String.valueOf(i.get("indicatorName")), i);
    }
    for (String name : indicatorNames) {
      Map<String, Object> item = byName.get(name);
      if (item == null) {
        return false;
      }
      Object s = item.get("score");
      if (!(s instanceof Number) || Double.isNaN(((Number) s).doubleValue())) {
        return false;
      }
    }
    return true;
  }

  private static double[] normalizeRoleWeights(Double managerWeight, Double dottedWeight) {
    if (managerWeight != null && dottedWeight != null) {
      return new double[] {managerWeight, dottedWeight};
    }
    return new double[] {0.5, 0.5};
  }

  private static String trimOrEmpty(String s) {
    return s == null ? "" : s.trim();
  }

  private static String iso(Timestamp ts) {
    return ts == null ? null : ts.toInstant().toString();
  }

  private static Double toDouble(BigDecimal bd) {
    return bd == null ? null : bd.doubleValue();
  }

  private List<Map<String, Object>> parseReviewJson(String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.readValue(json, REVIEW_LIST_TYPE);
    } catch (Exception e) {
      return null;
    }
  }

  private String writeJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 序列化失败");
    }
  }

  private static class WhereClause {
    final String sql;
    final List<Object> args;

    WhereClause(String sql, List<Object> args) {
      this.sql = sql;
      this.args = args;
    }
  }

  private WhereClause buildListWhere(
      String userId,
      String status,
      String focus,
      String period,
      String departmentId,
      String employeeName,
      boolean listAll,
      String empAlias) {
    List<String> parts = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();

    if ("need_score".equals(focus)) {
      parts.add(
          "((pr.status=? AND pr.manager_id=?) OR (pr.status=? AND (pr.manager_id=? OR pr.dotted_manager_id=?)) OR (pr.status=? AND pr.dotted_manager_id=?))");
      args.add("manager_review");
      args.add(userId);
      args.add("dual_manager_review");
      args.add(userId);
      args.add(userId);
      args.add("dotted_manager_review");
      args.add(userId);
    } else if ("need_approve_goal".equals(focus)) {
      parts.add("(pr.status=? AND (pr.manager_id=? OR pr.dotted_manager_id=?))");
      args.add("goal_pending_review");
      args.add(userId);
      args.add(userId);
    } else if (status != null && !status.isEmpty()) {
      parts.add("pr.status=?");
      args.add(status);
    }
    if (period != null && !period.isEmpty()) {
      parts.add("pr.period=?");
      args.add(period);
    }
    if (!listAll) {
      parts.add("(pr.employee_id=? OR pr.manager_id=? OR pr.dotted_manager_id=?)");
      args.add(userId);
      args.add(userId);
      args.add(userId);
    }
    String deptId = departmentId == null ? "" : departmentId.trim();
    if (!deptId.isEmpty() && listAll) {
      parts.add(empAlias + ".department_id=?");
      args.add(deptId);
    }
    String nameQ = employeeName == null ? "" : employeeName.trim();
    if (!nameQ.isEmpty()) {
      String safe = "%" + nameQ.replace("%", "").replace("_", "") + "%";
      if (safe.length() > 2) {
        parts.add(empAlias + ".name LIKE ?");
        args.add(safe);
      }
    }
    String sql = parts.isEmpty() ? "1=1" : String.join(" AND ", parts);
    return new WhereClause(sql, args);
  }

  public Map<String, Object> list(
      String userId,
      String status,
      String focus,
      String period,
      String departmentId,
      String employeeName,
      int page,
      int pageSize) {
    Map<String, Object> empty = new LinkedHashMap<String, Object>();
    empty.put("items", Collections.emptyList());
    empty.put("total", 0);
    empty.put("page", page);
    empty.put("pageSize", pageSize);
    empty.put("canBatchCreate", false);
    empty.put("canExport", false);
    if (userId == null || userId.isEmpty()) {
      return empty;
    }
    List<String> userRoles = resolveRolesForUser(userId);
    MenuFlags flags = performanceMenuFlags(userId);
    WhereClause wc = buildListWhere(userId, status, focus, period, departmentId, employeeName, flags.listAll, "emp");
    int offset = (page - 1) * pageSize;

    String from =
        " FROM performance_record pr"
            + " LEFT JOIN employee_hierarchy emp ON emp.employee_id = pr.employee_id"
            + " LEFT JOIN employee_hierarchy mgr ON mgr.employee_id = pr.manager_id"
            + " WHERE "
            + wc.sql;

    List<Object> listArgs = new ArrayList<Object>(wc.args);
    listArgs.add(pageSize);
    listArgs.add(offset);

    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT pr.id, pr.employee_id, pr.period, pr.status, pr.manager_id, pr.total_score,"
                + " pr._created_at, pr._updated_at, emp.name AS employee_name, mgr.name AS manager_name"
                + from
                + " ORDER BY pr._created_at DESC LIMIT ? OFFSET ?",
            (rs, rn) -> mapListItem(rs),
            listArgs.toArray());

    Integer total =
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT pr.id)" + from, Integer.class, wc.args.toArray());

    log.info(
        "绩效列表查询: userId={}, roles={}, total={}", userId, userRoles, total);

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("total", total == null ? 0 : total);
    out.put("page", page);
    out.put("pageSize", pageSize);
    out.put("canBatchCreate", flags.batchCreate);
    out.put("canExport", flags.exportData);
    return out;
  }

  private Map<String, Object> mapListItem(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("id", rs.getString("id"));
    item.put("employeeId", rs.getString("employee_id"));
    item.put("employeeName", trimOrEmpty(rs.getString("employee_name")));
    item.put("period", rs.getString("period"));
    item.put("status", rs.getString("status"));
    item.put("managerId", rs.getString("manager_id"));
    item.put("managerName", trimOrEmpty(rs.getString("manager_name")));
    item.put("totalScore", toDouble(rs.getBigDecimal("total_score")));
    item.put("createdAt", iso(rs.getTimestamp("_created_at")));
    item.put("updatedAt", iso(rs.getTimestamp("_updated_at")));
    return item;
  }

  public Map<String, Object> listSupervisorCalibrationQueue(
      String userId,
      String period,
      String departmentId,
      String employeeName,
      int page,
      int pageSize) {
    Map<String, Object> empty = new LinkedHashMap<String, Object>();
    empty.put("items", Collections.emptyList());
    empty.put("total", 0);
    empty.put("page", page);
    empty.put("pageSize", pageSize);
    if (userId == null || userId.isEmpty()) {
      return empty;
    }
    menuPermissionService.assertSuperAdmin(
        menuPermissionService.getUserRole(userId), "仅超级管理员可查看绩效校准（上级评分）队列");

    List<String> parts = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();
    parts.add("(pr.status=? OR pr.status=? OR pr.status=?)");
    args.add("manager_review");
    args.add("dual_manager_review");
    args.add("dotted_manager_review");
    if (period != null && !period.trim().isEmpty()) {
      parts.add("pr.period=?");
      args.add(period.trim());
    }
    String deptId = departmentId == null ? "" : departmentId.trim();
    if (!deptId.isEmpty()) {
      parts.add("emp.department_id=?");
      args.add(deptId);
    }
    String nameQ = employeeName == null ? "" : employeeName.trim();
    if (!nameQ.isEmpty()) {
      String safe = "%" + nameQ.replace("%", "").replace("_", "") + "%";
      if (safe.length() > 2) {
        parts.add("emp.name LIKE ?");
        args.add(safe);
      }
    }
    String where = String.join(" AND ", parts);
    int offset = (page - 1) * pageSize;
    String from =
        " FROM performance_record pr"
            + " LEFT JOIN employee_hierarchy emp ON emp.employee_id = pr.employee_id"
            + " LEFT JOIN employee_hierarchy mgr ON mgr.employee_id = pr.manager_id"
            + " LEFT JOIN employee_hierarchy dot ON dot.employee_id = pr.dotted_manager_id"
            + " WHERE "
            + where;

    List<Object> listArgs = new ArrayList<Object>(args);
    listArgs.add(pageSize);
    listArgs.add(offset);

    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT pr.id, pr.employee_id, pr.period, pr.status, pr.manager_id, pr.dotted_manager_id,"
                + " pr.total_score, pr._created_at, pr._updated_at, emp.name AS employee_name,"
                + " mgr.name AS manager_name, dot.name AS dotted_manager_name"
                + from
                + " ORDER BY pr._updated_at DESC LIMIT ? OFFSET ?",
            (rs, rn) -> {
              Map<String, Object> item = mapListItem(rs);
              String dottedId = rs.getString("dotted_manager_id");
              item.put("dottedManagerId", dottedId);
              if (dottedId != null && !dottedId.isEmpty()) {
                item.put("dottedManagerName", trimOrEmpty(rs.getString("dotted_manager_name")));
              }
              return item;
            },
            listArgs.toArray());

    Integer total = jdbc.queryForObject("SELECT COUNT(DISTINCT pr.id)" + from, Integer.class, args.toArray());

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("total", total == null ? 0 : total);
    out.put("page", page);
    out.put("pageSize", pageSize);
    return out;
  }

  public Map<String, Object> exportData(
      String userId,
      String status,
      String focus,
      String period,
      String departmentId,
      String employeeName) {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", Collections.emptyList());
    if (userId == null || userId.isEmpty()) {
      return out;
    }
    menuPermissionService.assertMenuAllowed(userId, "performance_export");
    MenuFlags flags = performanceMenuFlags(userId);
    WhereClause wc =
        buildListWhere(userId, status, focus, period, departmentId, employeeName, flags.listAll, "emp");

    String sql =
        "SELECT pr.employee_id, pr.period, pr.status, pr.total_score, pr.self_review, pr.manager_review,"
            + " pr.dotted_manager_review, pr._updated_at, emp.name AS employee_name, emp.department_name"
            + " FROM performance_record pr"
            + " LEFT JOIN employee_hierarchy emp ON emp.employee_id = pr.employee_id"
            + " WHERE "
            + wc.sql
            + " ORDER BY pr._created_at DESC";

    List<Map<String, Object>> items =
        jdbc.query(
            sql,
            (rs, rn) -> {
              Map<String, Object> row = new LinkedHashMap<String, Object>();
              row.put("employeeName", trimOrEmpty(rs.getString("employee_name")));
              row.put("department", trimOrEmpty(rs.getString("department_name")));
              row.put("period", rs.getString("period"));
              row.put("status", rs.getString("status"));
              BigDecimal ts = rs.getBigDecimal("total_score");
              row.put("totalScore", ts == null ? 0 : ts.doubleValue());
              row.put("selfReviewComment", joinComments(rs.getString("self_review")));
              row.put("managerReviewComment", joinComments(rs.getString("manager_review")));
              row.put("dottedManagerReviewComment", joinComments(rs.getString("dotted_manager_review")));
              row.put("updatedAt", iso(rs.getTimestamp("_updated_at")));
              return row;
            },
            wc.args.toArray());

    log.info("绩效数据导出: userId={}, count={}", userId, items.size());
    out.put("items", items);
    return out;
  }

  private String joinComments(String json) {
    List<Map<String, Object>> list = parseReviewJson(json);
    if (list == null || list.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> r : list) {
      if (sb.length() > 0) {
        sb.append("; ");
      }
      Object c = r.get("comment");
      sb.append(c == null ? "" : String.valueOf(c));
    }
    return sb.toString();
  }

  private Map<String, Object> loadRecord(String id) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT * FROM performance_record WHERE id = ? LIMIT 1",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("employeeId", rs.getString("employee_id"));
              m.put("templateId", rs.getString("template_id"));
              m.put("period", rs.getString("period"));
              m.put("status", rs.getString("status"));
              m.put("managerId", rs.getString("manager_id"));
              m.put("dottedManagerId", rs.getString("dotted_manager_id"));
              m.put("goalSetting", rs.getString("goal_setting"));
              m.put("goalApprovedBy", rs.getString("goal_approved_by"));
              m.put("personalSummary", rs.getString("personal_summary"));
              m.put("selfReview", rs.getString("self_review"));
              m.put("managerReview", rs.getString("manager_review"));
              m.put("dottedManagerReview", rs.getString("dotted_manager_review"));
              m.put("totalScore", rs.getBigDecimal("total_score"));
              m.put("rejectionReason", rs.getString("rejection_reason"));
              m.put("finalReviewerId", rs.getString("final_reviewer_id"));
              m.put("finalReviewedAt", rs.getTimestamp("final_reviewed_at"));
              m.put("createdAt", rs.getTimestamp("_created_at"));
              m.put("updatedAt", rs.getTimestamp("_updated_at"));
              return m;
            },
            id);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "绩效记录不存在");
    }
    return rows.get(0);
  }

  public Map<String, Object> getDetail(String userId, String id) {
    Map<String, Object> r = loadRecord(id);
    MenuFlags flags = performanceMenuFlags(userId);
    String employeeId = String.valueOf(r.get("employeeId"));
    String managerId = String.valueOf(r.get("managerId"));
    String dottedManagerId = r.get("dottedManagerId") == null ? null : String.valueOf(r.get("dottedManagerId"));
    boolean isEmployee = employeeId.equals(userId);
    boolean isManager = managerId.equals(userId);
    boolean isDottedManager = dottedManagerId != null && dottedManagerId.equals(userId);
    if (!flags.listAll && !isEmployee && !isManager && !isDottedManager) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该绩效记录");
    }

    String templateId = r.get("templateId") == null ? null : String.valueOf(r.get("templateId"));
    String templateName = "";
    List<Map<String, Object>> indicators = Collections.emptyList();
    if (templateId != null && !templateId.isEmpty()) {
      List<Map<String, Object>> tplRows =
          jdbc.query(
              "SELECT name, indicators FROM performance_template WHERE id = ? LIMIT 1",
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", rs.getString("name"));
                m.put("indicators", rs.getString("indicators"));
                return m;
              },
              templateId);
      if (!tplRows.isEmpty()) {
        templateName = trimOrEmpty((String) tplRows.get(0).get("name"));
        try {
          String indJson = (String) tplRows.get(0).get("indicators");
          if (indJson != null) {
            indicators = MAPPER.readValue(indJson, REVIEW_LIST_TYPE);
          }
        } catch (Exception ignored) {
          indicators = Collections.emptyList();
        }
      }
    }

    boolean isCompleted = "completed".equals(r.get("status"));
    boolean hideReviewData = isEmployee && !flags.listAll && !isCompleted;

    Set<String> nameIds = new HashSet<String>();
    nameIds.add(employeeId);
    nameIds.add(managerId);
    if (dottedManagerId != null) {
      nameIds.add(dottedManagerId);
    }
    Map<String, String> nameById = loadEmployeeNames(nameIds);

    List<String> indicatorNames = new ArrayList<String>();
    for (Map<String, Object> ind : indicators) {
      indicatorNames.add(String.valueOf(ind.get("name")));
    }

    String detailViewerRole = menuPermissionService.getUserRole(userId);
    boolean isSuperAdminViewer = "super_admin".equals(detailViewerRole);
    boolean showReviewSynthesis =
        !hideReviewData
            && !indicatorNames.isEmpty()
            && (isManager || isDottedManager || flags.listAll || isSuperAdminViewer)
            && (dottedManagerId != null || isSuperAdminViewer);

    List<Map<String, Object>> mgrRevForTotals = parseReviewJson((String) r.get("managerReview"));
    List<Map<String, Object>> dotRevForTotals = parseReviewJson((String) r.get("dottedManagerReview"));
    Double managerWeightedTotal = null;
    Double dottedManagerWeightedTotal = null;
    if (!indicatorNames.isEmpty()) {
      if (isReviewComplete(mgrRevForTotals, indicatorNames)) {
        managerWeightedTotal = round2(calcWeightedScore(mgrRevForTotals, indicators));
      }
      if (dottedManagerId != null && isReviewComplete(dotRevForTotals, indicatorNames)) {
        dottedManagerWeightedTotal = round2(calcWeightedScore(dotRevForTotals, indicators));
      }
    }

    Map<String, Object> reviewRoleWeights = null;
    List<Map<String, Object>> reviewMergedIndicators = null;
    Double reviewMergedTotal = null;
    if (showReviewSynthesis) {
      Map<String, Double> weights = getReviewWeights();
      double[] norm =
          normalizeRoleWeights(weights.get("managerWeight"), weights.get("dottedWeight"));
      reviewRoleWeights = new LinkedHashMap<String, Object>();
      reviewRoleWeights.put("managerWeight", norm[0]);
      reviewRoleWeights.put("dottedWeight", norm[1]);

      Map<String, Double> mMap = new HashMap<String, Double>();
      if (mgrRevForTotals != null) {
        for (Map<String, Object> x : mgrRevForTotals) {
          mMap.put(String.valueOf(x.get("indicatorName")), scoreOf(x));
        }
      }
      Map<String, Double> dMap = new HashMap<String, Double>();
      if (dotRevForTotals != null) {
        for (Map<String, Object> x : dotRevForTotals) {
          dMap.put(String.valueOf(x.get("indicatorName")), scoreOf(x));
        }
      }
      reviewMergedIndicators = new ArrayList<Map<String, Object>>();
      for (String name : indicatorNames) {
        Double ms = mMap.get(name);
        Double ds = dMap.get(name);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("indicatorName", name);
        if (ms != null) {
          row.put("managerScore", ms);
        }
        if (ds != null) {
          row.put("dottedScore", ds);
        }
        if (ms != null && ds != null) {
          row.put("mergedScore", round2(norm[0] * ms + norm[1] * ds));
        }
        reviewMergedIndicators.add(row);
      }
      if (isReviewComplete(mgrRevForTotals, indicatorNames)
          && isReviewComplete(dotRevForTotals, indicatorNames)) {
        try {
          reviewMergedTotal =
              computeTotalScore(templateId, mgrRevForTotals, dotRevForTotals, true);
        } catch (Exception e) {
          reviewMergedTotal = null;
        }
      }
    }

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("id", r.get("id"));
    out.put("employeeId", employeeId);
    out.put("employeeName", nameById.get(employeeId));
    if (templateId != null) {
      out.put("templateId", templateId);
    }
    out.put("templateName", templateName);
    out.put("period", r.get("period"));
    out.put("status", r.get("status"));
    out.put("managerId", managerId);
    out.put("managerName", nameById.get(managerId));
    if (dottedManagerId != null) {
      out.put("dottedManagerId", dottedManagerId);
      out.put("dottedManagerName", nameById.get(dottedManagerId));
    }
    out.put("goalSetting", parseReviewJson((String) r.get("goalSetting")));
    Object goalApprovedBy = r.get("goalApprovedBy");
    if (goalApprovedBy != null) {
      out.put("goalApprovedBy", goalApprovedBy);
    }
    Object personalSummary = r.get("personalSummary");
    if (personalSummary != null) {
      out.put("personalSummary", personalSummary);
    }
    out.put("selfReview", parseReviewJson((String) r.get("selfReview")));
    if (!hideReviewData) {
      out.put("managerReview", mgrRevForTotals);
      out.put("dottedManagerReview", dotRevForTotals);
    }
    out.put("totalScore", toDouble((BigDecimal) r.get("totalScore")));
    if (managerWeightedTotal != null) {
      out.put("managerWeightedTotal", managerWeightedTotal);
    }
    if (dottedManagerWeightedTotal != null) {
      out.put("dottedManagerWeightedTotal", dottedManagerWeightedTotal);
    }
    Object rejectionReason = r.get("rejectionReason");
    if (rejectionReason != null) {
      out.put("rejectionReason", rejectionReason);
    }
    Object finalReviewerId = r.get("finalReviewerId");
    if (finalReviewerId != null) {
      out.put("finalReviewerId", finalReviewerId);
    }
    Timestamp finalReviewedAt = (Timestamp) r.get("finalReviewedAt");
    if (finalReviewedAt != null) {
      out.put("finalReviewedAt", iso(finalReviewedAt));
    }
    out.put("indicators", indicators);
    if (reviewRoleWeights != null) {
      out.put("reviewRoleWeights", reviewRoleWeights);
    }
    if (reviewMergedIndicators != null) {
      out.put("reviewMergedIndicators", reviewMergedIndicators);
    }
    if (reviewMergedTotal != null) {
      out.put("reviewMergedTotal", reviewMergedTotal);
    }
    out.put("createdAt", iso((Timestamp) r.get("createdAt")));
    out.put("updatedAt", iso((Timestamp) r.get("updatedAt")));
    return out;
  }

  private Map<String, String> loadEmployeeNames(Set<String> ids) {
    if (ids.isEmpty()) {
      return Collections.emptyMap();
    }
    List<String> idList = new ArrayList<String>(ids);
    String placeholders = String.join(",", Collections.nCopies(idList.size(), "?"));
    Map<String, String> nameById = new HashMap<String, String>();
    jdbc.query(
        "SELECT employee_id, name FROM employee_hierarchy WHERE employee_id IN (" + placeholders + ")",
        rs -> {
          nameById.put(rs.getString("employee_id"), trimOrEmpty(rs.getString("name")));
        },
        idList.toArray());
    return nameById;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> saveDraft(String userId, String id, Map<String, Object> body) {
    Map<String, Object> r = loadRecord(id);
    String reviewType = String.valueOf(body.get("reviewType"));
    Object content = body.get("content");
    List<String> sets = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();

    if ("goal".equals(reviewType)) {
      if (!String.valueOf(r.get("employeeId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权保存目标设定");
      }
      sets.add("goal_setting=?");
      args.add(writeJson(content));
    } else if ("self".equals(reviewType)) {
      if (!String.valueOf(r.get("employeeId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权保存该草稿");
      }
      sets.add("self_review=?");
      args.add(writeJson(content));
      if (body.containsKey("personalSummary")) {
        sets.add("personal_summary=?");
        args.add(body.get("personalSummary"));
      }
    } else if ("manager".equals(reviewType)) {
      if (!String.valueOf(r.get("managerId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权保存该草稿");
      }
      String st = String.valueOf(r.get("status"));
      if (!"manager_review".equals(st) && !"dual_manager_review".equals(st) && !"final_review".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许保存上级评分草稿");
      }
      sets.add("manager_review=?");
      args.add(writeJson(content));
    } else if ("dotted_manager".equals(reviewType)) {
      if (r.get("dottedManagerId") == null || !String.valueOf(r.get("dottedManagerId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权保存该草稿");
      }
      String st = String.valueOf(r.get("status"));
      if (!"dotted_manager_review".equals(st) && !"dual_manager_review".equals(st) && !"final_review".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许保存虚线上级评分草稿");
      }
      sets.add("dotted_manager_review=?");
      args.add(writeJson(content));
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无效的评审类型");
    }

    args.add(id);
    jdbc.update("UPDATE performance_record SET " + String.join(", ", sets) + " WHERE id=?", args.toArray());
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    return out;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> submit(String userId, String id, Map<String, Object> body) throws Exception {
    Map<String, Object> r = loadRecord(id);
    String reviewType = String.valueOf(body.get("reviewType"));
    List<Map<String, Object>> content =
        body.get("content") instanceof List ? (List<Map<String, Object>>) body.get("content") : null;
    String newStatus = String.valueOf(r.get("status"));
    List<String> sets = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();

    if ("goal".equals(reviewType)) {
      if (!String.valueOf(r.get("employeeId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权提交目标设定");
      }
      String st = String.valueOf(r.get("status"));
      if (!"goal_setting".equals(st) && !"goal_rejected".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许提交目标设定");
      }
      sets.add("goal_setting=?");
      args.add(writeJson(content));
      newStatus = "goal_pending_review";
    } else if ("self".equals(reviewType)) {
      if (!String.valueOf(r.get("employeeId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权提交自评");
      }
      String st = String.valueOf(r.get("status"));
      if (!"self_review".equals(st) && !"goal_rejected".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许提交自评");
      }
      sets.add("self_review=?");
      args.add(writeJson(content));
      if (body.containsKey("personalSummary")) {
        sets.add("personal_summary=?");
        args.add(body.get("personalSummary"));
      }
      String templateId = r.get("templateId") == null ? null : String.valueOf(r.get("templateId"));
      List<Map<String, Object>> indicators = getTemplateIndicators(templateId);
      List<String> indicatorNames = new ArrayList<String>();
      for (Map<String, Object> ind : indicators) {
        indicatorNames.add(String.valueOf(ind.get("name")));
      }
      if (isReviewComplete(content, indicatorNames)) {
        sets.add("total_score=?");
        args.add(String.valueOf(round2(calcWeightedScore(content, indicators))));
      }
      newStatus = r.get("dottedManagerId") != null ? "dual_manager_review" : "manager_review";
    } else if ("manager".equals(reviewType)) {
      if (!String.valueOf(r.get("managerId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权提交上级评分");
      }
      String st = String.valueOf(r.get("status"));
      if (!"manager_review".equals(st) && !"dual_manager_review".equals(st) && !"final_review".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许提交上级评分");
      }
      sets.add("manager_review=?");
      args.add(writeJson(content));
      boolean hasDotted = r.get("dottedManagerId") != null;
      String templateId = r.get("templateId") == null ? null : String.valueOf(r.get("templateId"));
      List<Map<String, Object>> indicators = getTemplateIndicators(templateId);
      List<String> indicatorNames = new ArrayList<String>();
      for (Map<String, Object> ind : indicators) {
        indicatorNames.add(String.valueOf(ind.get("name")));
      }
      List<Map<String, Object>> existingDot = parseReviewJson((String) r.get("dottedManagerReview"));

      if ("final_review".equals(st)) {
        newStatus = "final_review";
        if (hasDotted) {
          boolean mgrComplete = isReviewComplete(content, indicatorNames);
          boolean dotComplete = isReviewComplete(existingDot, indicatorNames);
          if (mgrComplete && dotComplete) {
            sets.add("total_score=?");
            args.add(String.valueOf(computeTotalScore(templateId, content, existingDot, true)));
          } else if (mgrComplete) {
            sets.add("total_score=?");
            args.add(String.valueOf(round2(calcWeightedScore(content, indicators))));
          }
        } else {
          sets.add("total_score=?");
          args.add(String.valueOf(computeTotalScore(templateId, content, null, false)));
        }
      } else if ("dual_manager_review".equals(st)) {
        boolean mgrComplete = isReviewComplete(content, indicatorNames);
        boolean dotComplete = isReviewComplete(existingDot, indicatorNames);
        if (mgrComplete && dotComplete && hasDotted) {
          sets.add("total_score=?");
          args.add(String.valueOf(computeTotalScore(templateId, content, existingDot, true)));
          newStatus = "final_review";
        } else {
          newStatus = "dual_manager_review";
          if (mgrComplete) {
            sets.add("total_score=?");
            args.add(String.valueOf(round2(calcWeightedScore(content, indicators))));
          }
        }
      } else {
        if (!hasDotted) {
          sets.add("total_score=?");
          args.add(String.valueOf(computeTotalScore(templateId, content, null, false)));
          newStatus = "final_review";
        } else {
          newStatus = "dual_manager_review";
          if (isReviewComplete(content, indicatorNames)) {
            sets.add("total_score=?");
            args.add(String.valueOf(round2(calcWeightedScore(content, indicators))));
          }
        }
      }
    } else if ("dotted_manager".equals(reviewType)) {
      if (r.get("dottedManagerId") == null || !String.valueOf(r.get("dottedManagerId")).equals(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权提交虚线上级评分");
      }
      String st = String.valueOf(r.get("status"));
      if (!"dotted_manager_review".equals(st) && !"dual_manager_review".equals(st) && !"final_review".equals(st)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许提交虚线上级评分");
      }
      sets.add("dotted_manager_review=?");
      args.add(writeJson(content));
      String templateId = r.get("templateId") == null ? null : String.valueOf(r.get("templateId"));
      List<Map<String, Object>> indicators = getTemplateIndicators(templateId);
      List<String> indicatorNames = new ArrayList<String>();
      for (Map<String, Object> ind : indicators) {
        indicatorNames.add(String.valueOf(ind.get("name")));
      }
      List<Map<String, Object>> existingMgr = parseReviewJson((String) r.get("managerReview"));

      if ("final_review".equals(st)) {
        newStatus = "final_review";
        boolean dotComplete = isReviewComplete(content, indicatorNames);
        boolean mgrComplete = isReviewComplete(existingMgr, indicatorNames);
        if (mgrComplete && dotComplete && r.get("dottedManagerId") != null) {
          sets.add("total_score=?");
          args.add(String.valueOf(computeTotalScore(templateId, existingMgr, content, true)));
        } else if (dotComplete) {
          sets.add("total_score=?");
          args.add(String.valueOf(round2(calcWeightedScore(content, indicators))));
        }
      } else if ("dual_manager_review".equals(st)) {
        boolean dotComplete = isReviewComplete(content, indicatorNames);
        boolean mgrComplete = isReviewComplete(existingMgr, indicatorNames);
        if (mgrComplete && dotComplete) {
          sets.add("total_score=?");
          args.add(String.valueOf(computeTotalScore(templateId, existingMgr, content, true)));
          newStatus = "final_review";
        } else {
          newStatus = "dual_manager_review";
          if (dotComplete) {
            sets.add("total_score=?");
            args.add(String.valueOf(round2(calcWeightedScore(content, indicators))));
          }
        }
      } else {
        sets.add("total_score=?");
        args.add(String.valueOf(computeTotalScore(templateId, existingMgr, content, true)));
        newStatus = "final_review";
      }
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无效的评审类型");
    }

    sets.add("status=?");
    args.add(newStatus);
    args.add(id);
    jdbc.update("UPDATE performance_record SET " + String.join(", ", sets) + " WHERE id=?", args.toArray());
    log.info("绩效 {} 已提交，新状态: {}", id, newStatus);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", newStatus);
    return out;
  }

  public Map<String, Object> reject(String userId, String id, String reason) {
    Map<String, Object> r = loadRecord(id);
    boolean isManager = String.valueOf(r.get("managerId")).equals(userId);
    boolean isDottedManager =
        r.get("dottedManagerId") != null && String.valueOf(r.get("dottedManagerId")).equals(userId);
    if (!isManager && !isDottedManager) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权驳回该绩效");
    }
    String st = String.valueOf(r.get("status"));
    if (isManager && !"manager_review".equals(st) && !"dual_manager_review".equals(st)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许驳回");
    }
    if (isDottedManager && !"dotted_manager_review".equals(st) && !"dual_manager_review".equals(st)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许驳回");
    }
    jdbc.update(
        "UPDATE performance_record SET status=?, rejection_reason=? WHERE id=?",
        "goal_rejected",
        reason,
        id);
    log.info("绩效 {} 已驳回，原因: {}", id, reason);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    return out;
  }

  public Map<String, Object> listMonthPeriodsForCreate(String userId) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先登录");
    }
    menuPermissionService.assertMenuAllowed(userId, "performance_batch_create");
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT period_key, name FROM evaluation_period WHERE period_type=? ORDER BY sort_order ASC, period_key DESC",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("periodKey", rs.getString("period_key"));
              m.put("name", trimOrEmpty(rs.getString("name")));
              return m;
            },
            "month");
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> createBatch(String userId, Map<String, Object> body) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先登录");
    }
    menuPermissionService.assertMenuAllowed(userId, "performance_batch_create");

    List<String> employeeNamesToCreate = new ArrayList<String>();
    if (body.get("employeeNames") instanceof List) {
      for (Object o : (List<?>) body.get("employeeNames")) {
        if (o != null) {
          employeeNamesToCreate.add(String.valueOf(o));
        }
      }
    }
    Object deptNameObj = body.get("departmentName");
    if (deptNameObj != null && !String.valueOf(deptNameObj).isEmpty()) {
      String departmentName = String.valueOf(deptNameObj);
      List<String> deptNames =
          jdbc.query(
              "SELECT name FROM employee_hierarchy WHERE department_name = ?",
              (rs, rn) -> rs.getString("name"),
              departmentName);
      for (String n : deptNames) {
        if (n != null && !n.isEmpty()) {
          employeeNamesToCreate.add(n);
        }
      }
    }
    Set<String> unique = new LinkedHashSet<String>();
    for (String n : employeeNamesToCreate) {
      unique.add(n);
    }
    if (unique.size() > 100) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "单次最多创建100条绩效记录");
    }
    String period = String.valueOf(body.get("period"));
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

    for (String employeeName : unique) {
      try {
        List<Map<String, Object>> hierarchy =
            jdbc.query(
                "SELECT employee_id, name, manager_id, dotted_manager_id FROM employee_hierarchy WHERE name = ? LIMIT 1",
                (rs, rn) -> {
                  Map<String, Object> m = new LinkedHashMap<String, Object>();
                  m.put("employeeId", rs.getString("employee_id"));
                  m.put("name", rs.getString("name"));
                  m.put("managerId", rs.getString("manager_id"));
                  m.put("dottedManagerId", rs.getString("dotted_manager_id"));
                  return m;
                },
                employeeName);
        if (hierarchy.isEmpty()) {
          results.add(resultRow("", employeeName, false, null, "员工不在层级表中，请先同步员工信息"));
          continue;
        }
        Map<String, Object> emp = hierarchy.get(0);
        String employeeIdValue = String.valueOf(emp.get("employeeId"));
        String managerId = emp.get("managerId") == null ? null : String.valueOf(emp.get("managerId"));
        String dottedManagerId =
            emp.get("dottedManagerId") == null ? null : String.valueOf(emp.get("dottedManagerId"));
        if (managerId == null || managerId.isEmpty()) {
          results.add(resultRow(employeeIdValue, (String) emp.get("name"), false, null, "该员工未设置直属上级"));
          continue;
        }
        Integer existing =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM performance_record WHERE employee_id=? AND period=?",
                Integer.class,
                employeeIdValue,
                period);
        if (existing != null && existing > 0) {
          results.add(
              resultRow(
                  employeeIdValue,
                  (String) emp.get("name"),
                  false,
                  null,
                  "该员工在 " + period + " 周期已有绩效记录"));
          continue;
        }
        String newId = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO performance_record (id, employee_id, period, status, manager_id, dotted_manager_id) VALUES (?,?,?,?,?,?)",
            newId,
            employeeIdValue,
            period,
            "template_selection",
            managerId,
            dottedManagerId);
        results.add(resultRow(employeeIdValue, (String) emp.get("name"), true, newId, null));
        log.info("创建绩效记录: employeeName={}, period={}, id={}", employeeName, period, newId);
      } catch (Exception e) {
        results.add(resultRow("", employeeName, false, null, e.getMessage() == null ? "创建失败" : e.getMessage()));
      }
    }

    int successCount = 0;
    for (Map<String, Object> r : results) {
      if (Boolean.TRUE.equals(r.get("success"))) {
        successCount++;
      }
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("results", results);
    out.put("total", results.size());
    out.put("successCount", successCount);
    out.put("failCount", results.size() - successCount);
    return out;
  }

  private static Map<String, Object> resultRow(
      String employeeId, String employeeName, boolean success, String id, String error) {
    Map<String, Object> m = new LinkedHashMap<String, Object>();
    m.put("employeeId", employeeId);
    if (employeeName != null) {
      m.put("employeeName", employeeName);
    }
    m.put("success", success);
    if (id != null) {
      m.put("id", id);
    }
    if (error != null) {
      m.put("error", error);
    }
    return m;
  }

  public Map<String, Object> approveGoal(String userId, String id, Map<String, Object> body) {
    Map<String, Object> r = loadRecord(id);
    boolean isManager = String.valueOf(r.get("managerId")).equals(userId);
    boolean isDottedManager =
        r.get("dottedManagerId") != null && String.valueOf(r.get("dottedManagerId")).equals(userId);
    if (!isManager && !isDottedManager) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权审核目标设定");
    }
    if (!"goal_pending_review".equals(String.valueOf(r.get("status")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许审核目标");
    }
    boolean approved = Boolean.TRUE.equals(body.get("approved"));
    String newStatus = approved ? "self_review" : "goal_rejected";
    if (approved) {
      jdbc.update(
          "UPDATE performance_record SET status=?, goal_approved_by=? WHERE id=?",
          newStatus,
          userId,
          id);
    } else {
      String rejectionReason =
          body.get("rejectionReason") == null ? "目标设定未通过审核" : String.valueOf(body.get("rejectionReason"));
      jdbc.update(
          "UPDATE performance_record SET status=?, goal_approved_by=?, rejection_reason=? WHERE id=?",
          newStatus,
          userId,
          rejectionReason,
          id);
    }
    log.info("绩效 {} 目标审核完成，新状态: {}", id, newStatus);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", newStatus);
    return out;
  }

  public Map<String, Object> finalReview(String userId, String id, Map<String, Object> body) {
    MenuFlags flags = performanceMenuFlags(userId);
    if (!flags.reviewAdmin) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权执行终审操作");
    }
    Map<String, Object> r = loadRecord(id);
    if (!"final_review".equals(String.valueOf(r.get("status")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许终审");
    }
    boolean approved = Boolean.TRUE.equals(body.get("approved"));
    String newStatus;
    if (approved) {
      newStatus = "completed";
      jdbc.update(
          "UPDATE performance_record SET status=?, final_reviewer_id=?, final_reviewed_at=NOW() WHERE id=?",
          newStatus,
          userId,
          id);
    } else {
      newStatus =
          body.get("returnToStage") == null ? "self_review" : String.valueOf(body.get("returnToStage"));
      String rejectionReason =
          body.get("rejectionReason") == null ? "终审未通过" : String.valueOf(body.get("rejectionReason"));
      jdbc.update(
          "UPDATE performance_record SET status=?, final_reviewer_id=?, final_reviewed_at=NOW(), rejection_reason=? WHERE id=?",
          newStatus,
          userId,
          rejectionReason,
          id);
    }
    log.info("绩效 {} 终审完成，新状态: {}", id, newStatus);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", newStatus);
    return out;
  }

  public Map<String, Object> selectTemplate(String userId, String performanceId, String templateId) {
    Map<String, Object> r = loadRecord(performanceId);
    if (!String.valueOf(r.get("employeeId")).equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有本人可以选择模板");
    }
    if (!"template_selection".equals(String.valueOf(r.get("status")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许选择模板");
    }
    List<Map<String, Object>> templates =
        jdbc.query(
            "SELECT status FROM performance_template WHERE id = ? LIMIT 1",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("status", rs.getString("status"));
              return m;
            },
            templateId);
    if (templates.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    if (!"enabled".equals(templates.get(0).get("status"))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该模板已停用");
    }
    jdbc.update(
        "UPDATE performance_record SET template_id=?, status=? WHERE id=?",
        templateId,
        "goal_setting",
        performanceId);
    log.info("绩效 {} 选择模板: {}", performanceId, templateId);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", "goal_setting");
    return out;
  }

  public Map<String, Object> calibrate(String userId, String id, Map<String, Object> body) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先登录");
    }
    MenuFlags flags = performanceMenuFlags(userId);
    if (!flags.reviewAdmin) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权执行绩效校准");
    }
    Map<String, Object> r = loadRecord(id);
    if (!"final_review".equals(String.valueOf(r.get("status")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前状态不允许绩效校准");
    }
    boolean approved = Boolean.TRUE.equals(body.get("approved"));
    String newStatus;
    if (approved) {
      newStatus = "completed";
      if (body.containsKey("finalScore") && body.get("finalScore") instanceof Number) {
        jdbc.update(
            "UPDATE performance_record SET status=?, final_reviewer_id=?, final_reviewed_at=NOW(), total_score=? WHERE id=?",
            newStatus,
            userId,
            ((Number) body.get("finalScore")).doubleValue(),
            id);
      } else {
        jdbc.update(
            "UPDATE performance_record SET status=?, final_reviewer_id=?, final_reviewed_at=NOW() WHERE id=?",
            newStatus,
            userId,
            id);
      }
    } else {
      newStatus =
          body.get("returnToStage") == null ? "self_review" : String.valueOf(body.get("returnToStage"));
      String rejectionReason =
          body.get("rejectionReason") == null ? "校准未通过" : String.valueOf(body.get("rejectionReason"));
      jdbc.update(
          "UPDATE performance_record SET status=?, final_reviewer_id=?, final_reviewed_at=NOW(), rejection_reason=? WHERE id=?",
          newStatus,
          userId,
          rejectionReason,
          id);
    }
    log.info("绩效 {} 校准完成，新状态: {}", id, newStatus);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    out.put("newStatus", newStatus);
    return out;
  }

  public Map<String, Object> ensureMonthlyPerformanceRecordsForPeriod(String period) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT employee_id, manager_id, dotted_manager_id FROM employee_hierarchy WHERE manager_id IS NOT NULL",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("employeeId", rs.getString("employee_id"));
              m.put("managerId", rs.getString("manager_id"));
              m.put("dottedManagerId", rs.getString("dotted_manager_id"));
              return m;
            });

    int created = 0;
    int skipped = 0;
    for (Map<String, Object> emp : rows) {
      String employeeIdValue = String.valueOf(emp.get("employeeId"));
      String managerId = emp.get("managerId") == null ? null : String.valueOf(emp.get("managerId"));
      if (managerId == null || managerId.isEmpty()) {
        skipped++;
        continue;
      }
      Integer existing =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM performance_record WHERE employee_id=? AND period=?",
              Integer.class,
              employeeIdValue,
              period);
      if (existing != null && existing > 0) {
        skipped++;
        continue;
      }
      String newId = UUID.randomUUID().toString();
      String dottedManagerId =
          emp.get("dottedManagerId") == null ? null : String.valueOf(emp.get("dottedManagerId"));
      jdbc.update(
          "INSERT INTO performance_record (id, employee_id, period, status, manager_id, dotted_manager_id) VALUES (?,?,?,?,?,?)",
          newId,
          employeeIdValue,
          period,
          "template_selection",
          managerId,
          dottedManagerId);
      created++;
    }
    log.info("月度绩效自动创建 period={} created={} skipped={}", period, created, skipped);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("created", created);
    out.put("skipped", skipped);
    return out;
  }
}
