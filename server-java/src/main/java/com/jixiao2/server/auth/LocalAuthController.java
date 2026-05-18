package com.jixiao2.server.auth;

import com.jixiao2.server.config.Jixiao2Properties;
import com.jixiao2.server.employee.EmployeeLookupService;
import com.jixiao2.server.employee.EmployeeLookupService.EmployeeRow;
import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.security.SessionTokenCodec;
import com.jixiao2.server.web.SessionCookieSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocalAuthController {

  private static final Duration SESSION_MAX_AGE = Duration.ofDays(7);

  private final SessionTokenCodec sessionTokenCodec;
  private final EmployeeLookupService employees;
  private final MenuPermissionService menuPermissionService;
  private final SessionCookieSupport sessionCookieSupport;
  private final Jixiao2Properties jixiao2Properties;

  public LocalAuthController(
      SessionTokenCodec sessionTokenCodec,
      EmployeeLookupService employees,
      MenuPermissionService menuPermissionService,
      SessionCookieSupport sessionCookieSupport,
      Jixiao2Properties jixiao2Properties) {
    this.sessionTokenCodec = sessionTokenCodec;
    this.employees = employees;
    this.menuPermissionService = menuPermissionService;
    this.sessionCookieSupport = sessionCookieSupport;
    this.jixiao2Properties = jixiao2Properties;
  }

  @PostMapping("/auth/password/login")
  public ResponseEntity<Map<String, Object>> passwordLogin(
      @RequestBody Map<String, String> body) {
    if (!jixiao2Properties.getAuth().isPasswordLoginEnabled()) {
      Map<String, Object> err = new LinkedHashMap<String, Object>();
      err.put("success", Boolean.FALSE);
      err.put("message", "账密登录已关闭，请使用飞书登录");
      return ResponseEntity.status(403).body(err);
    }
    if (!sessionTokenCodec.hasSecret()) {
      Map<String, Object> err = new LinkedHashMap<String, Object>();
      err.put("success", Boolean.FALSE);
      err.put("message", "未配置 SESSION_JWT_SECRET，无法签发会话");
      return ResponseEntity.ok(err);
    }
    String username = body.containsKey("username") ? body.get("username") : "";
    String password = body.containsKey("password") ? body.get("password") : "";
    employees.assertPassword(password);
    EmployeeRow row = employees.resolveByUsername(username);
    String display = employees.displayName(row, username.trim());
    List<String> roles = menuPermissionService.getRoleKeysForUser(row.getEmployeeId());
    if (roles.isEmpty()) {
      roles = new ArrayList<String>(Arrays.asList("employee"));
    } else {
      roles = new ArrayList<String>(roles);
    }
    String cookieVal = sessionTokenCodec.sign(row.getEmployeeId(), display, roles, null);
    if (cookieVal == null) {
      Map<String, Object> err = new LinkedHashMap<String, Object>();
      err.put("success", Boolean.FALSE);
      err.put("message", "未配置 SESSION_JWT_SECRET，无法签发会话");
      return ResponseEntity.ok(err);
    }
    Map<String, Object> ok = new LinkedHashMap<String, Object>();
    ok.put("success", Boolean.TRUE);
    return ResponseEntity.ok()
        .header(
            "Set-Cookie",
            sessionCookieSupport.sessionCookie(cookieVal, SESSION_MAX_AGE).toString())
        .body(ok);
  }
}
