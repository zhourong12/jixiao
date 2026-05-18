package com.jixiao2.server.performance;

import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.web.CurrentUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/performance-feishu-task-config")
public class PerformanceFeishuTaskConfigController {

  private final MenuPermissionService menuPermissionService;
  private final PerformanceFeishuTaskService performanceFeishuTaskService;

  public PerformanceFeishuTaskConfigController(
      MenuPermissionService menuPermissionService, PerformanceFeishuTaskService performanceFeishuTaskService) {
    this.menuPermissionService = menuPermissionService;
    this.performanceFeishuTaskService = performanceFeishuTaskService;
  }

  @GetMapping
  public Map<String, Object> get(@CurrentUser String userId) {
    menuPermissionService.assertMenuAllowed(userId, "admin_performance_feishu_task");
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("enabled", performanceFeishuTaskService.isFeishuTaskEnabled());
    out.put("items", performanceFeishuTaskService.listNodeConfig(userId));
    return out;
  }

  @SuppressWarnings("unchecked")
  @PatchMapping
  public Map<String, Object> patch(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    menuPermissionService.assertMenuAllowed(userId, "admin_performance_feishu_task");
    Object enabledObj = body.get("enabled");
    if (enabledObj != null) {
      boolean on =
          enabledObj instanceof Boolean
              ? (Boolean) enabledObj
              : "1".equals(String.valueOf(enabledObj).trim())
                  || "true".equalsIgnoreCase(String.valueOf(enabledObj).trim());
      performanceFeishuTaskService.setFeishuTaskEnabled(on);
    }
    List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
    performanceFeishuTaskService.updateNodeConfigs(userId, items);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", true);
    return out;
  }
}
