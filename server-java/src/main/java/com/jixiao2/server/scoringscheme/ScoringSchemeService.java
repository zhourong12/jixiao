package com.jixiao2.server.scoringscheme;

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
public class ScoringSchemeService {

  private static final double WEIGHT_SUM_EPS = 0.01;

  private final JdbcTemplate jdbc;

  public ScoringSchemeService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> list(int page, int pageSize) {
    Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM scoring_scheme", Integer.class);
    int offset = (page - 1) * pageSize;
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT id, name, performance_weight, culture_weight, learning_weight, status, _created_at, _updated_at "
                + "FROM scoring_scheme ORDER BY _created_at DESC LIMIT ? OFFSET ?",
            new Object[] {pageSize, offset},
            (rs, rn) -> mapRow(rs));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("list", items);
    out.put("total", total == null ? 0 : total);
    out.put("page", page);
    out.put("pageSize", pageSize);
    return out;
  }

  public Map<String, Object> getById(String id) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT id, name, performance_weight, culture_weight, learning_weight, status, _created_at, _updated_at FROM scoring_scheme WHERE id = ?",
            new Object[] {id},
            (rs, rn) -> mapRow(rs));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评分方案不存在");
    }
    return rows.get(0);
  }

  public Map<String, Object> create(String userId, Map<String, Object> body) {
    double pw = toDouble(body.get("performanceWeight"));
    double cw = toDouble(body.get("cultureWeight"));
    double lw = toDouble(body.get("learningWeight"));
    validateWeights(pw, cw, lw);
    String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
    if (name.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "方案名称不能为空");
    }
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO scoring_scheme (id, name, performance_weight, culture_weight, learning_weight, status, _created_by, _updated_by) VALUES (?,?,?,?,?,?,?,?)",
        id, name, pw, cw, lw,
        body.get("status") == null ? "enabled" : String.valueOf(body.get("status")),
        userId, userId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", id);
    return out;
  }

  public Map<String, Boolean> update(String id, Map<String, Object> body) {
    Map<String, Object> cur = getById(id);
    double pw = body.containsKey("performanceWeight") ? toDouble(body.get("performanceWeight")) : ((Number) cur.get("performanceWeight")).doubleValue();
    double cw = body.containsKey("cultureWeight") ? toDouble(body.get("cultureWeight")) : ((Number) cur.get("cultureWeight")).doubleValue();
    double lw = body.containsKey("learningWeight") ? toDouble(body.get("learningWeight")) : ((Number) cur.get("learningWeight")).doubleValue();
    if (body.containsKey("performanceWeight") || body.containsKey("cultureWeight") || body.containsKey("learningWeight")) {
      validateWeights(pw, cw, lw);
    }
    List<String> sets = new ArrayList<>();
    List<Object> args = new ArrayList<>();
    if (body.containsKey("name")) {
      sets.add("name = ?");
      args.add(String.valueOf(body.get("name")).trim());
    }
    if (body.containsKey("performanceWeight")) {
      sets.add("performance_weight = ?");
      args.add(pw);
    }
    if (body.containsKey("cultureWeight")) {
      sets.add("culture_weight = ?");
      args.add(cw);
    }
    if (body.containsKey("learningWeight")) {
      sets.add("learning_weight = ?");
      args.add(lw);
    }
    if (body.containsKey("status")) {
      sets.add("status = ?");
      args.add(String.valueOf(body.get("status")));
    }
    if (!sets.isEmpty()) {
      args.add(id);
      jdbc.update(
          "UPDATE scoring_scheme SET " + String.join(", ", sets) + ", _updated_at = NOW() WHERE id = ?",
          args.toArray());
    }
    Map<String, Boolean> out = new LinkedHashMap<>();
    out.put("success", Boolean.TRUE);
    return out;
  }

  public Map<String, Boolean> delete(String id) {
    Integer used =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_record WHERE scoring_scheme_id = ? AND deleted_at IS NULL",
            Integer.class, id);
    if (used != null && used > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已有绩效记录使用该方案，无法删除");
    }
    int n = jdbc.update("DELETE FROM scoring_scheme WHERE id = ?", id);
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评分方案不存在");
    }
    Map<String, Boolean> out = new LinkedHashMap<>();
    out.put("success", Boolean.TRUE);
    return out;
  }

  private static Map<String, Object> mapRow(ResultSet rs) throws SQLException {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", rs.getString("id"));
    m.put("name", rs.getString("name"));
    m.put("performanceWeight", rs.getBigDecimal("performance_weight").doubleValue());
    m.put("cultureWeight", rs.getBigDecimal("culture_weight").doubleValue());
    m.put("learningWeight", rs.getBigDecimal("learning_weight").doubleValue());
    m.put("status", rs.getString("status"));
    Timestamp c = rs.getTimestamp("_created_at");
    Timestamp u = rs.getTimestamp("_updated_at");
    m.put("createdAt", c == null ? null : c.toInstant().toString());
    m.put("updatedAt", u == null ? null : u.toInstant().toString());
    return m;
  }

  private static double toDouble(Object o) {
    if (o instanceof Number) return ((Number) o).doubleValue();
    return Double.parseDouble(String.valueOf(o));
  }

  private static void validateWeights(double pw, double cw, double lw) {
    if (pw < 0 || cw < 0 || lw < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "权重百分比须为非负数");
    }
    if (Math.abs(pw + cw + lw - 100.0) > WEIGHT_SUM_EPS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绩效、文化、学习三项权重之和须为 100");
    }
  }
}
