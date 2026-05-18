package com.jixiao2.server.assessmentrule;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssessmentRuleService {

  private static final double WEIGHT_SUM_EPS = 0.001;

  private final JdbcTemplate jdbc;

  public AssessmentRuleService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> list(int page, int pageSize) {
    Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM assessment_rule", Integer.class);
    int offset = (page - 1) * pageSize;
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT id, name, manager_weight, dotted_manager_weight, status, _created_at, _updated_at "
                + "FROM assessment_rule ORDER BY _created_at DESC LIMIT ? OFFSET ?",
            new Object[] {pageSize, offset},
            (rs, rn) -> mapRow(rs));
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("total", total == null ? 0 : total);
    out.put("page", page);
    out.put("pageSize", pageSize);
    return out;
  }

  public Map<String, Object> getById(String id) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT id, name, manager_weight, dotted_manager_weight, status, _created_at, _updated_at FROM assessment_rule WHERE id = ?",
            new Object[] {id},
            (rs, rn) -> mapRow(rs));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "考核规则不存在");
    }
    return rows.get(0);
  }

  public Map<String, Object> create(String userId, Map<String, Object> body) {
    validateWeights(body.get("managerWeight"), body.get("dottedManagerWeight"));
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assessment_rule (id, name, manager_weight, dotted_manager_weight, status, _created_by, _updated_by) VALUES (?,?,?,?,?,?,?)",
        id,
        String.valueOf(body.get("name")).trim(),
        toDouble(body.get("managerWeight")),
        toDouble(body.get("dottedManagerWeight")),
        body.get("status") == null ? "enabled" : String.valueOf(body.get("status")),
        userId,
        userId);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("id", id);
    return out;
  }

  public Map<String, Boolean> update(String id, Map<String, Object> body) {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM assessment_rule WHERE id = ?", Integer.class, id) == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "考核规则不存在");
    }
    if (body.containsKey("managerWeight") || body.containsKey("dottedManagerWeight")) {
      Map<String, Object> cur = getById(id);
      double mw =
          body.containsKey("managerWeight")
              ? toDouble(body.get("managerWeight"))
              : ((Number) cur.get("managerWeight")).doubleValue();
      double dw =
          body.containsKey("dottedManagerWeight")
              ? toDouble(body.get("dottedManagerWeight"))
              : ((Number) cur.get("dottedManagerWeight")).doubleValue();
      validateWeights(mw, dw);
    }
    List<String> sets = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();
    if (body.containsKey("name")) {
      sets.add("name = ?");
      args.add(String.valueOf(body.get("name")).trim());
    }
    if (body.containsKey("managerWeight")) {
      sets.add("manager_weight = ?");
      args.add(toDouble(body.get("managerWeight")));
    }
    if (body.containsKey("dottedManagerWeight")) {
      sets.add("dotted_manager_weight = ?");
      args.add(toDouble(body.get("dottedManagerWeight")));
    }
    if (body.containsKey("status")) {
      sets.add("status = ?");
      args.add(String.valueOf(body.get("status")));
    }
    if (!sets.isEmpty()) {
      args.add(id);
      jdbc.update(
          "UPDATE assessment_rule SET " + String.join(", ", sets) + ", _updated_at = NOW() WHERE id = ?",
          args.toArray());
    }
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", Boolean.TRUE);
    return out;
  }

  public Map<String, Boolean> delete(String id) {
    Integer usedPr =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_record WHERE assessment_rule_id = ? AND deleted_at IS NULL",
            Integer.class,
            id);
    if (usedPr != null && usedPr > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已有绩效记录使用该规则，无法删除");
    }
    Integer usedTpl =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_template WHERE assessment_rule_id = ?", Integer.class, id);
    if (usedTpl != null && usedTpl > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已有绩效模板绑定该规则，无法删除");
    }
    Integer usedEmp =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM employee_hierarchy WHERE assessment_rule_id = ?", Integer.class, id);
    if (usedEmp != null && usedEmp > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已有员工绑定该规则，无法删除");
    }
    int n = jdbc.update("DELETE FROM assessment_rule WHERE id = ?", id);
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "考核规则不存在");
    }
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", Boolean.TRUE);
    return out;
  }

  private static Map<String, Object> mapRow(ResultSet rs) throws SQLException {
    Map<String, Object> m = new LinkedHashMap<String, Object>();
    m.put("id", rs.getString("id"));
    m.put("name", rs.getString("name"));
    m.put("managerWeight", rs.getBigDecimal("manager_weight").doubleValue());
    m.put("dottedManagerWeight", rs.getBigDecimal("dotted_manager_weight").doubleValue());
    m.put("status", rs.getString("status"));
    Timestamp c = rs.getTimestamp("_created_at");
    Timestamp u = rs.getTimestamp("_updated_at");
    m.put("createdAt", c == null ? null : c.toInstant().toString());
    m.put("updatedAt", u == null ? null : u.toInstant().toString());
    return m;
  }

  private static double toDouble(Object o) {
    if (o instanceof Number) {
      return ((Number) o).doubleValue();
    }
    return Double.parseDouble(String.valueOf(o));
  }

  private static void validateWeights(Object mwObj, Object dwObj) {
    double mw = toDouble(mwObj);
    double dw = toDouble(dwObj);
    if (mw < 0 || dw < 0 || !Double.isFinite(mw) || !Double.isFinite(dw)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上级与虚线权重须为非负有限数");
    }
    if (mw <= 0 && dw <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上级与虚线权重须至少一侧大于 0");
    }
    if (Math.abs(mw + dw - 1.0) > WEIGHT_SUM_EPS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上级权重与虚线权重之和须为 1（即 100%）");
    }
  }
}
