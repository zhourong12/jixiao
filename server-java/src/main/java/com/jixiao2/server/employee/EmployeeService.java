package com.jixiao2.server.employee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiao2.server.config.Jixiao2Properties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployeeService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbc;
  private final Jixiao2Properties properties;

  public EmployeeService(JdbcTemplate jdbc, Jixiao2Properties properties) {
    this.jdbc = jdbc;
    this.properties = properties;
  }

  private static String pickPrimaryRoleKey(List<String> keys) {
    if (keys.isEmpty()) {
      return "employee";
    }
    if (keys.contains("super_admin")) {
      return "super_admin";
    }
    if (keys.contains("admin")) {
      return "admin";
    }
    if (keys.size() == 1) {
      return keys.get(0);
    }
    for (String k : keys) {
      if (!"employee".equals(k)) {
        return k;
      }
    }
    return "employee";
  }

  public List<Map<String, Object>> listAssignableRoles() {
    return jdbc.query(
        "SELECT role_key, name FROM role ORDER BY sort_order",
        (rs, rn) -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("roleKey", rs.getString("role_key"));
          m.put("name", rs.getString("name"));
          return m;
        });
  }

  public void setUserPrimaryRole(String userId, String roleKey) {
    String trimmed = roleKey == null ? "" : roleKey.trim();
    if (trimmed.isEmpty()) {
      jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
      return;
    }
    Integer exists =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM role WHERE role_key = ?", Integer.class, trimmed);
    if (exists == null || exists == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在");
    }
    jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
    jdbc.update("INSERT INTO user_role (user_id, role_key) VALUES (?,?)", userId, trimmed);
  }

  private void enrichItemsWithRoles(List<Map<String, Object>> items) {
    if (items.isEmpty()) {
      return;
    }
    List<String> ids = new ArrayList<String>();
    for (Map<String, Object> item : items) {
      ids.add(String.valueOf(item.get("employeeId")));
    }
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
    List<Map<String, Object>> urRows =
        jdbc.query(
            "SELECT user_id, role_key FROM user_role WHERE user_id IN (" + placeholders + ")",
            ids.toArray(),
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("userId", rs.getString("user_id"));
              m.put("roleKey", rs.getString("role_key"));
              return m;
            });
    Map<String, List<String>> keysByUser = new HashMap<String, List<String>>();
    for (Map<String, Object> row : urRows) {
      String uid = String.valueOf(row.get("userId"));
      List<String> list = keysByUser.get(uid);
      if (list == null) {
        list = new ArrayList<String>();
        keysByUser.put(uid, list);
      }
      list.add(String.valueOf(row.get("roleKey")));
    }
    List<Map<String, Object>> roleRows =
        jdbc.query(
            "SELECT role_key, name FROM role",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("roleKey", rs.getString("role_key"));
              m.put("name", rs.getString("name"));
              return m;
            });
    Map<String, String> nameByKey = new HashMap<String, String>();
    for (Map<String, Object> r : roleRows) {
      nameByKey.put(String.valueOf(r.get("roleKey")), String.valueOf(r.get("name")));
    }
    for (Map<String, Object> item : items) {
      String eid = String.valueOf(item.get("employeeId"));
      List<String> keys = keysByUser.get(eid);
      if (keys == null) {
        keys = Collections.emptyList();
      }
      String primary = pickPrimaryRoleKey(keys);
      item.put("roleKey", primary);
      item.put("roleName", nameByKey.containsKey(primary) ? nameByKey.get(primary) : primary);
    }
  }

  public Map<String, Object> getAllHierarchies(int page, int pageSize, String keyword) {
    List<Object> args = new ArrayList<Object>();
    StringBuilder where = new StringBuilder("1=1");
    if (keyword != null && !keyword.trim().isEmpty()) {
      String like = "%" + keyword.trim() + "%";
      where.append(
          " AND (name LIKE ? OR phone LIKE ? OR employee_no LIKE ? OR position LIKE ? OR department_name LIKE ?)");
      args.add(like);
      args.add(like);
      args.add(like);
      args.add(like);
      args.add(like);
    }
    Integer total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM employee_hierarchy WHERE " + where,
            Integer.class,
            args.toArray());
    int offset = (page - 1) * pageSize;
    List<Object> listArgs = new ArrayList<Object>(args);
    listArgs.add(pageSize);
    listArgs.add(offset);
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT employee_id, manager_id, dotted_manager_id, department_id, department_name, name, phone, "
                + "employee_no, employee_type, position, work_location, join_date FROM employee_hierarchy WHERE "
                + where
                + " LIMIT ? OFFSET ?",
            listArgs.toArray(),
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("employeeId", rs.getString("employee_id"));
              m.put("managerId", rs.getString("manager_id"));
              m.put("dottedManagerId", rs.getString("dotted_manager_id"));
              m.put("departmentId", rs.getString("department_id"));
              m.put("departmentName", rs.getString("department_name"));
              m.put("name", rs.getString("name"));
              m.put("phone", rs.getString("phone"));
              m.put("employeeNo", rs.getString("employee_no"));
              m.put("employeeType", rs.getString("employee_type"));
              m.put("position", rs.getString("position"));
              m.put("workLocation", rs.getString("work_location"));
              java.sql.Date join = rs.getDate("join_date");
              m.put("joinDate", join == null ? null : join.toString());
              return m;
            });
    Set<String> managerIds = new HashSet<String>();
    for (Map<String, Object> row : rows) {
      Object mid = row.get("managerId");
      if (mid != null) {
        managerIds.add(String.valueOf(mid));
      }
      Object did = row.get("dottedManagerId");
      if (did != null) {
        managerIds.add(String.valueOf(did));
      }
    }
    Map<String, String> managerNameMap = new HashMap<String, String>();
    if (!managerIds.isEmpty()) {
      List<String> mids = new ArrayList<String>(managerIds);
      String placeholders = String.join(",", Collections.nCopies(mids.size(), "?"));
      jdbc.query(
          "SELECT employee_id, name FROM employee_hierarchy WHERE employee_id IN (" + placeholders + ")",
          mids.toArray(),
          (org.springframework.jdbc.core.RowCallbackHandler)
              rs -> managerNameMap.put(rs.getString("employee_id"), rs.getString("name")));
    }
    for (Map<String, Object> row : rows) {
      Object mid = row.get("managerId");
      if (mid != null) {
        String id = String.valueOf(mid);
        row.put("managerName", managerNameMap.containsKey(id) ? managerNameMap.get(id) : id);
      }
      Object did = row.get("dottedManagerId");
      if (did != null) {
        String id = String.valueOf(did);
        row.put("dottedManagerName", managerNameMap.containsKey(id) ? managerNameMap.get(id) : id);
      }
    }
    enrichItemsWithRoles(rows);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", rows);
    out.put("total", total == null ? 0 : total);
    return out;
  }

  public List<String> getAllDepartments() {
    List<String> names =
        jdbc.query(
            "SELECT DISTINCT department_name FROM employee_hierarchy WHERE department_name IS NOT NULL AND department_name <> ''",
            (rs, rn) -> rs.getString("department_name"));
    Collections.sort(names);
    return names;
  }

  public List<Map<String, Object>> listDepartmentOptions() {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT department_id, department_name FROM employee_hierarchy WHERE department_id IS NOT NULL AND department_id <> '' GROUP BY department_id, department_name",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("department_id"));
              m.put("name", rs.getString("department_name"));
              return m;
            });
    rows.sort(
        (a, b) -> {
          String an = a.get("name") == null ? String.valueOf(a.get("id")) : String.valueOf(a.get("name"));
          String bn = b.get("name") == null ? String.valueOf(b.get("id")) : String.valueOf(b.get("name"));
          return an.compareTo(bn);
        });
    return rows;
  }

  public Map<String, Object> createEmployee(Map<String, Object> body) {
    String userId = String.valueOf(body.get("userId"));
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO employee_hierarchy (id, employee_id, name, phone, employee_no, employee_type, position, work_location, join_date, department_name, manager_id, dotted_manager_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        userId,
        body.get("name"),
        body.get("phone"),
        body.get("employeeNo"),
        body.get("employeeType"),
        body.get("position"),
        body.get("workLocation"),
        body.get("joinDate"),
        body.get("departmentName"),
        body.get("managerId"),
        body.get("dottedManagerId"));
    Object roleKey = body.get("roleKey");
    if (roleKey != null && !String.valueOf(roleKey).trim().isEmpty()) {
      setUserPrimaryRole(userId, String.valueOf(roleKey));
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("id", id);
    return out;
  }

  public void updateEmployee(String employeeId, Map<String, Object> body) {
    List<String> sets = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();
    appendSet(sets, args, "name", body.get("name"));
    appendSet(sets, args, "phone", body.get("phone"));
    appendSet(sets, args, "employee_no", body.get("employeeNo"));
    appendSet(sets, args, "employee_type", body.get("employeeType"));
    appendSet(sets, args, "position", body.get("position"));
    appendSet(sets, args, "work_location", body.get("workLocation"));
    appendSet(sets, args, "join_date", body.get("joinDate"));
    appendSet(sets, args, "department_name", body.get("departmentName"));
    if (body.containsKey("managerId")) {
      sets.add("manager_id = ?");
      args.add(body.get("managerId"));
    }
    if (body.containsKey("dottedManagerId")) {
      sets.add("dotted_manager_id = ?");
      args.add(body.get("dottedManagerId"));
    }
    if (!sets.isEmpty()) {
      args.add(employeeId);
      jdbc.update(
          "UPDATE employee_hierarchy SET " + String.join(", ", sets) + " WHERE employee_id = ?",
          args.toArray());
    }
    if (body.containsKey("roleKey")) {
      setUserPrimaryRole(employeeId, String.valueOf(body.get("roleKey")));
    }
  }

  private static void appendSet(List<String> sets, List<Object> args, String col, Object val) {
    if (val != null) {
      sets.add(col + " = ?");
      args.add(val);
    }
  }

  public void deleteEmployee(String employeeId) {
    jdbc.update("DELETE FROM user_role WHERE user_id = ?", employeeId);
    jdbc.update("DELETE FROM employee_hierarchy WHERE employee_id = ?", employeeId);
  }

  public void upsertEmployeeHierarchy(Map<String, Object> body) {
    String employeeId = String.valueOf(body.get("employeeId"));
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM employee_hierarchy WHERE employee_id = ?",
            Integer.class,
            employeeId);
    if (count != null && count > 0) {
      jdbc.update(
          "UPDATE employee_hierarchy SET manager_id = ?, dotted_manager_id = ?, department_id = ?, department_name = ? WHERE employee_id = ?",
          body.get("managerId"),
          body.get("dottedManagerId"),
          body.get("departmentId"),
          body.get("departmentName"),
          employeeId);
    } else {
      jdbc.update(
          "INSERT INTO employee_hierarchy (id, employee_id, manager_id, dotted_manager_id, department_id, department_name) VALUES (?,?,?,?,?,?)",
          UUID.randomUUID().toString(),
          employeeId,
          body.get("managerId"),
          body.get("dottedManagerId"),
          body.get("departmentId"),
          body.get("departmentName"));
    }
  }

  public void clearAllEmployees() {
    jdbc.update("DELETE FROM employee_hierarchy");
  }

  public Map<String, Object> syncFromLark(boolean clearExisting) {
    String appId = properties.getFeishu().getAppId();
    String appSecret = properties.getFeishu().getAppSecret();
    if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置飞书应用凭证");
    }
    if (clearExisting) {
      clearAllEmployees();
    }
    try {
      String tenantToken = fetchTenantAccessToken(appId, appSecret);
      JsonNode deptRes = feishuGet(
          "https://open.feishu.cn/open-apis/contact/v3/departments?page_size=50&fetch_child=true&department_id_type=open_department_id",
          tenantToken);
      JsonNode deptItems = deptRes.path("data").path("items");
      List<JsonNode> departments = new ArrayList<JsonNode>();
      if (deptItems.isArray()) {
        for (JsonNode n : deptItems) {
          departments.add(n);
        }
      }
      Set<String> userIdSet = new HashSet<String>();
      List<Map<String, Object>> allUsers = new ArrayList<Map<String, Object>>();
      int deptLimit = Math.min(departments.size(), 20);
      for (int i = 0; i < deptLimit; i++) {
        JsonNode dept = departments.get(i);
        String deptId = dept.path("department_id").asText("");
        if (deptId.isEmpty()) {
          continue;
        }
        String pageToken = null;
        do {
          StringBuilder url =
              new StringBuilder(
                  "https://open.feishu.cn/open-apis/contact/v3/users?department_id=");
          url.append(deptId);
          url.append("&page_size=50&user_id_type=open_id&department_id_type=open_department_id");
          if (pageToken != null) {
            url.append("&page_token=").append(pageToken);
          }
          JsonNode res = feishuGet(url.toString(), tenantToken);
          JsonNode items = res.path("data").path("items");
          if (items.isArray()) {
            for (JsonNode item : items) {
              String uid = item.path("open_id").asText("");
              if (uid.isEmpty()) {
                uid = item.path("user_id").asText("");
              }
              if (uid.isEmpty() || userIdSet.contains(uid)) {
                continue;
              }
              userIdSet.add(uid);
              Map<String, Object> u = new LinkedHashMap<String, Object>();
              u.put("userId", uid);
              u.put("name", item.path("name").asText(""));
              u.put("leaderUserId", item.path("leader_user_id").asText(null));
              JsonNode deptIds = item.path("department_ids");
              if (deptIds.isArray() && deptIds.size() > 0) {
                u.put("departmentId", deptIds.get(0).asText(""));
              }
              allUsers.add(u);
            }
          }
          pageToken = res.path("data").path("page_token").asText(null);
          if (pageToken != null && pageToken.isEmpty()) {
            pageToken = null;
          }
        } while (pageToken != null);
      }
      if (allUsers.isEmpty()) {
        Map<String, Object> empty = new LinkedHashMap<String, Object>();
        empty.put("count", 0);
        empty.put("totalCount", 0);
        empty.put("validCount", 0);
        empty.put("skippedCount", 0);
        return empty;
      }
      Set<String> existingIds = new HashSet<String>();
      if (!clearExisting) {
        jdbc.query(
            "SELECT employee_id FROM employee_hierarchy",
            (org.springframework.jdbc.core.RowCallbackHandler)
                rs -> existingIds.add(rs.getString("employee_id")));
      }
      int synced = 0;
      int validCount = allUsers.size();
      int skipped = 0;
      for (Map<String, Object> user : allUsers) {
        String uid = String.valueOf(user.get("userId"));
        if (!clearExisting && existingIds.contains(uid)) {
          skipped++;
          continue;
        }
        try {
          jdbc.update(
              "INSERT INTO employee_hierarchy (id, employee_id, manager_id, name, department_name) VALUES (?,?,?,?,?)",
              UUID.randomUUID().toString(),
              uid,
              user.get("leaderUserId"),
              user.get("name"),
              user.get("departmentId"));
          synced++;
        } catch (Exception ignored) {
          // skip single user failures
        }
      }
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("count", synced);
      out.put("totalCount", allUsers.size());
      out.put("validCount", validCount);
      out.put("skippedCount", skipped);
      return out;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  private static String fetchTenantAccessToken(String appId, String appSecret) throws Exception {
    JsonNode res =
        feishuPostJson(
            "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",
            "{\"app_id\":\""
                + escapeJson(appId)
                + "\",\"app_secret\":\""
                + escapeJson(appSecret)
                + "\"}",
            null);
    if (res.path("code").asInt(-1) != 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, res.path("msg").asText("飞书 tenant token 失败"));
    }
    return res.path("tenant_access_token").asText("");
  }

  private static JsonNode feishuGet(String url, String token) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", "Bearer " + token);
    return readJson(conn);
  }

  private static JsonNode feishuPostJson(String url, String body, String bearer) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    if (bearer != null) {
      conn.setRequestProperty("Authorization", "Bearer " + bearer);
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    try (OutputStream os = conn.getOutputStream()) {
      os.write(bytes);
    }
    return readJson(conn);
  }

  private static JsonNode readJson(HttpURLConnection conn) throws Exception {
    int code = conn.getResponseCode();
    BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      sb.append(line);
    }
    reader.close();
    return MAPPER.readTree(sb.toString());
  }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
