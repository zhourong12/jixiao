package com.jixiao2.server.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MenuPermissionService {

  private static final Pattern ROLE_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,47}$");

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
    List<String> effective =
        roleKeys.isEmpty()
            ? Collections.singletonList("employee")
            : new ArrayList<String>(roleKeys);
    String primaryRole = getUserRole(userId);

    if (effective.contains("super_admin")) {
      return new MenuPermissionsMe(primaryRole, effective, MenuKeys.allTrue());
    }

    Map<String, Boolean> menus = new LinkedHashMap<String, Boolean>(MenuKeys.allFalse());
    if (effective.isEmpty()) {
      return new MenuPermissionsMe(primaryRole, effective, menus);
    }
    String placeholders = String.join(",", Collections.nCopies(effective.size(), "?"));
    String sql =
        "SELECT menu_key, allowed FROM role_menu WHERE role_key IN (" + placeholders + ")";
    jdbc.query(
        sql,
        rs -> {
          String mk = rs.getString("menu_key");
          int allowed = rs.getInt("allowed");
          if (menus.containsKey(mk) && allowed != 0) {
            menus.put(mk, Boolean.TRUE);
          }
        },
        effective.toArray());

    menus.put("performance_export", Boolean.FALSE);
    menus.put("performance_batch_create", Boolean.FALSE);
    menus.put("admin_performance_calibration", Boolean.FALSE);

    return new MenuPermissionsMe(
        primaryRole, effective, Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(menus)));
  }

  public void assertMenuAllowed(String userId, String menuKey) {
    MenuPermissionsMe perm = getEffectiveMenusForUser(userId);
    Boolean ok = perm.getMenus().get(menuKey);
    if (!Boolean.TRUE.equals(ok)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无此操作权限");
    }
  }

  /** 是否具备某菜单权限（不抛错） */
  public boolean isMenuAllowed(String userId, String menuKey) {
    if (userId == null || userId.isEmpty()) {
      return false;
    }
    MenuPermissionsMe perm = getEffectiveMenusForUser(userId);
    return Boolean.TRUE.equals(perm.getMenus().get(menuKey));
  }

  public void assertSuperAdmin(String role) {
    assertSuperAdmin(role, "仅超级管理员可配置菜单权限");
  }

  public void assertSuperAdmin(String role, String message) {
    if (!"super_admin".equals(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
  }

  public void assertCanManageEmployeeRoles(String requesterId) {
    String role = getUserRole(requesterId);
    if (!"admin".equals(role) && !"super_admin".equals(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可设置员工系统角色");
    }
  }

  /** JSON / 反序列化可能对布尔给出不同表示，统一为是否放行。 */
  private static boolean toAllowedFlag(Object v) {
    if (v == null) {
      return false;
    }
    if (Boolean.TRUE.equals(v)) {
      return true;
    }
    if (Boolean.FALSE.equals(v)) {
      return false;
    }
    if (v instanceof Number) {
      return ((Number) v).intValue() != 0;
    }
    String s = String.valueOf(v).trim();
    return "true".equalsIgnoreCase(s) || "1".equals(s);
  }

  public Map<String, Object> getMatrix(String requesterId) {
    assertSuperAdmin(getUserRole(requesterId));

    List<Map<String, Object>> roleRows =
        jdbc.query(
            "SELECT role_key, name, is_system, sort_order FROM role ORDER BY sort_order",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("roleKey", rs.getString("role_key"));
              m.put("name", rs.getString("name"));
              m.put("isSystem", rs.getInt("is_system") != 0);
              m.put("sortOrder", rs.getInt("sort_order"));
              return m;
            });

    List<String> roles = new ArrayList<String>();
    List<Map<String, Object>> roleInfos = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> r : roleRows) {
      roles.add((String) r.get("roleKey"));
      Map<String, Object> info = new LinkedHashMap<String, Object>();
      info.put("roleKey", r.get("roleKey"));
      info.put("name", r.get("name"));
      info.put("isSystem", r.get("isSystem"));
      roleInfos.add(info);
    }

    final Map<String, Integer> sortByKey = new LinkedHashMap<String, Integer>();
    jdbc.query(
        "SELECT menu_key, sort_order FROM menu",
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> sortByKey.put(rs.getString("menu_key"), rs.getInt("sort_order")));

    List<String> menus = new ArrayList<String>(MenuKeys.ALL);
    Collections.sort(
        menus,
        (a, b) -> {
          int sa = sortByKey.containsKey(a) ? sortByKey.get(a) : 10_000;
          int sb = sortByKey.containsKey(b) ? sortByKey.get(b) : 10_000;
          if (sa != sb) {
            return Integer.compare(sa, sb);
          }
          return a.compareTo(b);
        });

    Map<String, Map<String, Boolean>> matrix = new LinkedHashMap<String, Map<String, Boolean>>();
    for (String rk : roles) {
      Map<String, Boolean> row = new LinkedHashMap<String, Boolean>();
      for (String mk : MenuKeys.ALL) {
        row.put(mk, Boolean.FALSE);
      }
      matrix.put(rk, row);
    }

    jdbc.query(
        "SELECT role_key, menu_key, allowed FROM role_menu",
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> {
              String rk = rs.getString("role_key");
              String mk = rs.getString("menu_key");
              if (mk != null) {
                mk = mk.trim();
              }
              int allowed = rs.getInt("allowed");
              if (!matrix.containsKey(rk) || !MenuKeys.ALL.contains(mk)) {
                return;
              }
              matrix.get(rk).put(mk, allowed != 0);
            });

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("roles", roles);
    out.put("roleInfos", roleInfos);
    out.put("menus", menus);
    out.put("matrix", matrix);
    return out;
  }

  public void updateRoleMenus(String requesterId, Map<String, Object> body) {
    assertSuperAdmin(getUserRole(requesterId));
    String role = body.get("role") == null ? "" : String.valueOf(body.get("role")).trim();
    if ("super_admin".equals(role)) {
      return;
    }
    Integer cnt =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM role WHERE role_key = ?",
            Integer.class,
            role);
    if (cnt == null || cnt == 0) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "未知角色");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> menus = (Map<String, Object>) body.get("menus");
    if (menus == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体缺少 menus");
    }
    List<String> superOnly =
        java.util.Arrays.asList(
            "performance_export", "performance_batch_create", "admin_performance_calibration");
    for (Map.Entry<String, Object> e : menus.entrySet()) {
      String menuKey = e.getKey() == null ? "" : String.valueOf(e.getKey()).trim();
      if (menuKey.isEmpty()) {
        continue;
      }
      if (!MenuKeys.ALL.contains(menuKey)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "未知菜单项: "
                + menuKey
                + "。请确认后端已包含该 menu_key（与 MenuKeys.ALL / sync-menu-table.sql 一致），并重新部署。");
      }
      boolean allowed = toAllowedFlag(e.getValue());
      if (superOnly.contains(menuKey)) {
        if (allowed) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "导出绩效、批量创建与「绩效校准（上级评分）」仅超级管理员可用，不可授予其他角色");
        }
        jdbc.update(
            "INSERT INTO role_menu (role_key, menu_key, allowed) VALUES (?,?,0) "
                + "ON DUPLICATE KEY UPDATE allowed=0",
            role,
            menuKey);
        continue;
      }
      jdbc.update(
          "INSERT INTO role_menu (role_key, menu_key, allowed) VALUES (?,?,?) "
              + "ON DUPLICATE KEY UPDATE allowed=VALUES(allowed)",
          role,
          menuKey,
          allowed ? 1 : 0);
    }
  }

  public List<Map<String, Object>> listRbacRoles(String requesterId) {
    assertSuperAdmin(getUserRole(requesterId));
    return jdbc.query(
        "SELECT role_key, name, is_system, sort_order FROM role ORDER BY sort_order",
        (rs, rn) -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("roleKey", rs.getString("role_key"));
          m.put("name", rs.getString("name"));
          m.put("isSystem", rs.getInt("is_system") != 0);
          m.put("sortOrder", rs.getInt("sort_order"));
          return m;
        });
  }

  public void createRbacRole(String requesterId, Map<String, Object> body) {
    assertSuperAdmin(getUserRole(requesterId));
    String roleKey = String.valueOf(body.getOrDefault("roleKey", "")).trim();
    String name = String.valueOf(body.getOrDefault("name", "")).trim();
    if (!ROLE_KEY_PATTERN.matcher(roleKey).matches()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "角色标识须为小写字母开头，仅含小写字母、数字、下划线，长度 2～48");
    }
    if (name.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写角色名称");
    }
    Integer dup =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM role WHERE role_key = ?", Integer.class, roleKey);
    if (dup != null && dup > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该角色标识已存在");
    }
    int sortOrder;
    if (body.containsKey("sortOrder") && body.get("sortOrder") instanceof Number) {
      sortOrder = ((Number) body.get("sortOrder")).intValue();
    } else {
      Integer max =
          jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0) FROM role", Integer.class);
      sortOrder = (max == null ? 0 : max) + 10;
    }
    jdbc.update(
        "INSERT INTO role (role_key, name, is_system, sort_order) VALUES (?,?,0,?)",
        roleKey,
        name,
        sortOrder);
    for (String mk : MenuKeys.ALL) {
      jdbc.update(
          "INSERT INTO role_menu (role_key, menu_key, allowed) VALUES (?,?,0)", roleKey, mk);
    }
  }

  public void updateRbacRole(String requesterId, String roleKey, Map<String, Object> body) {
    assertSuperAdmin(getUserRole(requesterId));
    Integer ex =
        jdbc.queryForObject("SELECT COUNT(*) FROM role WHERE role_key = ?", Integer.class, roleKey);
    if (ex == null || ex == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在");
    }
    boolean touched = false;
    if (body.containsKey("name")) {
      String n = String.valueOf(body.get("name")).trim();
      if (n.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色名称不能为空");
      }
      jdbc.update("UPDATE role SET name = ? WHERE role_key = ?", n, roleKey);
      touched = true;
    }
    if (body.containsKey("sortOrder") && body.get("sortOrder") instanceof Number) {
      jdbc.update(
          "UPDATE role SET sort_order = ? WHERE role_key = ?",
          ((Number) body.get("sortOrder")).intValue(),
          roleKey);
      touched = true;
    }
    if (!touched) {
      // no-op
    }
  }

  public void deleteRbacRole(String requesterId, String roleKey) {
    assertSuperAdmin(getUserRole(requesterId));
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT is_system FROM role WHERE role_key = ? LIMIT 1",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("isSystem", rs.getInt("is_system"));
              return m;
            },
            roleKey);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在");
    }
    if (((Number) rows.get(0).get("isSystem")).intValue() != 0) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "系统内置角色不可删除");
    }
    Integer usage =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE role_key = ?", Integer.class, roleKey);
    if (usage != null && usage > 0) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仍有用户绑定该角色，无法删除");
    }
    jdbc.update("DELETE FROM role_menu WHERE role_key = ?", roleKey);
    jdbc.update("DELETE FROM role WHERE role_key = ?", roleKey);
  }

  /** 与 Nest MenuPermissionsMeResponse 对齐。 */
  public static final class MenuPermissionsMe {
    private final String role;
    private final List<String> roles;
    private final Map<String, Boolean> menus;

    public MenuPermissionsMe(String role, List<String> roles, Map<String, Boolean> menus) {
      this.role = role;
      this.roles = roles;
      this.menus = menus;
    }

    public String getRole() {
      return role;
    }

    public List<String> getRoles() {
      return roles;
    }

    public Map<String, Boolean> getMenus() {
      return menus;
    }
  }
}
