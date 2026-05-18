package com.jixiao2.server.orgdepartment;

import com.jixiao2.server.feishu.FeishuRegistryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrgDepartmentService {

  private final JdbcTemplate jdbc;
  private final FeishuRegistryService feishuRegistry;

  public OrgDepartmentService(JdbcTemplate jdbc, FeishuRegistryService feishuRegistry) {
    this.jdbc = jdbc;
    this.feishuRegistry = feishuRegistry;
  }

  public boolean tableReady() {
    try {
      jdbc.queryForObject("SELECT COUNT(*) FROM org_department", Integer.class);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public Map<String, Object> listAdmin(int page, int pageSize, String subjectCode, String keyword) {
    List<Object> args = new ArrayList<Object>();
    StringBuilder where = new StringBuilder("od.enabled = 1");
    if (subjectCode != null && !subjectCode.trim().isEmpty()) {
      String sid =
          feishuRegistry
              .findSubjectIdByCode(subjectCode.trim())
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
      where.append(" AND od.feishu_subject_id = ?");
      args.add(sid);
    }
    if (keyword != null && !keyword.trim().isEmpty()) {
      where.append(" AND od.name LIKE ?");
      args.add("%" + keyword.trim() + "%");
    }
    Integer total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM org_department od WHERE " + where, Integer.class, args.toArray());
    int offset = (page - 1) * pageSize;
    List<Object> listArgs = new ArrayList<Object>(args);
    listArgs.add(pageSize);
    listArgs.add(offset);
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT od.id, od.name, od.lark_department_id, od.sort_order, od.feishu_subject_id,"
                + " fs.code AS subject_code, fs.name AS subject_name,"
                + " (SELECT COUNT(*) FROM employee_hierarchy eh WHERE "
                + subjectEmployeeMatchSql("eh", "od.feishu_subject_id")
                + " AND "
                + departmentEmployeeMatchSql("eh", "od")
                + ") AS employee_count"
                + " FROM org_department od"
                + " INNER JOIN feishu_subject fs ON fs.id = od.feishu_subject_id"
                + " WHERE "
                + where
                + " ORDER BY fs.sort_order ASC, fs.code ASC, od.sort_order ASC, od.name ASC"
                + " LIMIT ? OFFSET ?",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("name", rs.getString("name"));
              m.put("larkDepartmentId", rs.getString("lark_department_id"));
              m.put("sortOrder", rs.getInt("sort_order"));
              m.put("subjectCode", rs.getString("subject_code"));
              m.put("subjectName", rs.getString("subject_name"));
              m.put("employeeCount", rs.getInt("employee_count"));
              return m;
            },
            listArgs.toArray());
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("total", total == null ? 0 : total);
    out.put("page", page);
    out.put("pageSize", pageSize);
    return out;
  }

  public List<Map<String, Object>> listDepartmentTree() {
    if (!tableReady()) {
      return new ArrayList<Map<String, Object>>();
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
    List<Map<String, Object>> tree = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> subject : subjects) {
      String sid = String.valueOf(subject.get("id"));
      List<Map<String, Object>> departments =
          jdbc.query(
              "SELECT id, name FROM org_department WHERE enabled = 1 AND feishu_subject_id = ?"
                  + " ORDER BY sort_order ASC, name ASC",
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getString("id"));
                m.put("name", rs.getString("name"));
                return m;
              },
              sid);
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

  public Map<String, Object> syncFromEmployees() {
    if (!tableReady()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "请先执行数据库脚本 server-java/database/sql/add-org-department.sql");
    }
    List<Map<String, Object>> subjects =
        jdbc.query(
            "SELECT id, code FROM feishu_subject WHERE enabled = 1",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("code", rs.getString("code"));
              return m;
            });
    int upserted = 0;
    for (Map<String, Object> subject : subjects) {
      String sid = String.valueOf(subject.get("id"));
      List<Map<String, Object>> rows =
          jdbc.query(
              "SELECT TRIM(eh.department_name) AS dept_name,"
                  + " MIN(NULLIF(TRIM(eh.department_id), '')) AS lark_id"
                  + " FROM employee_hierarchy eh"
                  + " WHERE TRIM(COALESCE(eh.department_name, '')) <> ''"
                  + " AND "
                  + subjectEmployeeMatchSql("eh", "?")
                  + " GROUP BY TRIM(eh.department_name)",
              (rs, rn) -> {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("name", rs.getString("dept_name"));
                m.put("larkId", rs.getString("lark_id"));
                return m;
              },
              sid,
              sid);
      for (Map<String, Object> row : rows) {
        String name = String.valueOf(row.get("name")).trim();
        if (name.isEmpty()) {
          continue;
        }
        String larkId = row.get("larkId") == null ? null : String.valueOf(row.get("larkId")).trim();
        if (larkId != null && larkId.isEmpty()) {
          larkId = null;
        }
        if (larkId != null && larkId.equals(name)) {
          larkId = null;
        }
        upserted += upsertDepartment(sid, name, larkId);
      }
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("upserted", upserted);
    out.put("success", true);
    return out;
  }

  public Map<String, Object> createDepartment(String subjectCode, String name, Integer sortOrder) {
    if (!tableReady()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门表未初始化");
    }
    String code = subjectCode == null ? "" : subjectCode.trim();
    String trimmed = name == null ? "" : name.trim();
    if (code.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择飞书主体");
    }
    if (trimmed.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门名称不能为空");
    }
    String sid =
        feishuRegistry
            .findSubjectIdByCode(code)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
    List<String> exists =
        jdbc.query(
            "SELECT id FROM org_department WHERE feishu_subject_id = ? AND name = ? AND enabled = 1 LIMIT 1",
            (rs, rn) -> rs.getString(1),
            sid,
            trimmed);
    if (!exists.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该主体下已存在同名部门");
    }
    String id = UUID.randomUUID().toString();
    int sort = sortOrder == null ? 0 : sortOrder;
    jdbc.update(
        "INSERT INTO org_department (id, feishu_subject_id, name, lark_department_id, sort_order, enabled)"
            + " VALUES (?,?,?,?,?,1)",
        id,
        sid,
        trimmed,
        null,
        sort);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("id", id);
    out.put("success", true);
    return out;
  }

  public void deleteDepartment(String id) {
    if (!tableReady()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门表未初始化");
    }
    String deptId = id == null ? "" : id.trim();
    if (deptId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门 id 无效");
    }
    int n = jdbc.update("UPDATE org_department SET enabled = 0 WHERE id = ? AND enabled = 1", deptId);
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部门不存在");
    }
  }

  public void updateDepartment(String id, String name, Integer sortOrder) {
    if (!tableReady()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门表未初始化");
    }
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门名称不能为空");
    }
    int n =
        jdbc.update(
            "UPDATE org_department SET name = ?, sort_order = COALESCE(?, sort_order) WHERE id = ?",
            trimmed,
            sortOrder,
            id);
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部门不存在");
    }
  }

  public Optional<Map<String, Object>> findBySubjectCodeAndName(String subjectCode, String name) {
    if (subjectCode == null || subjectCode.trim().isEmpty() || name == null || name.trim().isEmpty()) {
      return Optional.empty();
    }
    if (!tableReady()) {
      return Optional.empty();
    }
    String sid =
        feishuRegistry
            .findSubjectIdByCode(subjectCode.trim())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT od.id, od.name, od.lark_department_id, od.feishu_subject_id, fs.code AS subject_code"
                + " FROM org_department od"
                + " INNER JOIN feishu_subject fs ON fs.id = od.feishu_subject_id"
                + " WHERE od.enabled = 1 AND od.feishu_subject_id = ? AND od.name = ? LIMIT 1",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("name", rs.getString("name"));
              m.put("larkDepartmentId", rs.getString("lark_department_id"));
              m.put("feishuSubjectId", rs.getString("feishu_subject_id"));
              m.put("subjectCode", rs.getString("subject_code"));
              return m;
            },
            sid,
            name.trim());
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public Optional<Map<String, Object>> findById(String id) {
    if (id == null || id.trim().isEmpty() || !tableReady()) {
      return Optional.empty();
    }
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT od.id, od.name, od.lark_department_id, od.feishu_subject_id, fs.code AS subject_code"
                + " FROM org_department od"
                + " INNER JOIN feishu_subject fs ON fs.id = od.feishu_subject_id"
                + " WHERE od.id = ? AND od.enabled = 1 LIMIT 1",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("name", rs.getString("name"));
              m.put("larkDepartmentId", rs.getString("lark_department_id"));
              m.put("feishuSubjectId", rs.getString("feishu_subject_id"));
              m.put("subjectCode", rs.getString("subject_code"));
              return m;
            },
            id.trim());
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /** 将筛选 id（org_department.id 或历史部门名/id）展开为 SQL 条件片段。 */
  public void appendEmployeeDepartmentFilter(
      List<String> parts, List<Object> args, String subjectCode, String departmentId, String empAlias) {
    String deptKey = departmentId == null ? "" : departmentId.trim();
    if (deptKey.isEmpty()) {
      return;
    }
    Optional<Map<String, Object>> org = findById(deptKey);
    if (org.isPresent()) {
      String sid = String.valueOf(org.get().get("feishuSubjectId"));
      String name = String.valueOf(org.get().get("name"));
      String larkId = org.get().get("larkDepartmentId") == null ? "" : String.valueOf(org.get().get("larkDepartmentId")).trim();
      parts.add("(" + subjectEmployeeMatchSql(empAlias, "?") + ")");
      args.add(sid);
      args.add(sid);
      StringBuilder deptMatch = new StringBuilder("(");
      deptMatch.append(empAlias).append(".department_name=?");
      args.add(name);
      if (!larkId.isEmpty()) {
        deptMatch.append(" OR ").append(empAlias).append(".department_id=?");
        args.add(larkId);
      }
      deptMatch.append(" OR ").append(empAlias).append(".department_id=?");
      args.add(deptKey);
      deptMatch.append(")");
      parts.add(deptMatch.toString());
      String subCode = subjectCode == null ? "" : subjectCode.trim();
      if (!subCode.isEmpty()) {
        String filterSid =
            feishuRegistry
                .findSubjectIdByCode(subCode)
                .orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
        if (!filterSid.equals(sid)) {
          parts.add("1=0");
        }
      }
      return;
    }
    parts.add("(" + empAlias + ".department_id=? OR " + empAlias + ".department_name=?)");
    args.add(deptKey);
    args.add(deptKey);
  }

  private int upsertDepartment(String subjectId, String name, String larkDepartmentId) {
    List<String> ids =
        jdbc.query(
            "SELECT id FROM org_department WHERE feishu_subject_id = ? AND name = ? LIMIT 1",
            (rs, rn) -> rs.getString(1),
            subjectId,
            name);
    if (ids.isEmpty()) {
      String id = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO org_department (id, feishu_subject_id, name, lark_department_id, enabled) VALUES (?,?,?,?,1)",
          id,
          subjectId,
          name,
          larkDepartmentId);
      return 1;
    }
    jdbc.update(
        "UPDATE org_department SET lark_department_id = COALESCE(?, lark_department_id), enabled = 1 WHERE id = ?",
        larkDepartmentId,
        ids.get(0));
    return 1;
  }

  private static String subjectEmployeeMatchSql(String empAlias, String subjectIdPlaceholder) {
    return "("
        + empAlias
        + ".feishu_subject_id = "
        + subjectIdPlaceholder
        + " OR ("
        + empAlias
        + ".feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = "
        + subjectIdPlaceholder
        + " AND fs.code = 'default')))";
  }

  private static String departmentEmployeeMatchSql(String empAlias, String odAlias) {
    return "("
        + empAlias
        + ".department_name = "
        + odAlias
        + ".name OR "
        + empAlias
        + ".department_id = "
        + odAlias
        + ".lark_department_id OR "
        + empAlias
        + ".department_id = "
        + odAlias
        + ".id)";
  }
}
