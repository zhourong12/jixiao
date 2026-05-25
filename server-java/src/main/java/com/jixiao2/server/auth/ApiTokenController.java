package com.jixiao2.server.auth;

import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.web.CurrentUser;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/api-tokens")
public class ApiTokenController {

  private final ApiTokenService apiTokenService;
  private final MenuPermissionService menuPermissionService;

  public ApiTokenController(
      ApiTokenService apiTokenService, MenuPermissionService menuPermissionService) {
    this.apiTokenService = apiTokenService;
    this.menuPermissionService = menuPermissionService;
  }

  @GetMapping
  public Map<String, Object> list(@CurrentUser String userId) {
    assertAllowed(userId);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", apiTokenService.listTokens(userId));
    return out;
  }

  @PostMapping
  public Map<String, Object> create(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    assertAllowed(userId);
    String name = body.get("name") == null ? "" : String.valueOf(body.get("name"));
    LocalDateTime expiresAt = parseExpiresAt(body.get("expiresAt"));
    return apiTokenService.generateToken(userId, name, expiresAt);
  }

  @DeleteMapping("/{id}")
  public Map<String, Boolean> delete(@CurrentUser String userId, @PathVariable("id") Long id) {
    assertAllowed(userId);
    apiTokenService.deleteToken(userId, id);
    return Collections.singletonMap("success", Boolean.TRUE);
  }

  private void assertAllowed(String userId) {
    menuPermissionService.assertMenuAllowed(userId, "admin_api_tokens");
  }

  private static LocalDateTime parseExpiresAt(Object value) {
    if (value == null) {
      return null;
    }
    String raw = String.valueOf(value).trim();
    if (raw.isEmpty()) {
      return null;
    }
    try {
      return LocalDateTime.parse(raw);
    } catch (DateTimeParseException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "过期时间格式不正确");
    }
  }
}
