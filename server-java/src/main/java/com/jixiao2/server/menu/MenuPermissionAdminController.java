package com.jixiao2.server.menu;

import com.jixiao2.server.web.CurrentUser;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/menu-permissions")
public class MenuPermissionAdminController {

  private final MenuPermissionService menuPermissionService;

  public MenuPermissionAdminController(MenuPermissionService menuPermissionService) {
    this.menuPermissionService = menuPermissionService;
  }

  @GetMapping("/matrix")
  public Map<String, Object> matrix(@CurrentUser String userId) {
    return menuPermissionService.getMatrix(userId);
  }

  @PutMapping
  public Map<String, Boolean> update(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    menuPermissionService.updateRoleMenus(userId, body);
    return Collections.singletonMap("success", Boolean.TRUE);
  }

  @GetMapping("/roles")
  public Map<String, Object> listRoles(@CurrentUser String userId) {
    List<Map<String, Object>> items = menuPermissionService.listRbacRoles(userId);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  @PostMapping("/roles")
  public Map<String, Boolean> createRole(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    menuPermissionService.createRbacRole(userId, body);
    return Collections.singletonMap("success", Boolean.TRUE);
  }

  @PatchMapping("/roles/{roleKey}")
  public Map<String, Boolean> patchRole(
      @CurrentUser String userId,
      @PathVariable("roleKey") String roleKey,
      @RequestBody Map<String, Object> body) {
    menuPermissionService.updateRbacRole(userId, roleKey, body);
    return Collections.singletonMap("success", Boolean.TRUE);
  }

  @DeleteMapping("/roles/{roleKey}")
  public Map<String, Boolean> removeRole(@CurrentUser String userId, @PathVariable("roleKey") String roleKey) {
    menuPermissionService.deleteRbacRole(userId, roleKey);
    return Collections.singletonMap("success", Boolean.TRUE);
  }
}
