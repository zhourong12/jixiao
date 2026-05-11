package com.jixiao2.server.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MenuPermissionService {

  private final JdbcTemplate jdbc;

  public MenuPermissionService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<String> getRoleKeysForUser(String userId) {
    return jdbc.query(
        "SELECT role_key FROM user_role WHERE user_id = ?",
        (rs, rowNum) -> rs.getString("role_key"),
        userId);
  }

  /** super_admin > admin > 单角色 > 多角色时第一个非 employee > employee */
  public String getUserRole(String userId) {
    List<String> keys = getRoleKeysForUser(userId);
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

  public MenuPermissionsMe getEffectiveMenusForUser(String userId) {
    List<String> roleKeys = getRoleKeysForUser(userId);
    List<String> effective = roleKeys.isEmpty() ? List.of("employee") : new ArrayList<>(roleKeys);
    String primaryRole = getUserRole(userId);

    if (effective.contains("super_admin")) {
      return new MenuPermissionsMe(primaryRole, effective, MenuKeys.allTrue());
    }

    Map<String, Boolean> menus = new LinkedHashMap<>(MenuKeys.allFalse());
    if (effective.isEmpty()) {
      return new MenuPermissionsMe(primaryRole, effective, menus);
    }
    String placeholders = String.join(",", effective.stream().map(k -> "?").toList());
    String sql =
        "SELECT menu_key, allowed FROM role_menu WHERE role_key IN (" + placeholders + ")";
    jdbc.query(
        sql,
        rs -> {
          String mk = rs.getString("menu_key");
          int allowed = rs.getInt("allowed");
          if (menus.containsKey(mk) && allowed != 0) {
            menus.put(mk, true);
          }
        },
        effective.toArray());

    menus.put("performance_export", false);
    menus.put("performance_batch_create", false);
    menus.put("admin_performance_calibration", false);

    return new MenuPermissionsMe(primaryRole, effective, Map.copyOf(menus));
  }

  public record MenuPermissionsMe(String role, List<String> roles, Map<String, Boolean> menus) {}
}
