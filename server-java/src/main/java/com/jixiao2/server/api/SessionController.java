package com.jixiao2.server.api;

import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.menu.MenuPermissionService.MenuPermissionsMe;
import com.jixiao2.server.security.SessionPayload;
import com.jixiao2.server.security.SessionTokenCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {

  static final String SESSION_COOKIE = "jx_session";

  private final SessionTokenCodec codec;
  private final MenuPermissionService menuPermissionService;

  public SessionController(SessionTokenCodec codec, MenuPermissionService menuPermissionService) {
    this.codec = codec;
    this.menuPermissionService = menuPermissionService;
  }

  @GetMapping("/api/session/me")
  public Map<String, Object> me(@CookieValue(name = SESSION_COOKIE, required = false) String rawCookie) {
    SessionPayload p = rawCookie == null ? null : codec.verify(rawCookie);
    if (p == null || p.sub() == null || p.sub().isEmpty()) {
      return Map.of("authenticated", false);
    }
    MenuPermissionsMe perm = menuPermissionService.getEffectiveMenusForUser(p.sub());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("authenticated", true);
    body.put("user_id", p.sub());
    body.put("name", p.name() == null || p.name().isEmpty() ? p.sub() : p.name());
    body.put("roles", perm.roles());
    body.put("role", perm.role());
    body.put("menus", perm.menus());
    return body;
  }

  @PostMapping("/api/session/logout")
  public ResponseEntity<Map<String, Boolean>> logout() {
    ResponseCookie clear =
        ResponseCookie.from(SESSION_COOKIE, "")
            .httpOnly(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build();
    return ResponseEntity.ok().header("Set-Cookie", clear.toString()).body(Map.of("success", true));
  }
}
