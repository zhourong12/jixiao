package com.jixiao2.server.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class TemplateService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbc;

  public TemplateService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> list(int page, int pageSize) {
    Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM performance_template", Integer.class);
    int offset = (page - 1) * pageSize;
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT id, name, position, indicators, status, version, _created_at FROM performance_template ORDER BY _created_at DESC LIMIT ? OFFSET ?",
            new Object[] {pageSize, offset},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("name", rs.getString("name"));
              m.put("position", rs.getString("position"));
              m.put("indicatorCount", indicatorCount(rs.getString("indicators")));
              m.put("status", rs.getString("status"));
              m.put("version", rs.getInt("version"));
              Timestamp created = rs.getTimestamp("_created_at");
              m.put("createdAt", created == null ? null : created.toInstant().toString());
              return m;
            });
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
            "SELECT id, name, position, indicators, status, version, _created_at, _updated_at FROM performance_template WHERE id = ?",
            new Object[] {id},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("name", rs.getString("name"));
              m.put("position", rs.getString("position"));
              m.put("indicators", parseIndicators(rs.getString("indicators")));
              m.put("status", rs.getString("status"));
              m.put("version", rs.getInt("version"));
              Timestamp created = rs.getTimestamp("_created_at");
              Timestamp updated = rs.getTimestamp("_updated_at");
              m.put("createdAt", created == null ? null : created.toInstant().toString());
              m.put("updatedAt", updated == null ? null : updated.toInstant().toString());
              return m;
            });
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    return rows.get(0);
  }

  public Map<String, Object> create(String userId, Map<String, Object> body) {
    String id = UUID.randomUUID().toString();
    String indicators = toJson(body.get("indicators"));
    jdbc.update(
        "INSERT INTO performance_template (id, name, position, indicators, _created_by, _updated_by) VALUES (?,?,?,CAST(? AS JSON),?,?)",
        id,
        body.get("name"),
        body.get("position"),
        indicators,
        userId,
        userId);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("id", id);
    return out;
  }

  public Map<String, Boolean> update(String id, Map<String, Object> body) {
    List<Map<String, Object>> existing =
        jdbc.query(
            "SELECT version FROM performance_template WHERE id = ?",
            new Object[] {id},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("version", rs.getInt("version"));
              return m;
            });
    if (existing.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    List<String> sets = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();
    if (body.containsKey("name")) {
      sets.add("name = ?");
      args.add(body.get("name"));
    }
    if (body.containsKey("position")) {
      sets.add("position = ?");
      args.add(body.get("position"));
    }
    if (body.containsKey("indicators")) {
      sets.add("indicators = CAST(? AS JSON)");
      args.add(toJson(body.get("indicators")));
      sets.add("version = ?");
      args.add(((Number) existing.get(0).get("version")).intValue() + 1);
    }
    if (!sets.isEmpty()) {
      args.add(id);
      jdbc.update(
          "UPDATE performance_template SET " + String.join(", ", sets) + " WHERE id = ?",
          args.toArray());
    }
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", Boolean.TRUE);
    return out;
  }

  public Map<String, Object> toggleStatus(String id) {
    List<String> statuses =
        jdbc.query(
            "SELECT status FROM performance_template WHERE id = ?",
            new Object[] {id},
            (rs, rn) -> rs.getString("status"));
    if (statuses.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    String current = statuses.get(0);
    String next = "enabled".equals(current) ? "disabled" : "enabled";
    jdbc.update("UPDATE performance_template SET status = ? WHERE id = ?", next, id);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", Boolean.TRUE);
    out.put("newStatus", next);
    return out;
  }

  public Map<String, Object> copy(String id) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT name, position, indicators FROM performance_template WHERE id = ?",
            new Object[] {id},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("name", rs.getString("name"));
              m.put("position", rs.getString("position"));
              m.put("indicators", rs.getString("indicators"));
              return m;
            });
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    String newId = UUID.randomUUID().toString();
    Map<String, Object> src = rows.get(0);
    jdbc.update(
        "INSERT INTO performance_template (id, name, position, indicators, status, version) VALUES (?,?,?,CAST(? AS JSON),?,1)",
        newId,
        String.valueOf(src.get("name")) + " (副本)",
        src.get("position"),
        src.get("indicators"),
        "disabled");
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("newTemplateId", newId);
    return out;
  }

  public Map<String, Boolean> delete(String id) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM performance_template WHERE id = ?", Integer.class, id);
    if (count == null || count == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }
    jdbc.update("DELETE FROM performance_template WHERE id = ?", id);
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", Boolean.TRUE);
    return out;
  }

  private static int indicatorCount(String json) {
    try {
      JsonNode n = MAPPER.readTree(json);
      return n.isArray() ? n.size() : 0;
    } catch (Exception e) {
      return 0;
    }
  }

  private static Object parseIndicators(String json) {
    try {
      return MAPPER.readValue(json, Object.class);
    } catch (Exception e) {
      return new ArrayList<Object>();
    }
  }

  private static String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      return "[]";
    }
  }
}
