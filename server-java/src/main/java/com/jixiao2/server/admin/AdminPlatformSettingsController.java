package com.jixiao2.server.admin;

import com.jixiao2.server.auth.AuthConfigService;
import com.jixiao2.server.feishu.FeishuAppBadgeService;
import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.performance.PerformanceFeishuTaskService;
import com.jixiao2.server.web.CurrentUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统配置页：飞书应用角标、飞书绩效待办等全局开关（与 {@code systemconfig} 包内评审权重配置 API 分离）。 */
@RestController
@RequestMapping("/api/admin/platform-settings")
public class AdminPlatformSettingsController {

  private final MenuPermissionService menuPermissionService;
  private final PerformanceFeishuTaskService performanceFeishuTaskService;
  private final FeishuAppBadgeService feishuAppBadgeService;
  private final AuthConfigService authConfigService;

  public AdminPlatformSettingsController(
      MenuPermissionService menuPermissionService,
      PerformanceFeishuTaskService performanceFeishuTaskService,
      FeishuAppBadgeService feishuAppBadgeService,
      AuthConfigService authConfigService) {
    this.menuPermissionService = menuPermissionService;
    this.performanceFeishuTaskService = performanceFeishuTaskService;
    this.feishuAppBadgeService = feishuAppBadgeService;
    this.authConfigService = authConfigService;
  }

  @GetMapping
  public Map<String, Object> get(@CurrentUser String userId) {
    menuPermissionService.assertMenuAllowed(userId, "admin_performance_feishu_task");
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("appBadgeEnabled", feishuAppBadgeService.isAppBadgeEnabled());
    out.put("feishuTaskEnabled", performanceFeishuTaskService.isFeishuTaskEnabled());
    out.put("feishuTaskItems", performanceFeishuTaskService.listNodeConfig(userId));
    out.put("passwordLoginEnabled", authConfigService.isPasswordLoginEnabled());
    return out;
  }

  @SuppressWarnings("unchecked")
  @PatchMapping
  public Map<String, Object> patch(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    menuPermissionService.assertMenuAllowed(userId, "admin_performance_feishu_task");
    Object badgeObj = body.get("appBadgeEnabled");
    if (badgeObj != null) {
      feishuAppBadgeService.setAppBadgeEnabled(parseEnabled(badgeObj));
    }
    Object taskObj = body.get("feishuTaskEnabled");
    if (taskObj != null) {
      performanceFeishuTaskService.setFeishuTaskEnabled(parseEnabled(taskObj));
    }
    List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("feishuTaskItems");
    if (items != null) {
      performanceFeishuTaskService.updateNodeConfigs(userId, items);
    }
    Object pwdObj = body.get("passwordLoginEnabled");
    if (pwdObj != null) {
      authConfigService.setPasswordLoginEnabled(parseEnabled(pwdObj));
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    return out;
  }

  private static boolean parseEnabled(Object raw) {
    if (raw instanceof Boolean) {
      return (Boolean) raw;
    }
    String t = String.valueOf(raw).trim();
    return "1".equals(t) || "true".equalsIgnoreCase(t);
  }
}
