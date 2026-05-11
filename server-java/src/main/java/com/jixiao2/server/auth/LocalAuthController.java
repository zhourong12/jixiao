package com.jixiao2.server.auth;

import com.jixiao2.server.employee.EmployeeLookupService;
import com.jixiao2.server.employee.EmployeeLookupService.EmployeeRow;
import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.security.SessionTokenCodec;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocalAuthController {

  static final String SESSION_COOKIE = "jx_session";
  private static final Duration SESSION_MAX_AGE = Duration.ofDays(7);

  private final SessionTokenCodec sessionTokenCodec;
  private final EmployeeLookupService employees;
  private final MenuPermissionService menuPermissionService;
  private final org.springframework.core.env.Environment environment;

  public LocalAuthController(
      SessionTokenCodec sessionTokenCodec,
      EmployeeLookupService employees,
      MenuPermissionService menuPermissionService,
      org.springframework.core.env.Environment environment) {
    this.sessionTokenCodec = sessionTokenCodec;
    this.employees = employees;
    this.menuPermissionService = menuPermissionService;
    this.environment = environment;
  }

  @PostMapping("/auth/password/login")
  public ResponseEntity<Map<String, Object>> passwordLogin(
      @RequestBody Map<String, String> body) {
    if (!sessionTokenCodec.hasSecret()) {
      return ResponseEntity.ok(
          Map.of("success", false, "message", "未配置 SESSION_JWT_SECRET，无法签发会话"));
    }
    String username = body.getOrDefault("username", "");
    String password = body.getOrDefault("password", "");
    employees.assertPassword(password);
    EmployeeRow row = employees.resolveByUsername(username);
    String display = employees.displayName(row, username.trim());
    List<String> roles = menuPermissionService.getRoleKeysForUser(row.employeeId());
    if (roles.isEmpty()) {
      roles = List.of("employee");
    } else {
      roles = List.copyOf(roles);
    }
    String cookieVal = sessionTokenCodec.sign(row.employeeId(), display, roles, null);
    if (cookieVal == null) {
      return ResponseEntity.ok(
          Map.of("success", false, "message", "未配置 SESSION_JWT_SECRET，无法签发会话"));
    }
    boolean prod =
        java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")
            || java.util.Arrays.asList(environment.getActiveProfiles()).contains("production");
    ResponseCookie cookie =
        ResponseCookie.from(SESSION_COOKIE, cookieVal)
            .httpOnly(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(SESSION_MAX_AGE)
            .secure(prod)
            .build();
    return ResponseEntity.ok().header("Set-Cookie", cookie.toString()).body(Map.of("success", true));
  }
}
