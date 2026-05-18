package com.jixiao2.server.orgdepartment;

import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.web.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/departments")
public class OrgDepartmentController {

  private final OrgDepartmentService orgDepartmentService;
  private final MenuPermissionService menuPermissionService;

  public OrgDepartmentController(
      OrgDepartmentService orgDepartmentService, MenuPermissionService menuPermissionService) {
    this.orgDepartmentService = orgDepartmentService;
    this.menuPermissionService = menuPermissionService;
  }

  @GetMapping
  public Map<String, Object> list(
      @CurrentUser String userId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int pageSize,
      @RequestParam(required = false) String subjectCode,
      @RequestParam(required = false) String keyword) {
    menuPermissionService.assertMenuAllowed(userId, "admin_departments");
    return orgDepartmentService.listAdmin(page, pageSize, subjectCode, keyword);
  }

  @PostMapping("/sync-from-employees")
  public Map<String, Object> syncFromEmployees(@CurrentUser String userId) {
    menuPermissionService.assertMenuAllowed(userId, "admin_departments");
    return orgDepartmentService.syncFromEmployees();
  }

  @PostMapping
  public Map<String, Object> create(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    menuPermissionService.assertMenuAllowed(userId, "admin_departments");
    String subjectCode = body.get("subjectCode") == null ? null : String.valueOf(body.get("subjectCode"));
    String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
    Integer sortOrder = null;
    Object so = body.get("sortOrder");
    if (so != null && !String.valueOf(so).trim().isEmpty()) {
      sortOrder = Integer.valueOf(String.valueOf(so).trim());
    }
    return orgDepartmentService.createDepartment(subjectCode, name, sortOrder);
  }

  @PatchMapping("/{id}")
  public Map<String, Boolean> update(
      @CurrentUser String userId, @PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    menuPermissionService.assertMenuAllowed(userId, "admin_departments");
    String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
    Integer sortOrder = null;
    Object so = body.get("sortOrder");
    if (so != null && !String.valueOf(so).trim().isEmpty()) {
      sortOrder = Integer.valueOf(String.valueOf(so).trim());
    }
    orgDepartmentService.updateDepartment(id, name, sortOrder);
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", true);
    return out;
  }

  @DeleteMapping("/{id}")
  public Map<String, Boolean> delete(@CurrentUser String userId, @PathVariable("id") String id) {
    menuPermissionService.assertMenuAllowed(userId, "admin_departments");
    orgDepartmentService.deleteDepartment(id);
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", true);
    return out;
  }
}
