package com.jixiao2.server.api;

import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.menu.MenuPermissionService.MenuPermissionsMe;
import com.jixiao2.server.security.SessionPayload;
import com.jixiao2.server.security.SessionTokenCodec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.jixiao2.server.web.SessionCookieSupport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {

  public static final String SESSION_COOKIE = "jx_session";

  private final SessionTokenCodec codec;
  private final MenuPermissionService menuPermissionService;
  private final SessionCookieSupport sessionCookieSupport;

  public SessionController(
      SessionTokenCodec codec,
      MenuPermissionService menuPermissionService,
      SessionCookieSupport sessionCookieSupport) {
    this.codec = codec;
    this.menuPermissionService = menuPermissionService;
    this.sessionCookieSupport = sessionCookieSupport;
  }

  @GetMapping("/api/session/me")
  public Map<String, Object> me(@CookieValue(name = SESSION_COOKIE, required = false) String rawCookie) {
    SessionPayload p = rawCookie == null ? null : codec.verify(rawCookie);
    if (p == null || p.getSub() == null || p.getSub().isEmpty()) {
      return Collections.singletonMap("authenticated", Boolean.FALSE);
    }
    MenuPermissionsMe perm = menuPermissionService.getEffectiveMenusForUser(p.getSub());
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("authenticated", Boolean.TRUE);
    body.put("user_id", p.getSub());
    body.put(
        "name",
        p.getName() == null || p.getName().isEmpty() ? p.getSub() : p.getName());
    body.put("roles", perm.getRoles());
    body.put("role", perm.getRole());
    body.put("menus", perm.getMenus());
    return body;
  }

  @PostMapping("/api/session/logout")
  public ResponseEntity<Map<String, Boolean>> logout() {
    return ResponseEntity.ok()
        .header("Set-Cookie", sessionCookieSupport.clearSessionCookie().toString())
        .body(Collections.singletonMap("success", Boolean.TRUE));
  }
}
