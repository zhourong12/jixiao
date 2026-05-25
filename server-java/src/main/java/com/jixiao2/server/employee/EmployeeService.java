package com.jixiao2.server.employee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiao2.server.feishu.FeishuRegistryService;
import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.orgdepartment.OrgDepartmentService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployeeService {

  private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);
  /** 飞书员工同步 / Directory 席位扫描诊断日志前缀 */
  private static final String FEISHU_SYNC_TRACE = "[feishu-sync]";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbc;
  private final FeishuRegistryService feishuRegistry;
  private final MenuPermissionService menuPermissionService;
  private final OrgDepartmentService orgDepartmentService;

  public EmployeeService(
      JdbcTemplate jdbc,
      FeishuRegistryService feishuRegistry,
      MenuPermissionService menuPermissionService,
      OrgDepartmentService orgDepartmentService) {
    this.jdbc = jdbc;
    this.feishuRegistry = feishuRegistry;
    this.menuPermissionService = menuPermissionService;
    this.orgDepartmentService = orgDepartmentService;
  }

  private static String jsonNodeForLog(JsonNode node, int maxLen) {
    if (node == null || node.isMissingNode()) {
      return "";
    }
    try {
      String s = MAPPER.writeValueAsString(node);
      if (s.length() > maxLen) {
        return s.substring(0, maxLen) + "...(" + s.length() + " chars)";
      }
      return s;
    } catch (Exception e) {
      return node.toString();
    }
  }

  /** 校验员工档案中的考核规则 id 存在且为启用（null / 空字符串跳过） */
  private void assertEnabledAssessmentRuleId(String ruleIdRaw) {
    if (ruleIdRaw == null) {
      return;
    }
    String id = ruleIdRaw.trim();
    if (id.isEmpty()) {
      return;
    }
    Integer ok =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM assessment_rule WHERE id = ? AND status = 'enabled'",
            Integer.class,
            id);
    if (ok == null || ok == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "考核规则不存在或已停用");
    }
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
    return getAllHierarchies(page, pageSize, keyword, null, null);
  }

  public Map<String, Object> getAllHierarchies(int page, int pageSize, String keyword, String subjectCode) {
    return getAllHierarchies(page, pageSize, keyword, subjectCode, null);
  }

  public Map<String, Object> getAllHierarchies(
      int page, int pageSize, String keyword, String subjectCode, String departmentId) {
    List<Object> args = new ArrayList<Object>();
    StringBuilder where = new StringBuilder("1=1");
    if (keyword != null && !keyword.trim().isEmpty()) {
      String like = "%" + keyword.trim() + "%";
      where.append(
          " AND (eh.name LIKE ? OR eh.phone LIKE ? OR eh.employee_no LIKE ? OR eh.position LIKE ? OR eh.department_name LIKE ?)");
      args.add(like);
      args.add(like);
      args.add(like);
      args.add(like);
      args.add(like);
    }
    if (subjectCode != null && !subjectCode.trim().isEmpty()) {
      String sid =
          feishuRegistry
              .findSubjectIdByCode(subjectCode.trim())
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
      where.append(
          " AND (eh.feishu_subject_id = ? OR (eh.feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = ? AND fs.code = 'default')))");
      args.add(sid);
      args.add(sid);
    }
    String deptId = departmentId == null ? "" : departmentId.trim();
    if (!deptId.isEmpty()) {
      if (orgDepartmentService.tableReady()) {
        List<String> deptParts = new ArrayList<String>();
        List<Object> deptArgs = new ArrayList<Object>();
        orgDepartmentService.appendEmployeeDepartmentFilter(
            deptParts, deptArgs, subjectCode, deptId, "eh");
        for (String part : deptParts) {
          where.append(" AND ").append(part);
        }
        args.addAll(deptArgs);
      } else {
        where.append(" AND (eh.department_id=? OR eh.department_name=?)");
        args.add(deptId);
        args.add(deptId);
      }
    }
    Integer total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM employee_hierarchy eh WHERE " + where,
            Integer.class,
            args.toArray());
    int offset = (page - 1) * pageSize;
    List<Object> listArgs = new ArrayList<Object>(args);
    listArgs.add(pageSize);
    listArgs.add(offset);
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT eh.employee_id, eh.manager_id, eh.dotted_manager_id, eh.department_id, eh.department_name, "
                + "eh.name, eh.phone, eh.employee_no, eh.employee_type, eh.position, eh.work_location, eh.join_date, "
                + "eh.feishu_subject_id, eh.feishu_open_id, eh.assessment_rule_id, ar.name AS assessment_rule_name, "
                + "fs.code AS feishu_subject_code, fs.name AS feishu_subject_name "
                + "FROM employee_hierarchy eh "
                + "LEFT JOIN feishu_subject fs ON fs.id = eh.feishu_subject_id "
                + "LEFT JOIN assessment_rule ar ON ar.id = eh.assessment_rule_id WHERE "
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
              m.put("feishuSubjectId", rs.getString("feishu_subject_id"));
              m.put("feishuOpenId", rs.getString("feishu_open_id"));
              m.put("assessmentRuleId", rs.getString("assessment_rule_id"));
              m.put("assessmentRuleName", rs.getString("assessment_rule_name"));
              m.put("feishuSubjectCode", rs.getString("feishu_subject_code"));
              m.put("feishuSubjectName", rs.getString("feishu_subject_name"));
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
    Map<String, String> managerNameMap = buildManagerNameLookup(managerIds);
    for (Map<String, Object> row : rows) {
      Object mid = row.get("managerId");
      if (mid != null) {
        String id = String.valueOf(mid).trim();
        row.put("managerName", sanitizePersonDisplayName(managerNameMap.get(id), id));
      }
      Object did = row.get("dottedManagerId");
      if (did != null) {
        String id = String.valueOf(did).trim();
        row.put("dottedManagerName", sanitizePersonDisplayName(managerNameMap.get(id), id));
      }
    }
    enrichItemsWithRoles(rows);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", rows);
    out.put("total", total == null ? 0 : total);
    return out;
  }

  /** 按 employee_id 与 feishu_open_id 解析上级/虚线上级姓名（未命中不返回 open_id 占位）。 */
  private Map<String, String> buildManagerNameLookup(Set<String> managerIds) {
    Map<String, String> managerNameMap = new HashMap<String, String>();
    if (managerIds == null || managerIds.isEmpty()) {
      return managerNameMap;
    }
    List<String> mids = new ArrayList<String>();
    for (String id : managerIds) {
      if (id != null && !id.trim().isEmpty()) {
        mids.add(id.trim());
      }
    }
    if (mids.isEmpty()) {
      return managerNameMap;
    }
    String placeholders = String.join(",", Collections.nCopies(mids.size(), "?"));
    List<Object> args = new ArrayList<Object>();
    args.addAll(mids);
    args.addAll(mids);
    jdbc.query(
        "SELECT employee_id, feishu_open_id, name FROM employee_hierarchy WHERE employee_id IN ("
            + placeholders
            + ") OR feishu_open_id IN ("
            + placeholders
            + ")",
        args.toArray(),
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> {
              String name = rs.getString("name");
              if (name == null || name.trim().isEmpty()) {
                return;
              }
              String trimmedName = name.trim();
              if (looksLikeFeishuOpenId(trimmedName)) {
                return;
              }
              String employeeId = rs.getString("employee_id");
              if (employeeId != null && !employeeId.trim().isEmpty()) {
                managerNameMap.put(employeeId.trim(), trimmedName);
              }
              String openId = rs.getString("feishu_open_id");
              if (openId != null && !openId.trim().isEmpty()) {
                managerNameMap.put(openId.trim(), trimmedName);
              }
            });
    return managerNameMap;
  }

  /** 不把 open_id 或纯 ID 当作展示姓名。 */
  private static String sanitizePersonDisplayName(String name, String id) {
    if (name == null || name.trim().isEmpty()) {
      return null;
    }
    String n = name.trim();
    if (looksLikeFeishuOpenId(n)) {
      return null;
    }
    if (id != null && n.equals(id.trim())) {
      return null;
    }
    return n;
  }

  /**
   * 飞书同步后：将 manager_id / dotted_manager_id 规范为同主体内已存在员工的 employee_id，便于列表解析姓名。
   */
  private int normalizeManagerIdsInSubject(String subjectId) {
    int direct =
        jdbc.update(
            "UPDATE employee_hierarchy eh "
                + "INNER JOIN employee_hierarchy mgr ON mgr.feishu_subject_id = eh.feishu_subject_id "
                + "AND mgr.employee_id <> eh.employee_id "
                + "AND (mgr.feishu_open_id = eh.manager_id OR mgr.employee_id = eh.manager_id) "
                + "SET eh.manager_id = mgr.employee_id "
                + "WHERE eh.feishu_subject_id = ? AND eh.manager_id IS NOT NULL AND TRIM(eh.manager_id) <> ''",
            subjectId);
    int dotted =
        jdbc.update(
            "UPDATE employee_hierarchy eh "
                + "INNER JOIN employee_hierarchy mgr ON mgr.feishu_subject_id = eh.feishu_subject_id "
                + "AND mgr.employee_id <> eh.employee_id "
                + "AND (mgr.feishu_open_id = eh.dotted_manager_id OR mgr.employee_id = eh.dotted_manager_id) "
                + "SET eh.dotted_manager_id = mgr.employee_id "
                + "WHERE eh.feishu_subject_id = ? AND eh.dotted_manager_id IS NOT NULL AND TRIM(eh.dotted_manager_id) <> ''",
            subjectId);
    return direct + dotted;
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
    return listDepartmentOptions(null);
  }

  /**
   * 按飞书主体分组的部门树（筛选用）：一级为主体，二级为该主体下员工出现的部门。
   */
  public List<Map<String, Object>> listDepartmentTree() {
    if (orgDepartmentService.tableReady()) {
      List<Map<String, Object>> fromOrg = orgDepartmentService.listDepartmentTree();
      if (!fromOrg.isEmpty()) {
        return fromOrg;
      }
    }
    List<Map<String, Object>> subjects =
        jdbc.query(
            "SELECT id, code, name FROM feishu_subject WHERE enabled = 1 ORDER BY sort_order ASC, code ASC",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("code", rs.getString("code"));
              m.put("name", rs.getString("name"));
              return m;
            });
    RowMapper<Map<String, Object>> deptMapper =
        (rs, rn) -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("id", rs.getString("id"));
          m.put("name", rs.getString("name"));
          return m;
        };
    String deptSql =
        "SELECT DISTINCT"
            + " COALESCE(NULLIF(TRIM(eh.department_id), ''), TRIM(eh.department_name)) AS id,"
            + " COALESCE(NULLIF(TRIM(eh.department_name), ''), TRIM(eh.department_id)) AS name"
            + " FROM employee_hierarchy eh"
            + " WHERE ((eh.department_id IS NOT NULL AND TRIM(eh.department_id) <> '')"
            + " OR (eh.department_name IS NOT NULL AND TRIM(eh.department_name) <> ''))"
            + " AND (eh.feishu_subject_id = ? OR (eh.feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = ? AND fs.code = 'default')))"
            + " ORDER BY name ASC";
    List<Map<String, Object>> tree = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> subject : subjects) {
      String sid = String.valueOf(subject.get("id"));
      List<Map<String, Object>> departments = jdbc.query(deptSql, deptMapper, sid, sid);
      departments = dedupeDepartmentsByDisplayName(departments);
      if (departments.isEmpty()) {
        continue;
      }
      Map<String, Object> node = new LinkedHashMap<String, Object>();
      node.put("subjectCode", subject.get("code"));
      node.put("subjectName", subject.get("name"));
      node.put("departments", departments);
      tree.add(node);
    }
    return tree;
  }

  /** 同一主体下按部门展示名去重（避免 department_id 不同但名称相同出现多条）。筛选用 name 作为 id。 */
  private static List<Map<String, Object>> dedupeDepartmentsByDisplayName(List<Map<String, Object>> rows) {
    Map<String, Map<String, Object>> byName = new LinkedHashMap<String, Map<String, Object>>();
    for (Map<String, Object> row : rows) {
      String name =
          row.get("name") == null
              ? ""
              : String.valueOf(row.get("name")).trim();
      if (name.isEmpty() && row.get("id") != null) {
        name = String.valueOf(row.get("id")).trim();
      }
      if (name.isEmpty()) {
        continue;
      }
      String key = name.toLowerCase();
      if (byName.containsKey(key)) {
        continue;
      }
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("id", name);
      item.put("name", name);
      byName.put(key, item);
    }
    return new ArrayList<Map<String, Object>>(byName.values());
  }

  public List<Map<String, Object>> listDepartmentOptions(String subjectCode) {
    RowMapper<Map<String, Object>> rowMapper =
        (rs, rn) -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("id", rs.getString("id"));
          m.put("name", rs.getString("name"));
          return m;
        };
    List<Map<String, Object>> rows;
    if (subjectCode == null || subjectCode.trim().isEmpty()) {
      rows =
          jdbc.query(
              "SELECT DISTINCT"
                  + " COALESCE(NULLIF(TRIM(department_id), ''), TRIM(department_name)) AS id,"
                  + " COALESCE(NULLIF(TRIM(department_name), ''), TRIM(department_id)) AS name"
                  + " FROM employee_hierarchy"
                  + " WHERE (department_id IS NOT NULL AND TRIM(department_id) <> '')"
                  + " OR (department_name IS NOT NULL AND TRIM(department_name) <> '')",
              rowMapper);
    } else {
      String sid =
          feishuRegistry
              .findSubjectIdByCode(subjectCode.trim())
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
      rows =
          jdbc.query(
              "SELECT DISTINCT"
                  + " COALESCE(NULLIF(TRIM(eh.department_id), ''), TRIM(eh.department_name)) AS id,"
                  + " COALESCE(NULLIF(TRIM(eh.department_name), ''), TRIM(eh.department_id)) AS name"
                  + " FROM employee_hierarchy eh"
                  + " WHERE ((eh.department_id IS NOT NULL AND TRIM(eh.department_id) <> '')"
                  + " OR (eh.department_name IS NOT NULL AND TRIM(eh.department_name) <> ''))"
                  + " AND (eh.feishu_subject_id = ? OR (eh.feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = ? AND fs.code = 'default')))",
              rowMapper,
              sid,
              sid);
    }
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
    String reqEmployeeNo =
        body.get("employeeNo") == null ? "" : String.valueOf(body.get("employeeNo")).trim();
    String reqDepartmentName =
        body.get("departmentName") == null ? "" : String.valueOf(body.get("departmentName")).trim();
    Object feishuSubjectCodeObj = body.get("feishuSubjectCode");
    Object feishuSubjectIdInBody = body.get("feishuSubjectId");
    Object feishuOpenIdObj = body.get("feishuOpenId");
    String feishuOpenIdForLog =
        feishuOpenIdObj == null ? "" : String.valueOf(feishuOpenIdObj).trim();
    Object phoneObj = body.get("phone");
    boolean phoneBlank =
        phoneObj == null || String.valueOf(phoneObj).trim().isEmpty();
    log.info(
        "createEmployee 请求体摘要 userId={} name={} employeeNo=[{}] departmentName=[{}] "
            + "feishuSubjectCode={} feishuSubjectId={} feishuOpenId={} roleKey={} employeeType={} phoneBlank={} bodyKeys={}",
        userId,
        body.get("name"),
        reqEmployeeNo,
        reqDepartmentName,
        feishuSubjectCodeObj,
        feishuSubjectIdInBody,
        feishuOpenIdForLog,
        body.get("roleKey"),
        body.get("employeeType"),
        phoneBlank,
        body.keySet());
    Optional<String> subjectOpt = resolveFeishuSubjectIdFromBody(body);
    String feishuSubjectId = subjectOpt.map(String::trim).orElse(null);
    log.info(
        "createEmployee 主体解析结果 feishuSubjectId={} (来自 body 的 id/code 解析，可能为 null)",
        feishuSubjectId);
    String feishuOpenId =
        feishuOpenIdObj != null && !String.valueOf(feishuOpenIdObj).trim().isEmpty()
            ? String.valueOf(feishuOpenIdObj).trim()
            : null;
    Object assessmentRuleObj = body.get("assessmentRuleId");
    if (assessmentRuleObj == null) {
      assessmentRuleObj = body.get("assessment_rule_id");
    }
    String assessmentRuleId =
        assessmentRuleObj == null ? null : String.valueOf(assessmentRuleObj).trim();
    if (assessmentRuleId != null && assessmentRuleId.isEmpty()) {
      assessmentRuleId = null;
    }
    assertEnabledAssessmentRuleId(assessmentRuleId);
    applyDepartmentBinding(body);
    reqDepartmentName =
        body.get("departmentName") == null ? "" : String.valueOf(body.get("departmentName")).trim();
    if (reqEmployeeNo.isEmpty() || reqDepartmentName.isEmpty()) {
      log.warn(
          "createEmployee 工号或部门在请求中为空 employeeNoEmpty={} departmentNameEmpty={} userId={} feishuOpenId={}",
          reqEmployeeNo.isEmpty(),
          reqDepartmentName.isEmpty(),
          userId,
          feishuOpenId);
    }
    jdbc.update(
        "INSERT INTO employee_hierarchy (id, employee_id, name, phone, employee_no, employee_type, position, work_location, join_date, department_id, department_name, manager_id, dotted_manager_id, feishu_subject_id, feishu_open_id, assessment_rule_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        userId,
        body.get("name"),
        body.get("phone"),
        body.get("employeeNo"),
        body.get("employeeType"),
        body.get("position"),
        body.get("workLocation"),
        body.get("joinDate"),
        body.get("departmentId"),
        body.get("departmentName"),
        body.get("managerId"),
        body.get("dottedManagerId"),
        feishuSubjectId,
        feishuOpenId,
        assessmentRuleId);
    log.info(
        "createEmployee 已写入 employee_hierarchy id={} employee_id={} employee_no=[{}] department_name=[{}] feishu_subject_id={} feishu_open_id={}",
        id,
        userId,
        body.get("employeeNo"),
        body.get("departmentName"),
        feishuSubjectId,
        feishuOpenId);
    Object roleKey = body.get("roleKey");
    if (roleKey != null && !String.valueOf(roleKey).trim().isEmpty()) {
      setUserPrimaryRole(userId, String.valueOf(roleKey));
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("id", id);
    return out;
  }

  public void updateEmployee(String employeeId, Map<String, Object> body) {
    if (body.containsKey("departmentId") || body.containsKey("departmentName")) {
      applyDepartmentBinding(body);
    }
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
    if (body.containsKey("departmentId")) {
      sets.add("department_id = ?");
      args.add(body.get("departmentId"));
    }
    if (body.containsKey("managerId")) {
      sets.add("manager_id = ?");
      args.add(body.get("managerId"));
    }
    if (body.containsKey("dottedManagerId")) {
      sets.add("dotted_manager_id = ?");
      args.add(body.get("dottedManagerId"));
    }
    if (body.containsKey("feishuSubjectId")) {
      sets.add("feishu_subject_id = ?");
      Object v = body.get("feishuSubjectId");
      args.add(v == null || String.valueOf(v).trim().isEmpty() ? null : String.valueOf(v).trim());
    } else if (body.containsKey("feishuSubjectCode")) {
      Object sc = body.get("feishuSubjectCode");
      if (sc == null || String.valueOf(sc).trim().isEmpty()) {
        sets.add("feishu_subject_id = ?");
        args.add(null);
      } else {
        String sid =
            feishuRegistry
                .findSubjectIdByCode(String.valueOf(sc).trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在"));
        sets.add("feishu_subject_id = ?");
        args.add(sid);
      }
    }
    if (body.containsKey("feishuOpenId")) {
      sets.add("feishu_open_id = ?");
      Object v = body.get("feishuOpenId");
      args.add(v == null || String.valueOf(v).trim().isEmpty() ? null : String.valueOf(v).trim());
    }
    if (body.containsKey("assessmentRuleId") || body.containsKey("assessment_rule_id")) {
      Object v = body.containsKey("assessmentRuleId") ? body.get("assessmentRuleId") : body.get("assessment_rule_id");
      String rid = v == null ? "" : String.valueOf(v).trim();
      if (rid.isEmpty()) {
        sets.add("assessment_rule_id = ?");
        args.add(null);
      } else {
        assertEnabledAssessmentRuleId(rid);
        sets.add("assessment_rule_id = ?");
        args.add(rid);
      }
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

  /** 批量更新员工档案中的考核规则；assessmentRuleId 为空字符串时清空绑定。 */
  public Map<String, Object> batchUpdateAssessmentRule(List<String> employeeIds, String assessmentRuleIdRaw) {
    List<String> ids = new ArrayList<String>();
    if (employeeIds != null) {
      for (String id : employeeIds) {
        if (id != null && !id.trim().isEmpty() && !ids.contains(id.trim())) {
          ids.add(id.trim());
        }
      }
    }
    if (ids.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择员工");
    }
    String rid = assessmentRuleIdRaw == null ? "" : assessmentRuleIdRaw.trim();
    String ruleId = null;
    if (!rid.isEmpty()) {
      assertEnabledAssessmentRuleId(rid);
      ruleId = rid;
    }
    String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
    List<Object> args = new ArrayList<Object>();
    args.add(ruleId);
    args.addAll(ids);
    int updated =
        jdbc.update(
            "UPDATE employee_hierarchy SET assessment_rule_id = ? WHERE employee_id IN ("
                + placeholders
                + ")",
            args.toArray());
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", Boolean.TRUE);
    out.put("updatedCount", updated);
    return out;
  }

  private Optional<String> resolveFeishuSubjectIdFromBody(Map<String, Object> body) {
    Object sid = body.get("feishuSubjectId");
    if (sid != null && !String.valueOf(sid).trim().isEmpty()) {
      return Optional.of(String.valueOf(sid).trim());
    }
    Object sc = body.get("feishuSubjectCode");
    if (sc != null && !String.valueOf(sc).trim().isEmpty()) {
      return feishuRegistry.findSubjectIdByCode(String.valueOf(sc).trim());
    }
    return Optional.empty();
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
      boolean touchDepartment =
          body.containsKey("departmentName") || body.containsKey("departmentId");
      if (touchDepartment) {
        jdbc.update(
            "UPDATE employee_hierarchy SET manager_id = ?, dotted_manager_id = ?, department_id = ?, department_name = ? WHERE employee_id = ?",
            body.get("managerId"),
            body.get("dottedManagerId"),
            body.get("departmentId"),
            body.get("departmentName"),
            employeeId);
      } else {
        jdbc.update(
            "UPDATE employee_hierarchy SET manager_id = ?, dotted_manager_id = ? WHERE employee_id = ?",
            body.get("managerId"),
            body.get("dottedManagerId"),
            employeeId);
      }
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

  /** 部门数过多时限制扫描次数，避免单次请求时间过长。 */
  private static final int MAX_DEPARTMENTS_FOR_USER_SCAN = 400;

  private static String departmentNodeDisplayName(JsonNode deptNode) {
    if (deptNode == null || deptNode.isMissingNode()) {
      return "";
    }
    String name = deptNode.path("name").asText("").trim();
    if (!name.isEmpty()) {
      return name;
    }
    JsonNode i18n = deptNode.path("i18n_name");
    if (i18n.isObject()) {
      String zh = i18n.path("zh_cn").asText("").trim();
      if (!zh.isEmpty()) {
        return zh;
      }
      String en = i18n.path("en_us").asText("").trim();
      if (!en.isEmpty()) {
        return en;
      }
    }
    return "";
  }

  /** 通讯录用户列表/详情里 {@code department_path[0].department_name} 的展示名（不依赖部门 ID 映射表）。 */
  private static String userDepartmentNameFromItem(JsonNode userItem) {
    if (userItem == null || userItem.isMissingNode()) {
      return "";
    }
    JsonNode path = userItem.path("department_path");
    if (!path.isArray() || path.size() == 0) {
      return "";
    }
    return departmentNodeDisplayName(path.get(0).path("department_name"));
  }

  private static String userEmployeeNoText(JsonNode userItem) {
    if (userItem == null || userItem.isMissingNode()) {
      return "";
    }
    JsonNode n = userItem.path("employee_no");
    if (!n.isMissingNode() && !n.isNull()) {
      String raw = n.isNumber() ? n.asText("").trim() : n.asText("").trim();
      if (!raw.isEmpty()) {
        return raw;
      }
    }
    JsonNode jobNo = userItem.path("job_number");
    if (!jobNo.isMissingNode() && !jobNo.isNull()) {
      String raw = jobNo.asText("").trim();
      if (!raw.isEmpty()) {
        return raw;
      }
    }
    return "";
  }

  /**
   * 拉取根下全部部门分页（含 {@code fetch_child}），再按部门拉用户并去重，用于同步与选人「全量」列表。
   *
   * <p>部门 ID 在 {@code department_id_type=open_department_id} 下须使用 {@code open_department_id} 调用户列表。
   */
  private List<JsonNode> fetchAllDepartmentNodes(String tenantToken) throws Exception {
    List<JsonNode> departments = new ArrayList<JsonNode>();
    String deptPageToken = null;
    do {
      StringBuilder deptUrl =
          new StringBuilder(
              "https://open.feishu.cn/open-apis/contact/v3/departments?parent_department_id=0&page_size=50&fetch_child=true&department_id_type=open_department_id");
      if (deptPageToken != null) {
        deptUrl.append("&page_token=").append(deptPageToken);
      }
      JsonNode deptRes = feishuGet(deptUrl.toString(), tenantToken);
      int deptCode = deptRes.path("code").asInt(-1);
      if (deptCode != 0) {
        throw new Exception(
            "飞书部门列表失败: " + deptRes.path("msg").asText("") + " (code=" + deptCode + ")");
      }
      JsonNode deptItems = deptRes.path("data").path("items");
      if (deptItems.isArray()) {
        for (JsonNode n : deptItems) {
          departments.add(n);
        }
      }
      if (!deptRes.path("data").path("has_more").asBoolean(false)) {
        break;
      }
      deptPageToken = deptRes.path("data").path("page_token").asText(null);
      if (deptPageToken != null && deptPageToken.isEmpty()) {
        deptPageToken = null;
      }
    } while (deptPageToken != null);
    return departments;
  }

  private static Map<String, String> buildDeptOpenIdToName(List<JsonNode> departments) {
    Map<String, String> deptOpenIdToName = new LinkedHashMap<String, String>();
    for (JsonNode d : departments) {
      String oid = departmentOpenId(d);
      if (oid.isEmpty()) {
        continue;
      }
      String nm = departmentNodeDisplayName(d);
      if (!nm.isEmpty()) {
        deptOpenIdToName.put(oid, nm);
      }
    }
    return deptOpenIdToName;
  }

  private static String departmentOpenId(JsonNode dept) {
    if (dept == null || dept.isMissingNode()) {
      return "";
    }
    String oid = dept.path("open_department_id").asText("").trim();
    if (oid.isEmpty()) {
      oid = dept.path("department_id").asText("").trim();
    }
    return oid;
  }

  /** 顶层部门 open_department_id（用于 find_by_department + fetch_child 拉全员）。 */
  private static List<String> resolveRootOpenDepartmentIds(List<JsonNode> departments) {
    Set<String> allOpenIds = new HashSet<String>();
    for (JsonNode d : departments) {
      String oid = departmentOpenId(d);
      if (!oid.isEmpty()) {
        allOpenIds.add(oid);
      }
    }
    List<String> roots = new ArrayList<String>();
    for (JsonNode d : departments) {
      String oid = departmentOpenId(d);
      if (oid.isEmpty()) {
        continue;
      }
      String parent = d.path("parent_department_id").asText("").trim();
      if (parent.isEmpty() || "0".equals(parent) || !allOpenIds.contains(parent)) {
        roots.add(oid);
      }
    }
    if (roots.isEmpty() && !departments.isEmpty()) {
      String oid = departmentOpenId(departments.get(0));
      if (!oid.isEmpty()) {
        roots.add(oid);
      }
    }
    return roots;
  }

  private void appendUsersFromContactResponse(
      JsonNode items,
      String fallbackDeptId,
      Map<String, String> deptOpenIdToName,
      JsonNode fallbackDeptNode,
      Set<String> userIdSet,
      List<Map<String, Object>> allUsers) {
    if (items == null || !items.isArray()) {
      return;
    }
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
      u.put("employeeNo", userEmployeeNoText(item));
      u.put("leaderUserId", item.path("leader_user_id").asText(null));
      JsonNode dottedLeaders = item.path("dotted_line_leader_user_ids");
      if (dottedLeaders.isArray() && dottedLeaders.size() > 0) {
        u.put("dottedLeaderUserId", dottedLeaders.get(0).asText(null));
      }
      JsonNode deptIds = item.path("department_ids");
      if (deptIds.isArray() && deptIds.size() > 0) {
        u.put("departmentId", deptIds.get(0).asText(""));
      }
      String deptLabel = userDepartmentNameFromItem(item);
      if (deptLabel.isEmpty() && fallbackDeptNode != null) {
        deptLabel = departmentNodeDisplayName(fallbackDeptNode);
      }
      if (deptLabel.isEmpty()) {
        deptLabel = deptOpenIdToName.getOrDefault(fallbackDeptId, "");
      }
      if (deptLabel.isEmpty() && deptIds.isArray()) {
        for (JsonNode idNode : deptIds) {
          String did = idNode.asText("").trim();
          if (did.isEmpty()) {
            continue;
          }
          deptLabel = deptOpenIdToName.getOrDefault(did, "");
          if (!deptLabel.isEmpty()) {
            break;
          }
        }
      }
      u.put("departmentName", deptLabel);
      allUsers.add(u);
    }
  }

  /**
   * 按部门拉用户；{@code fetchChild=true} 时含子部门成员（用于根部门拉全员）。
   */
  private void fetchUsersFindByDepartment(
      String tenantToken,
      String deptId,
      String departmentIdType,
      boolean fetchChild,
      Map<String, String> deptOpenIdToName,
      JsonNode fallbackDeptNode,
      Set<String> userIdSet,
      List<Map<String, Object>> allUsers)
      throws Exception {
    String pageToken = null;
    do {
      StringBuilder url =
          new StringBuilder(
              "https://open.feishu.cn/open-apis/contact/v3/users/find_by_department");
      url.append("?department_id=").append(URLEncoder.encode(deptId, StandardCharsets.UTF_8.name()));
      url.append("&department_id_type=")
          .append(URLEncoder.encode(departmentIdType, StandardCharsets.UTF_8.name()));
      url.append("&page_size=50&user_id_type=open_id");
      if (fetchChild) {
        url.append("&fetch_child=true");
      }
      if (pageToken != null) {
        url.append("&page_token=").append(pageToken);
      }
      JsonNode res = feishuGet(url.toString(), tenantToken);
      if (res.path("code").asInt(0) != 0) {
        throw new Exception(
            "飞书 find_by_department 失败 dept="
                + deptId
                + " fetchChild="
                + fetchChild
                + ": "
                + res.path("msg").asText("")
                + " (code="
                + res.path("code").asInt()
                + ")");
      }
      appendUsersFromContactResponse(
          res.path("data").path("items"),
          deptId,
          deptOpenIdToName,
          fallbackDeptNode,
          userIdSet,
          allUsers);
      pageToken = res.path("data").path("page_token").asText(null);
      if (pageToken != null && pageToken.isEmpty()) {
        pageToken = null;
      }
    } while (pageToken != null);
  }

  /** 按部门拉「直属」成员（与历史 sync-from-lark 一致）。 */
  private void fetchUsersByDepartmentDirect(
      String tenantToken,
      String deptId,
      JsonNode deptNode,
      Map<String, String> deptOpenIdToName,
      Set<String> userIdSet,
      List<Map<String, Object>> allUsers)
      throws Exception {
    String pageToken = null;
    do {
      StringBuilder url =
          new StringBuilder("https://open.feishu.cn/open-apis/contact/v3/users?department_id=");
      url.append(URLEncoder.encode(deptId, StandardCharsets.UTF_8.name()));
      url.append("&page_size=50&user_id_type=open_id&department_id_type=open_department_id");
      if (pageToken != null) {
        url.append("&page_token=").append(pageToken);
      }
      JsonNode res = feishuGet(url.toString(), tenantToken);
      if (res.path("code").asInt(0) != 0) {
        throw new Exception(
            "飞书用户列表失败 dept="
                + deptId
                + ": "
                + res.path("msg").asText("")
                + " (code="
                + res.path("code").asInt()
                + ")");
      }
      appendUsersFromContactResponse(
          res.path("data").path("items"), deptId, deptOpenIdToName, deptNode, userIdSet, allUsers);
      pageToken = res.path("data").path("page_token").asText(null);
      if (pageToken != null && pageToken.isEmpty()) {
        pageToken = null;
      }
    } while (pageToken != null);
  }

  private List<Map<String, Object>> fetchLarkContactUsers(String appId, String appSecret) throws Exception {
    String tenantToken = fetchTenantAccessToken(appId, appSecret);
    List<JsonNode> departments = fetchAllDepartmentNodes(tenantToken);
    Map<String, String> deptOpenIdToName = buildDeptOpenIdToName(departments);
    Set<String> userIdSet = new HashSet<String>();
    List<Map<String, Object>> allUsers = new ArrayList<Map<String, Object>>();

    List<String> rootDeptIds = resolveRootOpenDepartmentIds(departments);
    for (String rootId : rootDeptIds) {
      try {
        fetchUsersFindByDepartment(
            tenantToken,
            rootId,
            "open_department_id",
            true,
            deptOpenIdToName,
            null,
            userIdSet,
            allUsers);
      } catch (Exception ex) {
        log.warn("飞书通讯录根部门 find_by_department 失败 rootDeptId={} appId={} {}", rootId, appId, ex.getMessage());
      }
    }

    int deptLimit = Math.min(departments.size(), MAX_DEPARTMENTS_FOR_USER_SCAN);
    if (departments.size() > deptLimit) {
      log.warn(
          "飞书通讯录部门数 {} 超过扫描上限 {}，将截断按部门拉直属成员 appId={}",
          departments.size(),
          deptLimit,
          appId);
    }
    for (int i = 0; i < deptLimit; i++) {
      JsonNode dept = departments.get(i);
      String deptId = departmentOpenId(dept);
      if (deptId.isEmpty()) {
        continue;
      }
      try {
        fetchUsersByDepartmentDirect(
            tenantToken, deptId, dept, deptOpenIdToName, userIdSet, allUsers);
      } catch (Exception ex) {
        log.warn("飞书通讯录按部门拉直属成员失败 deptId={} appId={} {}", deptId, appId, ex.getMessage());
      }
    }

    log.info(
        "fetchLarkContactUsers appId={} deptCount={} deptScanned={} rootDeptCount={} userCount={}",
        appId,
        departments.size(),
        deptLimit,
        rootDeptIds.size(),
        allUsers.size());
    Collections.sort(
        allUsers,
        (a, b) ->
            String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
    return allUsers;
  }

  /** 选人接口单次返回人数上限（防止响应体过大）；全量在 {@link #fetchLarkContactUsers} 内已尽量拉全。 */
  private static final int MAX_FEISHU_USER_OPTIONS_RETURN = 8000;

  public Map<String, Object> listFeishuUserOptions(String subjectCode) {
    if (subjectCode == null || subjectCode.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 subjectCode");
    }
    String trimmedCode = subjectCode.trim();
    String subjectId =
        feishuRegistry
            .findSubjectIdByCode(trimmedCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
    FeishuRegistryService.FeishuImAppRow app =
        feishuRegistry
            .findDirectoryAppForSubjectId(subjectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该主体未配置通讯录用飞书应用"));
    try {
      List<Map<String, Object>> allUsers = fetchLarkContactUsers(app.getAppId(), app.getAppSecret());
      List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
      int n = 0;
      int emptyEmpNo = 0;
      int emptyDept = 0;
      for (Map<String, Object> u : allUsers) {
        if (n >= MAX_FEISHU_USER_OPTIONS_RETURN) {
          break;
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("feishuOpenId", u.get("userId"));
        row.put("name", u.get("name"));
        row.put("employeeNo", u.get("employeeNo"));
        row.put("departmentName", u.get("departmentName"));
        row.put("feishuSubjectCode", trimmedCode);
        String en = u.get("employeeNo") == null ? "" : String.valueOf(u.get("employeeNo")).trim();
        String dn = u.get("departmentName") == null ? "" : String.valueOf(u.get("departmentName")).trim();
        if (en.isEmpty()) {
          emptyEmpNo++;
        }
        if (dn.isEmpty()) {
          emptyDept++;
        }
        items.add(row);
        n++;
      }
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("items", items);
      out.put("total", allUsers.size());
      out.put("truncated", allUsers.size() > MAX_FEISHU_USER_OPTIONS_RETURN);
      log.info(
          "listFeishuUserOptions subjectCode={} subjectId={} appId={} 通讯录总人数={} 本次返回条数={} truncated={} "
              + "本批中空工号条数={} 本批中空部门条数={}",
          trimmedCode,
          subjectId,
          app.getAppId(),
          allUsers.size(),
          items.size(),
          allUsers.size() > MAX_FEISHU_USER_OPTIONS_RETURN,
          emptyEmpNo,
          emptyDept);
      return out;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /**
   * 按 open_id 拉取飞书用户详情（工号、主部门名称等），用于选人后补全表单；依赖通讯录应用 token。
   */
  public Map<String, Object> fetchFeishuUserProfile(String subjectCode, String openId) {
    String trimmedCode = subjectCode == null ? "" : subjectCode.trim();
    String oid = openId == null ? "" : openId.trim();
    if (trimmedCode.isEmpty() || oid.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 subjectCode 或 openId");
    }
    String subjectId =
        feishuRegistry
            .findSubjectIdByCode(trimmedCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
    FeishuRegistryService.FeishuImAppRow app =
        feishuRegistry
            .findDirectoryAppForSubjectId(subjectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该主体未配置通讯录用飞书应用"));
    try {
      String token = fetchTenantAccessToken(app.getAppId(), app.getAppSecret());
      String userUrl =
          "https://open.feishu.cn/open-apis/contact/v3/users/"
              + URLEncoder.encode(oid, StandardCharsets.UTF_8.name())
              + "?user_id_type=open_id&department_id_type=open_department_id";
      JsonNode res = feishuGet(userUrl, token);
      log.info(
          "fetchFeishuUserProfile subjectCode={} subjectId={} directoryAppId={} openId={} feishuCode={}",
          trimmedCode,
          subjectId,
          app.getAppId(),
          oid,
          res.path("code").asInt(-1));
      if (res.path("code").asInt(0) != 0) {
        log.warn(
            "fetchFeishuUserProfile 飞书返回错误 code={} msg={} response={}",
            res.path("code").asInt(-1),
            res.path("msg").asText(""),
            jsonNodeForLog(res, 12000));
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "飞书用户信息: " + res.path("msg").asText(""));
      }
      JsonNode user = res.path("data").path("user");
      JsonNode deptIdsNode = user.path("department_ids");
      int deptIdsSize = deptIdsNode.isArray() ? deptIdsNode.size() : -1;
      JsonNode deptPathNode = user.path("department_path");
      int deptPathSize = deptPathNode.isArray() ? deptPathNode.size() : -1;
      log.info(
          "fetchFeishuUserProfile 飞书 user 原始字段摘要 openId={} employee_no节点={} department_ids_size={} department_path_size={}",
          oid,
          user.path("employee_no").toString(),
          deptIdsSize,
          deptPathSize);
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      String empNo = userEmployeeNoText(user);
      out.put("employeeNo", empNo);
      out.put("mobile", user.path("mobile").asText(""));
      String deptName = userDepartmentNameFromItem(user);
      if (deptName.isEmpty() && deptIdsNode.isArray() && deptIdsNode.size() > 0) {
        String firstDept = deptIdsNode.get(0).asText("").trim();
        if (!firstDept.isEmpty()) {
          try {
            String deptUrl =
                "https://open.feishu.cn/open-apis/contact/v3/departments/"
                    + URLEncoder.encode(firstDept, StandardCharsets.UTF_8.name())
                    + "?department_id_type=open_department_id";
            JsonNode dres = feishuGet(deptUrl, token);
            if (dres.path("code").asInt(0) == 0) {
              JsonNode department = dres.path("data").path("department");
              deptName = departmentNodeDisplayName(department);
            } else {
              log.warn(
                  "fetchFeishuUserProfile 部门查询失败 deptId={} code={} msg={} response={}",
                  firstDept,
                  dres.path("code").asInt(-1),
                  dres.path("msg").asText(""),
                  jsonNodeForLog(dres, 8000));
            }
          } catch (Exception ex) {
            log.warn("fetchFeishuUserProfile 部门查询异常 deptId={}", firstDept, ex);
          }
        }
      }
      log.info(
          "fetchFeishuUserProfile 解析结果 openId={} employeeNo=[{}] departmentName=[{}] (department_path 首段后仍空则已尝试 department_ids 查部门)",
          oid,
          empNo,
          deptName);
      if (empNo.isEmpty() || deptName.isEmpty()) {
        log.warn(
            "fetchFeishuUserProfile 工号或部门仍为空（飞书本条 code=0 时接口未报错，多为字段未返回或权限/可见范围问题）subjectCode={} directoryAppId={} openId={} employeeNoEmpty={} departmentNameEmpty={} department_ids_size={} department_path_size={} feishuResponse={}",
            trimmedCode,
            app.getAppId(),
            oid,
            empNo.isEmpty(),
            deptName.isEmpty(),
            deptIdsSize,
            deptPathSize,
            jsonNodeForLog(res, 12000));
      }
      out.put("departmentName", deptName);
      return out;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.error("fetchFeishuUserProfile 异常 subjectCode={} openId={}", trimmedCode, oid, e);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  /** 可执行通讯录同步的已启用主体（含登录应用凭证）。 */
  public List<Map<String, Object>> listSyncableSubjects() {
    return jdbc.query(
        "SELECT s.code, s.name FROM feishu_subject s "
            + "WHERE s.enabled = 1 AND EXISTS ("
            + "SELECT 1 FROM feishu_app a WHERE a.feishu_subject_id = s.id AND a.enabled = 1 AND a.is_login_app = 1 "
            + "AND TRIM(a.app_id) <> '' AND TRIM(a.app_secret) <> '') "
            + "ORDER BY s.sort_order ASC, s.code ASC",
        (rs, rn) -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("code", rs.getString("code"));
          m.put("name", rs.getString("name"));
          return m;
        });
  }

  /**
   * 按主体从飞书通讯录 upsert 员工（新增 + 更新）。多主体请用 {@link #syncAllSubjectsFromFeishu}。
   */
  public Map<String, Object> syncSubjectFromFeishu(String subjectCode) {
    String trimmedCode = subjectCode == null ? "" : subjectCode.trim();
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("subjectCode", trimmedCode);
    out.put("createdCount", 0);
    out.put("updatedCount", 0);
    out.put("failedCount", 0);
    out.put("deletedCount", 0);
    out.put("reconciledManagerCount", 0);
    out.put("totalCount", 0);
    out.put("createdNames", new ArrayList<String>());
    if (trimmedCode.isEmpty()) {
      out.put("success", Boolean.FALSE);
      out.put("message", "缺少 subjectCode");
      return out;
    }
    String subjectId;
    try {
      subjectId =
          feishuRegistry
              .findSubjectIdByCode(trimmedCode)
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
    } catch (ResponseStatusException e) {
      out.put("success", Boolean.FALSE);
      out.put("message", e.getReason());
      return out;
    }
    List<String> names =
        jdbc.query(
            "SELECT name FROM feishu_subject WHERE id = ? LIMIT 1",
            (rs, rn) -> rs.getString(1),
            subjectId);
    out.put("subjectName", names.isEmpty() ? trimmedCode : names.get(0));
    FeishuRegistryService.FeishuImAppRow app;
    try {
      app =
          feishuRegistry
              .findDirectoryAppForSubjectId(subjectId)
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该主体未配置通讯录用飞书应用"));
    } catch (ResponseStatusException e) {
      out.put("success", Boolean.FALSE);
      out.put("message", e.getReason());
      return out;
    }
    try {
      String tenantToken = fetchTenantAccessToken(app.getAppId(), app.getAppSecret());
      List<Map<String, Object>> allUsers = fetchLarkContactUsers(app.getAppId(), app.getAppSecret());
      out.put("totalCount", allUsers.size());
      out.put("skippedNoSeatCount", 0);
      if (allUsers.isEmpty()) {
        out.put("deletedCount", 0);
        out.put("reconciledManagerCount", 0);
        out.put("success", Boolean.TRUE);
        return out;
      }
      log.info(
          "{} syncSubjectFromFeishu 开始 subjectCode={} directoryAppId={}",
          FEISHU_SYNC_TRACE,
          trimmedCode,
          app.getAppId());
      List<JsonNode> departments = fetchAllDepartmentNodes(tenantToken);
      DirectorySeatScanStats seatStats = new DirectorySeatScanStats();
      Set<String> seatedOpenIds =
          fetchOpenIdsWithAssignedSeat(tenantToken, departments, seatStats);
      out.put("seatAssignedCount", seatedOpenIds.size());
      int contactInSeat = 0;
      for (Map<String, Object> user : allUsers) {
        String oid = String.valueOf(user.get("userId")).trim();
        if (!oid.isEmpty() && seatedOpenIds.contains(oid)) {
          contactInSeat++;
        }
      }
      log.info(
          "{} syncSubjectFromFeishu 通讯录与席位 subjectCode={} contactCount={} deptCount={} seatAssignedOpenIdCount={} contactInSeatCount={} directoryEmployeeRows={} dirWithSeat={} dirEmptySubscriptionIds={} dirMissingSubscriptionField={} deptApiCalls={} deptApiErrors={}",
          FEISHU_SYNC_TRACE,
          trimmedCode,
          allUsers.size(),
          departments.size(),
          seatedOpenIds.size(),
          contactInSeat,
          seatStats.employeeRows,
          seatStats.withSeat,
          seatStats.emptySubscriptionIds,
          seatStats.missingSubscriptionField,
          seatStats.deptApiCalls,
          seatStats.deptApiErrors);
      if (seatStats.deptApiErrors > 0 && seatStats.firstErrorSnippet != null) {
        log.warn(
            "{} syncSubjectFromFeishu Directory 部分部门请求失败 subjectCode={} firstError={}",
            FEISHU_SYNC_TRACE,
            trimmedCode,
            seatStats.firstErrorSnippet);
      }
      if (seatStats.employeeRows > 0 && seatStats.withSeat == 0) {
        log.warn(
            "{} syncSubjectFromFeishu Directory 返回了 {} 条员工但 subscription_ids 均空；请确认应用已开通并生效 directory:employee.base.subscription_ids:read，且重新发布/授权后重试。样例={}",
            FEISHU_SYNC_TRACE,
            seatStats.employeeRows,
            seatStats.sampleEmployeeSnippet);
      }
      if (seatedOpenIds.size() > 0 && seatedOpenIds.size() <= 8) {
        log.info(
            "{} syncSubjectFromFeishu 有席位 open_id 列表 subjectCode={} ids={}",
            FEISHU_SYNC_TRACE,
            trimmedCode,
            seatedOpenIds);
      }
      SubjectEmployeeIndex index = loadSubjectEmployeeIndex(subjectId);
      int created = 0;
      int updated = 0;
      int failed = 0;
      int skippedNoSeat = 0;
      List<String> createdNames = new ArrayList<String>();
      List<String> skippedNoSeatSamples = new ArrayList<String>();
      for (Map<String, Object> user : allUsers) {
        String openId = String.valueOf(user.get("userId")).trim();
        if (openId.isEmpty()) {
          failed++;
          continue;
        }
        try {
          if (!seatedOpenIds.contains(openId)) {
            skippedNoSeat++;
            if (skippedNoSeatSamples.size() < 8) {
              skippedNoSeatSamples.add(
                  displayNameFromFeishuUser(user, openId) + "(" + openId + ")");
            }
            continue;
          }
          if (nullableString(user.get("employeeNo")) == null) {
            enrichUserFromFeishuDetail(tenantToken, openId, user);
          }
          String existingEmployeeId = resolveExistingEmployeeId(index, openId, user.get("name"));
          if (existingEmployeeId == null) {
            insertEmployeeFromFeishuContact(subjectId, openId, user);
            registerEmployeeInIndex(index, openId, openId, user.get("name"));
            created++;
            createdNames.add(displayNameFromFeishuUser(user, openId));
          } else {
            updateEmployeeFromFeishuContact(subjectId, existingEmployeeId, openId, user);
            registerEmployeeInIndex(index, openId, existingEmployeeId, user.get("name"));
            updated++;
          }
        } catch (Exception ex) {
          failed++;
          log.warn(
              "syncSubjectFromFeishu 单条失败 subjectCode={} openId={} name={}",
              trimmedCode,
              openId,
              user.get("name"),
              ex);
        }
      }
      int deleted = dedupeDuplicateNamesInSubject(subjectId);
      int reconciledManagers = normalizeManagerIdsInSubject(subjectId);
      if (skippedNoSeat > 0) {
        log.info(
            "{} syncSubjectFromFeishu 无席位跳过样例 subjectCode={} count={} samples={}",
            FEISHU_SYNC_TRACE,
            trimmedCode,
            skippedNoSeat,
            skippedNoSeatSamples);
      }
      log.info(
          "syncSubjectFromFeishu 完成 subjectCode={} created={} updated={} skippedNoSeat={} deleted={} reconciledManagers={}",
          trimmedCode,
          created,
          updated,
          skippedNoSeat,
          deleted,
          reconciledManagers);
      out.put("createdCount", created);
      out.put("createdNames", createdNames);
      out.put("updatedCount", updated);
      out.put("failedCount", failed);
      out.put("skippedNoSeatCount", skippedNoSeat);
      out.put("deletedCount", deleted);
      out.put("reconciledManagerCount", reconciledManagers);
      out.put("success", Boolean.TRUE);
      return out;
    } catch (ResponseStatusException e) {
      out.put("success", Boolean.FALSE);
      out.put("message", e.getReason());
      return out;
    } catch (Exception e) {
      log.error("syncSubjectFromFeishu 失败 subjectCode={}", trimmedCode, e);
      out.put("success", Boolean.FALSE);
      out.put("message", e.getMessage() != null ? e.getMessage() : "飞书通讯录同步失败");
      return out;
    }
  }

  /** 同步一个或多个主体；未指定时同步全部可同步主体。 */
  public Map<String, Object> syncAllSubjectsFromFeishu(List<String> subjectCodes) {
    List<String> codes = new ArrayList<String>();
    if (subjectCodes == null || subjectCodes.isEmpty()) {
      for (Map<String, Object> row : listSyncableSubjects()) {
        codes.add(String.valueOf(row.get("code")));
      }
    } else {
      for (String c : subjectCodes) {
        if (c != null && !c.trim().isEmpty()) {
          codes.add(c.trim());
        }
      }
    }
    if (codes.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无可同步的飞书主体");
    }
    int totalCreated = 0;
    int totalUpdated = 0;
    int totalFailed = 0;
    int totalSkippedNoSeat = 0;
    int totalDeleted = 0;
    List<String> allCreatedNames = new ArrayList<String>();
    List<Map<String, Object>> subjectResults = new ArrayList<Map<String, Object>>();
    boolean allSuccess = true;
    StringBuilder errMsg = new StringBuilder();
    for (String code : codes) {
      Map<String, Object> one = syncSubjectFromFeishu(code);
      subjectResults.add(one);
      if (!Boolean.TRUE.equals(one.get("success"))) {
        allSuccess = false;
        if (errMsg.length() > 0) {
          errMsg.append("; ");
        }
        errMsg.append(code).append(": ").append(one.get("message"));
      } else {
        totalCreated += intValue(one.get("createdCount"));
        totalUpdated += intValue(one.get("updatedCount"));
        totalFailed += intValue(one.get("failedCount"));
        totalSkippedNoSeat += intValue(one.get("skippedNoSeatCount"));
        totalDeleted += intValue(one.get("deletedCount"));
        Object namesObj = one.get("createdNames");
        if (namesObj instanceof List) {
          for (Object n : (List<?>) namesObj) {
            if (n != null && !String.valueOf(n).trim().isEmpty()) {
              allCreatedNames.add(String.valueOf(n).trim());
            }
          }
        }
      }
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", allSuccess);
    out.put("createdCount", totalCreated);
    out.put("createdNames", allCreatedNames);
    out.put("updatedCount", totalUpdated);
    out.put("failedCount", totalFailed);
    out.put("skippedNoSeatCount", totalSkippedNoSeat);
    out.put("deletedCount", totalDeleted);
    out.put("subjects", subjectResults);
    if (!allSuccess) {
      out.put("message", errMsg.toString());
    }
    return out;
  }

  private static String displayNameFromFeishuUser(Map<String, Object> user, String openId) {
    String name = nullableString(user.get("name"));
    if (name != null && !name.isEmpty()) {
      return name;
    }
    String employeeNo = nullableString(user.get("employeeNo"));
    if (employeeNo != null && !employeeNo.isEmpty()) {
      return employeeNo;
    }
    return openId == null ? "" : openId;
  }

  private static int intValue(Object v) {
    if (v == null) {
      return 0;
    }
    if (v instanceof Number) {
      return ((Number) v).intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static final class SubjectEmployeeIndex {
    final Map<String, String> openIdToEmployeeId = new HashMap<String, String>();
    final Map<String, List<String>> nameToEmployeeIds = new HashMap<String, List<String>>();
  }

  private static String normalizeEmployeeName(Object name) {
    if (name == null) {
      return "";
    }
    return String.valueOf(name).trim();
  }

  private static boolean looksLikeFeishuOpenId(String id) {
    if (id == null) {
      return false;
    }
    String s = id.trim();
    return s.startsWith("ou_") || s.startsWith("on_");
  }

  private SubjectEmployeeIndex loadSubjectEmployeeIndex(String subjectId) {
    SubjectEmployeeIndex index = new SubjectEmployeeIndex();
    jdbc.query(
        "SELECT employee_id, feishu_open_id, name FROM employee_hierarchy WHERE feishu_subject_id = ?",
        new Object[] {subjectId},
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> {
              String employeeId = rs.getString("employee_id");
              if (employeeId == null || employeeId.trim().isEmpty()) {
                return;
              }
              String eid = employeeId.trim();
              String openId = rs.getString("feishu_open_id");
              if (openId != null && !openId.trim().isEmpty()) {
                index.openIdToEmployeeId.put(openId.trim(), eid);
              }
              index.openIdToEmployeeId.put(eid, eid);
              String nameKey = normalizeEmployeeName(rs.getString("name"));
              if (!nameKey.isEmpty()) {
                List<String> list = index.nameToEmployeeIds.get(nameKey);
                if (list == null) {
                  list = new ArrayList<String>();
                  index.nameToEmployeeIds.put(nameKey, list);
                }
                if (!list.contains(eid)) {
                  list.add(eid);
                }
              }
            });
    return index;
  }

  private void registerEmployeeInIndex(
      SubjectEmployeeIndex index, String openId, String employeeId, Object name) {
    String eid = employeeId == null ? "" : employeeId.trim();
    if (eid.isEmpty()) {
      return;
    }
    if (openId != null && !openId.trim().isEmpty()) {
      index.openIdToEmployeeId.put(openId.trim(), eid);
    }
    index.openIdToEmployeeId.put(eid, eid);
    String nameKey = normalizeEmployeeName(name);
    if (!nameKey.isEmpty()) {
      List<String> list = index.nameToEmployeeIds.get(nameKey);
      if (list == null) {
        list = new ArrayList<String>();
        index.nameToEmployeeIds.put(nameKey, list);
      }
      if (!list.contains(eid)) {
        list.add(eid);
      }
    }
  }

  private String resolveExistingEmployeeId(
      SubjectEmployeeIndex index, String openId, Object name) {
    String byOpen = index.openIdToEmployeeId.get(openId);
    if (byOpen != null) {
      return byOpen;
    }
    String nameKey = normalizeEmployeeName(name);
    if (nameKey.isEmpty()) {
      return null;
    }
    List<String> ids = index.nameToEmployeeIds.get(nameKey);
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    if (ids.size() == 1) {
      return ids.get(0);
    }
    return pickKeeperEmployeeId(ids);
  }

  private String pickKeeperEmployeeId(List<String> employeeIds) {
    if (employeeIds == null || employeeIds.isEmpty()) {
      return null;
    }
    if (employeeIds.size() == 1) {
      return employeeIds.get(0);
    }
    String bestId = employeeIds.get(0);
    int bestScore = -1;
    for (String eid : employeeIds) {
      List<Map<String, Object>> rows =
          jdbc.query(
              "SELECT employee_id, employee_no, feishu_open_id, phone FROM employee_hierarchy WHERE employee_id = ? LIMIT 1",
              new Object[] {eid},
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("employeeId", rs.getString("employee_id"));
                m.put("employeeNo", rs.getString("employee_no"));
                m.put("feishuOpenId", rs.getString("feishu_open_id"));
                m.put("phone", rs.getString("phone"));
                return m;
              });
      if (rows.isEmpty()) {
        continue;
      }
      int score = scoreKeeperRow(rows.get(0));
      if (score > bestScore) {
        bestScore = score;
        bestId = eid;
      }
    }
    return bestId;
  }

  private static int scoreKeeperRow(Map<String, Object> row) {
    int score = 0;
    String employeeId = String.valueOf(row.get("employeeId"));
    String employeeNo = row.get("employeeNo") == null ? "" : String.valueOf(row.get("employeeNo")).trim();
    String feishuOpenId =
        row.get("feishuOpenId") == null ? "" : String.valueOf(row.get("feishuOpenId")).trim();
    String phone = row.get("phone") == null ? "" : String.valueOf(row.get("phone")).trim();
    if (!employeeNo.isEmpty() && !looksLikeFeishuOpenId(employeeNo)) {
      score += 100;
    }
    if (!feishuOpenId.isEmpty()) {
      score += 20;
    }
    if (!phone.isEmpty()) {
      score += 10;
    }
    if (!looksLikeFeishuOpenId(employeeId)) {
      score += 50;
    }
    return score;
  }

  private int dedupeDuplicateNamesInSubject(String subjectId) {
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT employee_id, name, employee_no, feishu_open_id, phone FROM employee_hierarchy WHERE feishu_subject_id = ?",
            new Object[] {subjectId},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("employeeId", rs.getString("employee_id"));
              m.put("name", rs.getString("name"));
              m.put("employeeNo", rs.getString("employee_no"));
              m.put("feishuOpenId", rs.getString("feishu_open_id"));
              m.put("phone", rs.getString("phone"));
              return m;
            });
    Map<String, List<Map<String, Object>>> byName = new LinkedHashMap<String, List<Map<String, Object>>>();
    for (Map<String, Object> row : rows) {
      String nameKey = normalizeEmployeeName(row.get("name"));
      if (nameKey.isEmpty()) {
        continue;
      }
      List<Map<String, Object>> group = byName.get(nameKey);
      if (group == null) {
        group = new ArrayList<Map<String, Object>>();
        byName.put(nameKey, group);
      }
      group.add(row);
    }
    int deleted = 0;
    for (List<Map<String, Object>> group : byName.values()) {
      if (group.size() <= 1) {
        continue;
      }
      List<String> ids = new ArrayList<String>();
      for (Map<String, Object> row : group) {
        ids.add(String.valueOf(row.get("employeeId")));
      }
      String keeperId = pickKeeperEmployeeId(ids);
      for (Map<String, Object> row : group) {
        String eid = String.valueOf(row.get("employeeId"));
        if (keeperId != null && !keeperId.equals(eid)) {
          deleteEmployee(eid);
          deleted++;
          log.info(
              "dedupeDuplicateNamesInSubject 删除重复员工 subjectId={} name={} deletedEmployeeId={} keeperEmployeeId={}",
              subjectId,
              row.get("name"),
              eid,
              keeperId);
        }
      }
    }
    return deleted;
  }

  private void enrichUserFromFeishuDetail(String tenantToken, String openId, Map<String, Object> user)
      throws Exception {
    String url =
        "https://open.feishu.cn/open-apis/contact/v3/users/"
            + URLEncoder.encode(openId, StandardCharsets.UTF_8.name())
            + "?user_id_type=open_id&department_id_type=open_department_id";
    JsonNode res = feishuGet(url, tenantToken);
    if (res.path("code").asInt(0) != 0) {
      return;
    }
    JsonNode item = res.path("data").path("user");
    String empNo = userEmployeeNoText(item);
    if (!empNo.isEmpty()) {
      user.put("employeeNo", empNo);
    }
    String mobile = item.path("mobile").asText("").trim();
    if (!mobile.isEmpty()) {
      user.put("phone", mobile);
    }
    if (nullableString(user.get("departmentName")) == null) {
      String deptName = userDepartmentNameFromItem(item);
      if (!deptName.isEmpty()) {
        user.put("departmentName", deptName);
      }
    }
    JsonNode deptIds = item.path("department_ids");
    if (!user.containsKey("departmentId") && deptIds.isArray() && deptIds.size() > 0) {
      user.put("departmentId", deptIds.get(0).asText(""));
    }
    if (user.get("leaderUserId") == null || String.valueOf(user.get("leaderUserId")).trim().isEmpty()) {
      String leader = item.path("leader_user_id").asText("").trim();
      if (!leader.isEmpty()) {
        user.put("leaderUserId", leader);
      }
    }
    if (user.get("dottedLeaderUserId") == null || String.valueOf(user.get("dottedLeaderUserId")).trim().isEmpty()) {
      JsonNode dottedLeaders = item.path("dotted_line_leader_user_ids");
      if (dottedLeaders.isArray() && dottedLeaders.size() > 0) {
        String dottedLeader = dottedLeaders.get(0).asText("").trim();
        if (!dottedLeader.isEmpty()) {
          user.put("dottedLeaderUserId", dottedLeader);
        }
      }
    }
  }

  private void insertEmployeeFromFeishuContact(
      String subjectId, String openId, Map<String, Object> user) {
    jdbc.update(
        "INSERT INTO employee_hierarchy (id, employee_id, feishu_subject_id, feishu_open_id, manager_id, dotted_manager_id, name, phone, employee_no, department_id, department_name) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        openId,
        subjectId,
        openId,
        nullableString(user.get("leaderUserId")),
        nullableString(user.get("dottedLeaderUserId")),
        nullableString(user.get("name")),
        nullableString(user.get("phone")),
        nullableString(user.get("employeeNo")),
        nullableString(user.get("departmentId")),
        nullableString(user.get("departmentName")));
  }

  private void updateEmployeeFromFeishuContact(
      String subjectId, String employeeId, String openId, Map<String, Object> user) {
    List<String> sets = new ArrayList<String>();
    List<Object> args = new ArrayList<Object>();
    sets.add("feishu_subject_id = ?");
    args.add(subjectId);
    sets.add("feishu_open_id = ?");
    args.add(openId);
    sets.add("manager_id = ?");
    args.add(nullableString(user.get("leaderUserId")));
    sets.add("dotted_manager_id = ?");
    args.add(nullableString(user.get("dottedLeaderUserId")));
    String name = nullableString(user.get("name"));
    if (name != null) {
      sets.add("name = ?");
      args.add(name);
    }
    String employeeNo = nullableString(user.get("employeeNo"));
    if (employeeNo != null) {
      sets.add("employee_no = ?");
      args.add(employeeNo);
    }
    String phone = nullableString(user.get("phone"));
    if (phone != null) {
      sets.add("phone = ?");
      args.add(phone);
    }
    if (user.containsKey("departmentId")) {
      sets.add("department_id = ?");
      args.add(nullableString(user.get("departmentId")));
    }
    String departmentName = nullableString(user.get("departmentName"));
    if (departmentName != null) {
      sets.add("department_name = ?");
      args.add(departmentName);
    }
    sets.add("_updated_at = CURRENT_TIMESTAMP");
    args.add(employeeId);
    String sql =
        "UPDATE employee_hierarchy SET "
            + String.join(", ", sets)
            + " WHERE employee_id = ?";
    jdbc.update(sql, args.toArray());
  }

  private static String nullableString(Object v) {
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v).trim();
    return s.isEmpty() ? null : s;
  }

  public Map<String, Object> syncFromLark(String subjectCode, boolean clearExisting) {
    if (subjectCode == null || subjectCode.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 subjectCode");
    }
    String trimmedCode = subjectCode.trim();
    String subjectId =
        feishuRegistry
            .findSubjectIdByCode(trimmedCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
    FeishuRegistryService.FeishuImAppRow app =
        feishuRegistry
            .findDirectoryAppForSubjectId(subjectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该主体未配置通讯录用飞书应用"));
    if (clearExisting) {
      clearAllEmployees();
    }
    try {
      List<Map<String, Object>> allUsers = fetchLarkContactUsers(app.getAppId(), app.getAppSecret());
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
              "INSERT INTO employee_hierarchy (id, employee_id, feishu_subject_id, feishu_open_id, manager_id, dotted_manager_id, name, department_name) VALUES (?,?,?,?,?,?,?,?)",
              UUID.randomUUID().toString(),
              uid,
              subjectId,
              uid,
              user.get("leaderUserId"),
              user.get("dottedLeaderUserId"),
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

  private static final String DIRECTORY_EMPLOYEES_FILTER_URL =
      "https://open.feishu.cn/open-apis/directory/v1/employees/filter"
          + "?employee_id_type=open_id&department_id_type=open_department_id";

  private static final class DirectorySeatScanStats {
    int deptApiCalls;
    int deptApiErrors;
    int employeeRows;
    int withSeat;
    int emptySubscriptionIds;
    int missingSubscriptionField;
    String firstErrorSnippet;
    String sampleEmployeeSnippet;
  }

  /**
   * 按部门调用 Directory「批量获取员工」，汇总 {@code base_info.subscription_ids} 非空的 open_id（已分配飞书席位）。
   */
  private Set<String> fetchOpenIdsWithAssignedSeat(
      String tenantToken, List<JsonNode> departments, DirectorySeatScanStats stats) throws Exception {
    Set<String> withSeat = new LinkedHashSet<String>();
    int deptLimit = Math.min(departments.size(), MAX_DEPARTMENTS_FOR_USER_SCAN);
    for (int i = 0; i < deptLimit; i++) {
      String deptId = departmentOpenId(departments.get(i));
      if (deptId.isEmpty()) {
        continue;
      }
      try {
        fetchSeatedOpenIdsInDepartment(tenantToken, deptId, withSeat, stats);
      } catch (Exception ex) {
        stats.deptApiErrors++;
        if (stats.firstErrorSnippet == null) {
          stats.firstErrorSnippet = ex.getMessage();
        }
        log.warn(
            "{} Directory 按部门拉员工失败 deptId={} err={}",
            FEISHU_SYNC_TRACE,
            deptId,
            ex.getMessage());
      }
    }
    log.info(
        "{} fetchOpenIdsWithAssignedSeat deptScanned={} seatAssignedOpenIdCount={} directoryEmployeeRows={} withSeat={} emptySubscriptionIds={} missingField={}",
        FEISHU_SYNC_TRACE,
        deptLimit,
        withSeat.size(),
        stats.employeeRows,
        stats.withSeat,
        stats.emptySubscriptionIds,
        stats.missingSubscriptionField);
    return withSeat;
  }

  private void fetchSeatedOpenIdsInDepartment(
      String tenantToken,
      String departmentOpenId,
      Set<String> withSeat,
      DirectorySeatScanStats stats)
      throws Exception {
    String pageToken = "";
    int pageIndex = 0;
    do {
      String body = buildDirectoryEmployeesFilterBody(departmentOpenId, pageToken);
      stats.deptApiCalls++;
      JsonNode res = feishuPostJson(DIRECTORY_EMPLOYEES_FILTER_URL, body, tenantToken);
      int code = res.path("code").asInt(-1);
      if (code != 0) {
        String snippet = jsonNodeForLog(res, 2000);
        if (stats.firstErrorSnippet == null) {
          stats.firstErrorSnippet = "code=" + code + " msg=" + res.path("msg").asText("") + " " + snippet;
        }
        log.warn(
            "{} Directory employees/filter 非0 deptId={} page={} code={} msg={} response={}",
            FEISHU_SYNC_TRACE,
            departmentOpenId,
            pageIndex,
            code,
            res.path("msg").asText(""),
            snippet);
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "飞书 Directory 员工查询失败: "
                + res.path("msg").asText("")
                + "（需开通 directory:employee:list 与 directory:employee.base.subscription_ids:read）");
      }
      JsonNode data = res.path("data");
      JsonNode employees = data.path("employees");
      int pageRows = 0;
      int pageWithSeat = 0;
      if (employees.isArray()) {
        for (JsonNode emp : employees) {
          pageRows++;
          stats.employeeRows++;
          String openId = directoryEmployeeOpenId(emp);
          int seatState = directoryEmployeeSeatState(emp);
          if (seatState == 1) {
            stats.withSeat++;
            pageWithSeat++;
            if (!openId.isEmpty()) {
              withSeat.add(openId);
            }
          } else if (seatState == 0) {
            stats.emptySubscriptionIds++;
          } else {
            stats.missingSubscriptionField++;
          }
          if (stats.sampleEmployeeSnippet == null && emp != null && !emp.isMissingNode()) {
            JsonNode base = emp.path("base_info");
            stats.sampleEmployeeSnippet =
                "openId="
                    + openId
                    + " seatState="
                    + seatState
                    + " base_info="
                    + jsonNodeForLog(base, 800);
          }
        }
      }
      if (pageIndex == 0 && pageRows > 0 && stats.deptApiCalls == 1) {
        log.info(
            "{} Directory employees/filter 首部门首页 deptId={} rows={} withSeatOnPage={}",
            FEISHU_SYNC_TRACE,
            departmentOpenId,
            pageRows,
            pageWithSeat);
      }
      JsonNode page = data.path("page_response");
      boolean hasMore = page.path("has_more").asBoolean(false);
      pageToken = page.path("page_token").asText("");
      pageIndex++;
      if (!hasMore || pageToken.isEmpty()) {
        break;
      }
    } while (true);
  }

  private static String buildDirectoryEmployeesFilterBody(String departmentOpenId, String pageToken)
      throws Exception {
    Map<String, Object> statusCond = new LinkedHashMap<String, Object>();
    statusCond.put("field", "work_info.staff_status");
    statusCond.put("operator", "eq");
    statusCond.put("value", "1");
    Map<String, Object> deptCond = new LinkedHashMap<String, Object>();
    deptCond.put("field", "base_info.departments.department_id");
    deptCond.put("operator", "eq");
    deptCond.put("value", "\"" + departmentOpenId + "\"");
    List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
    conditions.add(statusCond);
    conditions.add(deptCond);
    Map<String, Object> filter = new LinkedHashMap<String, Object>();
    filter.put("conditions", conditions);
    List<String> requiredFields = new ArrayList<String>();
    requiredFields.add("base_info.employee_id");
    requiredFields.add("base_info.subscription_ids");
    requiredFields.add("base_info.name");
    Map<String, Object> pageRequest = new LinkedHashMap<String, Object>();
    pageRequest.put("page_size", 100);
    pageRequest.put("page_token", pageToken == null ? "" : pageToken);
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("filter", filter);
    root.put("required_fields", requiredFields);
    root.put("page_request", pageRequest);
    return MAPPER.writeValueAsString(root);
  }

  private static String directoryEmployeeOpenId(JsonNode emp) {
    if (emp == null || emp.isMissingNode()) {
      return "";
    }
    String fromBase = emp.path("base_info").path("employee_id").asText("").trim();
    if (!fromBase.isEmpty()) {
      return fromBase;
    }
    return emp.path("employee_id").asText("").trim();
  }

  /** @return 1=有席位；0=字段在但为空；2=未返回 subscription_ids 字段（多为缺字段权限） */
  private static int directoryEmployeeSeatState(JsonNode emp) {
    JsonNode base = emp == null ? null : emp.path("base_info");
    if (base == null || base.isMissingNode()) {
      return 2;
    }
    if (!base.has("subscription_ids")) {
      return 2;
    }
    JsonNode ids = base.path("subscription_ids");
    if (!ids.isArray() || ids.size() == 0) {
      return 0;
    }
    for (JsonNode id : ids) {
      if (id != null && !id.asText("").trim().isEmpty()) {
        return 1;
      }
    }
    return 0;
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

  /** 将 departmentId / departmentName 对齐到 org_department 主数据（写入 employee_hierarchy）。 */
  private void applyDepartmentBinding(Map<String, Object> body) {
    if (!orgDepartmentService.tableReady()) {
      return;
    }
    Object deptIdObj = body.get("departmentId");
    if (deptIdObj != null && !String.valueOf(deptIdObj).trim().isEmpty()) {
      Optional<Map<String, Object>> od =
          orgDepartmentService.findById(String.valueOf(deptIdObj).trim());
      if (od.isPresent()) {
        body.put("departmentId", od.get().get("id"));
        body.put("departmentName", od.get().get("name"));
        return;
      }
    }
    Object nameObj = body.get("departmentName");
    String subjectCode = null;
    if (body.get("feishuSubjectCode") != null) {
      subjectCode = String.valueOf(body.get("feishuSubjectCode")).trim();
    }
    if (nameObj != null && subjectCode != null && !subjectCode.isEmpty()) {
      String name = String.valueOf(nameObj).trim();
      if (!name.isEmpty()) {
        Optional<Map<String, Object>> od =
            orgDepartmentService.findBySubjectCodeAndName(subjectCode, name);
        if (od.isPresent()) {
          body.put("departmentId", od.get().get("id"));
          body.put("departmentName", od.get().get("name"));
        }
      }
    }
  }
}
