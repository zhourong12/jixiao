package com.jixiao2.server.employee;

import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.web.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

  private final EmployeeService employeeService;
  private final MenuPermissionService menuPermissionService;

  public EmployeeController(
      EmployeeService employeeService, MenuPermissionService menuPermissionService) {
    this.employeeService = employeeService;
    this.menuPermissionService = menuPermissionService;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String subjectCode,
      @RequestParam(required = false) String departmentId) {
    Map<String, Object> data =
        employeeService.getAllHierarchies(page, pageSize, keyword, subjectCode, departmentId);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hierarchies = (List<Map<String, Object>>) data.get("items");
    List<Map<String, Object>> items = mapEmployeeItems(hierarchies);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("total", data.get("total"));
    out.put("page", page);
    out.put("pageSize", pageSize);
    return out;
  }

  @PostMapping
  public Map<String, Object> create(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    Object roleKey = body.get("roleKey");
    if (roleKey != null && !String.valueOf(roleKey).trim().isEmpty()) {
      menuPermissionService.assertCanManageEmployeeRoles(userId);
    }
    return employeeService.createEmployee(body);
  }

  @PutMapping("/{id}")
  public Map<String, Boolean> update(
      @CurrentUser String userId, @PathVariable("id") String employeeId, @RequestBody Map<String, Object> body) {
    if (body.containsKey("roleKey")) {
      menuPermissionService.assertCanManageEmployeeRoles(userId);
    }
    employeeService.updateEmployee(employeeId, body);
    return singleSuccess();
  }

  /** 使用多段路径，避免被 {@code PUT /{id}} 将 {@code batch-assessment-rule} 当作 id 匹配导致 POST 405。 */
  @PostMapping("/batch/assessment-rule")
  public Map<String, Object> batchAssessmentRule(
      @CurrentUser String userId, @RequestBody Map<String, Object> body) {
    menuPermissionService.assertMenuAllowed(userId, "admin_employees");
    List<String> employeeIds = new ArrayList<String>();
    Object rawIds = body == null ? null : body.get("employeeIds");
    if (rawIds instanceof List) {
      for (Object item : (List<?>) rawIds) {
        if (item != null && !String.valueOf(item).trim().isEmpty()) {
          employeeIds.add(String.valueOf(item).trim());
        }
      }
    }
    Object ruleObj = body == null ? null : body.get("assessmentRuleId");
    if (ruleObj == null && body != null) {
      ruleObj = body.get("assessment_rule_id");
    }
    String assessmentRuleId = ruleObj == null ? "" : String.valueOf(ruleObj);
    return employeeService.batchUpdateAssessmentRule(employeeIds, assessmentRuleId);
  }

  @DeleteMapping("/{id}")
  public Map<String, Boolean> delete(@PathVariable("id") String employeeId) {
    employeeService.deleteEmployee(employeeId);
    return singleSuccess();
  }

  @PutMapping("/{id}/hierarchy")
  public Map<String, Boolean> updateHierarchy(
      @PathVariable("id") String employeeId, @RequestBody Map<String, Object> body) {
    body.put("employeeId", employeeId);
    employeeService.upsertEmployeeHierarchy(body);
    return singleSuccess();
  }

  @GetMapping("/departments")
  public Map<String, Object> departments() {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", employeeService.getAllDepartments());
    return out;
  }

  @GetMapping("/department-options")
  public Map<String, Object> departmentOptions(@RequestParam(required = false) String subjectCode) {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", employeeService.listDepartmentOptions(subjectCode));
    return out;
  }

  @GetMapping("/department-tree")
  public Map<String, Object> departmentTree() {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", employeeService.listDepartmentTree());
    return out;
  }

  @GetMapping("/role-options")
  public Map<String, Object> roleOptions() {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", employeeService.listAssignableRoles());
    return out;
  }

  @GetMapping("/all")
  public Map<String, Object> all(@RequestParam(required = false) String subjectCode) {
    Map<String, Object> data = employeeService.getAllHierarchies(1, 10000, null, subjectCode);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hierarchies = (List<Map<String, Object>>) data.get("items");
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", mapEmployeeItems(hierarchies));
    return out;
  }

  /** 从飞书通讯录 upsert 员工；未传 subjectCodes 时同步全部已配置主体。 */
  @PostMapping("/sync-from-feishu")
  public Map<String, Object> syncFromFeishu(
      @CurrentUser String userId, @RequestBody(required = false) Map<String, Object> body) {
    menuPermissionService.assertMenuAllowed(userId, "admin_employees");
    List<String> subjectCodes = null;
    if (body != null && body.get("subjectCodes") instanceof List) {
      subjectCodes = new ArrayList<String>();
      for (Object item : (List<?>) body.get("subjectCodes")) {
        if (item != null && !String.valueOf(item).trim().isEmpty()) {
          subjectCodes.add(String.valueOf(item).trim());
        }
      }
    }
    try {
      return employeeService.syncAllSubjectsFromFeishu(subjectCodes);
    } catch (Exception e) {
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("success", Boolean.FALSE);
      out.put("createdCount", 0);
      out.put("updatedCount", 0);
      out.put("failedCount", 0);
      out.put("deletedCount", 0);
      out.put("createdNames", new ArrayList<String>());
      out.put("subjects", new ArrayList<Map<String, Object>>());
      out.put("message", e.getMessage());
      return out;
    }
  }

  @PostMapping("/sync-from-lark")
  public Map<String, Object> syncFromLark(
      @CurrentUser String userId, @RequestBody Map<String, Object> body) {
    menuPermissionService.assertMenuAllowed(userId, "admin_employees");
    boolean clearExisting = Boolean.TRUE.equals(body.get("clearExisting"));
    Object sc = body.get("subjectCode");
    if (sc == null || String.valueOf(sc).trim().isEmpty()) {
      sc = body.get("feishuSubjectCode");
    }
    String subjectCode = sc == null ? "" : String.valueOf(sc).trim();
    try {
      Map<String, Object> result = employeeService.syncFromLark(subjectCode, clearExisting);
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("success", Boolean.TRUE);
      out.put("syncedCount", result.get("count"));
      out.put("totalCount", result.get("totalCount"));
      out.put("validCount", result.get("validCount"));
      out.put("skippedCount", result.get("skippedCount"));
      return out;
    } catch (Exception e) {
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("success", Boolean.FALSE);
      out.put("syncedCount", 0);
      out.put("message", e.getMessage());
      return out;
    }
  }

  @GetMapping("/feishu-user-options")
  public Map<String, Object> feishuUserOptions(
      @CurrentUser String userId,
      @RequestParam String subjectCode) {
    menuPermissionService.assertMenuAllowed(userId, "admin_employees");
    return employeeService.listFeishuUserOptions(subjectCode);
  }

  @GetMapping("/feishu-user-profile")
  public Map<String, Object> feishuUserProfile(
      @CurrentUser String userId, @RequestParam String subjectCode, @RequestParam String openId) {
    menuPermissionService.assertMenuAllowed(userId, "admin_employees");
    return employeeService.fetchFeishuUserProfile(subjectCode, openId);
  }

  private static List<Map<String, Object>> mapEmployeeItems(List<Map<String, Object>> hierarchies) {
    List<Map<String, Object>> employeeItems = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> h : hierarchies) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      String eid = String.valueOf(h.get("employeeId"));
      item.put("id", eid);
      item.put("userId", eid);
      item.put("name", h.get("name") == null ? "" : h.get("name"));
      item.put("phone", h.get("phone"));
      item.put("employeeNo", h.get("employeeNo"));
      item.put("employeeType", h.get("employeeType"));
      item.put("position", h.get("position"));
      item.put("workLocation", h.get("workLocation"));
      item.put("joinDate", h.get("joinDate"));
      item.put("departmentId", h.get("departmentId"));
      item.put("departmentName", h.get("departmentName"));
      item.put("managerId", h.get("managerId"));
      item.put("managerName", h.get("managerName"));
      item.put("dottedManagerId", h.get("dottedManagerId"));
      item.put("dottedManagerName", h.get("dottedManagerName"));
      item.put("roleKey", h.get("roleKey"));
      item.put("roleName", h.get("roleName"));
      item.put("feishuSubjectId", h.get("feishuSubjectId"));
      item.put("feishuOpenId", h.get("feishuOpenId"));
      item.put("feishuSubjectCode", h.get("feishuSubjectCode"));
      item.put("feishuSubjectName", h.get("feishuSubjectName"));
      item.put("assessmentRuleId", h.get("assessmentRuleId"));
      item.put("assessmentRuleName", h.get("assessmentRuleName"));
      employeeItems.add(item);
    }
    return employeeItems;
  }

  private static Map<String, Boolean> singleSuccess() {
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", Boolean.TRUE);
    return out;
  }
}
