package com.jixiao2.server.auth;

import com.jixiao2.server.admin.AdminPlatformSettingsController;
import com.jixiao2.server.web.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** @deprecated 请使用 {@link AdminPlatformSettingsController} {@code /api/admin/platform-settings} */
@RestController
@RequestMapping("/api/admin/auth-config")
public class AuthConfigController {

  private final AdminPlatformSettingsController platformSettingsController;

  public AuthConfigController(AdminPlatformSettingsController platformSettingsController) {
    this.platformSettingsController = platformSettingsController;
  }

  @GetMapping
  public Map<String, Object> get(@CurrentUser String userId) {
    Map<String, Object> cfg = platformSettingsController.get(userId);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("passwordLoginEnabled", cfg.get("passwordLoginEnabled"));
    return out;
  }

  @PatchMapping
  public Map<String, Object> patch(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    Map<String, Object> mapped = new LinkedHashMap<String, Object>();
    if (body.containsKey("passwordLoginEnabled")) {
      mapped.put("passwordLoginEnabled", body.get("passwordLoginEnabled"));
    }
    platformSettingsController.patch(userId, mapped);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", Boolean.TRUE);
    out.put("passwordLoginEnabled", platformSettingsController.get(userId).get("passwordLoginEnabled"));
    return out;
  }
}
