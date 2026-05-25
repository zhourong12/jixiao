package com.jixiao2.server.performance;

import com.jixiao2.server.admin.AdminPlatformSettingsController;
import com.jixiao2.server.web.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** @deprecated 请使用 {@link AdminPlatformSettingsController} {@code /api/admin/platform-settings} */
@RestController
@RequestMapping("/api/admin/performance-feishu-task-config")
public class PerformanceFeishuTaskConfigController {

  private final AdminPlatformSettingsController platformSettingsController;

  public PerformanceFeishuTaskConfigController(AdminPlatformSettingsController platformSettingsController) {
    this.platformSettingsController = platformSettingsController;
  }

  @GetMapping
  public Map<String, Object> get(@CurrentUser String userId) {
    Map<String, Object> cfg = platformSettingsController.get(userId);
    Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
    out.put("enabled", cfg.get("feishuTaskEnabled"));
    out.put("items", cfg.get("feishuTaskItems"));
    return out;
  }

  @PatchMapping
  public Map<String, Object> patch(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    Map<String, Object> mapped = new java.util.LinkedHashMap<String, Object>();
    if (body.containsKey("enabled")) {
      mapped.put("feishuTaskEnabled", body.get("enabled"));
    }
    if (body.containsKey("items")) {
      mapped.put("feishuTaskItems", body.get("items"));
    }
    return platformSettingsController.patch(userId, mapped);
  }
}
