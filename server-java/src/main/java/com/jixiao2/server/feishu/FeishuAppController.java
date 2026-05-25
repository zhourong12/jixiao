package com.jixiao2.server.feishu;

import com.jixiao2.server.home.HomeService;
import com.jixiao2.server.web.CurrentUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feishu")
public class FeishuAppController {

  private static final Logger log = LoggerFactory.getLogger(FeishuAppController.class);

  private final FeishuJssdkService jssdkService;
  private final FeishuBadgeSyncService badgeSyncService;
  private final FeishuBadgeClientLogService badgeClientLogService;
  private final FeishuAppBadgeService appBadgeService;
  private final HomeService homeService;

  public FeishuAppController(
      FeishuJssdkService jssdkService,
      FeishuBadgeSyncService badgeSyncService,
      FeishuBadgeClientLogService badgeClientLogService,
      FeishuAppBadgeService appBadgeService,
      HomeService homeService) {
    this.jssdkService = jssdkService;
    this.badgeSyncService = badgeSyncService;
    this.badgeClientLogService = badgeClientLogService;
    this.appBadgeService = appBadgeService;
    this.homeService = homeService;
  }

  @GetMapping("/app-badge/enabled")
  public Map<String, Object> appBadgeEnabled() {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("enabled", appBadgeService.isAppBadgeEnabled());
    return out;
  }

  @GetMapping("/jssdk-config")
  public Map<String, Object> jssdkConfig(
      @CurrentUser String userId, @RequestParam("url") String url) {
    log.info("飞书 JSSDK 鉴权请求 employeeId={} pageUrl={}", userId, url);
    Map<String, Object> cfg = jssdkService.buildConfigForEmployee(userId, url);
    log.info(
        "飞书 JSSDK 鉴权响应 employeeId={} appId={} timestamp={}",
        userId,
        cfg.get("appId"),
        cfg.get("timestamp"));
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.putAll(cfg);
    return out;
  }

  @PostMapping("/badge-client-log")
  public Map<String, Object> badgeClientLog(
      @CurrentUser String userId, @RequestBody Map<String, Object> body) {
    List<String> lines = new ArrayList<String>();
    Object raw = body != null ? body.get("lines") : null;
    if (raw instanceof List) {
      for (Object o : (List<?>) raw) {
        if (o != null) {
          lines.add(String.valueOf(o));
        }
      }
    } else if (body != null && body.get("message") != null) {
      lines.add(String.valueOf(body.get("message")));
    }
    int written = badgeClientLogService.append(userId, lines);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("ok", true);
    out.put("written", written);
    out.put("logFile", "logs/feishu-badge-client.log");
    return out;
  }

  @PostMapping("/app-badge/sync")
  public Map<String, Object> syncAppBadge(@CurrentUser String userId) {
    log.info("飞书应用角标手动同步 API employeeId={}", userId);
    if (!appBadgeService.isAppBadgeEnabled()) {
      Map<String, Object> out = new LinkedHashMap<String, Object>();
      out.put("success", false);
      out.put("badgeNum", 0);
      out.put("skipped", true);
      out.put("reason", "feishu_app_badge_enabled 关闭");
      return out;
    }
    boolean ok = badgeSyncService.syncForEmployeeId(userId, "api_manual");
    int count = homeService.countTodos(userId, null, null);
    log.info("飞书应用角标手动同步 API 完成 employeeId={} todoCount={} success={}", userId, count, ok);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", ok);
    out.put("badgeNum", count);
    return out;
  }
}
