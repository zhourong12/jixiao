package com.jixiao2.server.api;

import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.menu.MenuPermissionService.MenuPermissionsMe;
import com.jixiao2.server.security.SessionPayload;
import com.jixiao2.server.security.SessionTokenCodec;
import com.jixiao2.server.web.SessionAuthInterceptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.jixiao2.server.web.SessionCookieSupport;
import javax.servlet.http.HttpServletRequest;
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
  public Map<String, Object> me(
      HttpServletRequest request,
      @CookieValue(name = SESSION_COOKIE, required = false) String rawCookie) {
    String userId = attr(request, SessionAuthInterceptor.ATTR_USER_ID);
    String name = attr(request, SessionAuthInterceptor.ATTR_USER_NAME);
    if (userId == null || userId.isEmpty()) {
      SessionPayload p = rawCookie == null ? null : codec.verify(rawCookie);
      if (p != null && p.getSub() != null && !p.getSub().isEmpty()) {
        userId = p.getSub();
        name = p.getName();
      }
    }
    if (userId == null || userId.isEmpty()) {
      return Collections.singletonMap("authenticated", Boolean.FALSE);
    }
    MenuPermissionsMe perm = menuPermissionService.getEffectiveMenusForUser(userId);
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("authenticated", Boolean.TRUE);
    body.put("user_id", userId);
    body.put(
        "name",
        name == null || name.isEmpty() ? userId : name);
    body.put("roles", perm.getRoles());
    body.put("role", perm.getRole());
    body.put("menus", perm.getMenus());
    return body;
  }

  private static String attr(HttpServletRequest request, String key) {
    Object v = request.getAttribute(key);
    return v == null ? null : String.valueOf(v);
  }

  @PostMapping("/api/session/logout")
  public ResponseEntity<Map<String, Boolean>> logout() {
    return ResponseEntity.ok()
        .header("Set-Cookie", sessionCookieSupport.clearSessionCookie().toString())
        .body(Collections.singletonMap("success", Boolean.TRUE));
  }
}
