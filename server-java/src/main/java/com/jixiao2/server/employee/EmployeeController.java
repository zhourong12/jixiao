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

  public EmployeeController(EmployeeService employeeService, MenuPermissionService menuPermissionService) {
    this.employeeService = employeeService;
    this.menuPermissionService = menuPermissionService;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword) {
    Map<String, Object> data = employeeService.getAllHierarchies(page, pageSize, keyword);
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
  public Map<String, Object> departmentOptions() {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", employeeService.listDepartmentOptions());
    return out;
  }

  @GetMapping("/role-options")
  public Map<String, Object> roleOptions() {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", employeeService.listAssignableRoles());
    return out;
  }

  @GetMapping("/all")
  public Map<String, Object> all() {
    Map<String, Object> data = employeeService.getAllHierarchies(1, 10000, null);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> hierarchies = (List<Map<String, Object>>) data.get("items");
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", mapEmployeeItems(hierarchies));
    return out;
  }

  @PostMapping("/sync-from-lark")
  public Map<String, Object> syncFromLark(@RequestBody Map<String, Object> body) {
    boolean clearExisting = Boolean.TRUE.equals(body.get("clearExisting"));
    try {
      Map<String, Object> result = employeeService.syncFromLark(clearExisting);
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
      item.put("departmentName", h.get("departmentName"));
      item.put("managerId", h.get("managerId"));
      item.put("managerName", h.get("managerName"));
      item.put("dottedManagerId", h.get("dottedManagerId"));
      item.put("dottedManagerName", h.get("dottedManagerName"));
      item.put("roleKey", h.get("roleKey"));
      item.put("roleName", h.get("roleName"));
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
