package com.jixiao2.server.systemconfig;

import com.jixiao2.server.web.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system-config")
public class SystemConfigController {

  private final SystemConfigService systemConfigService;

  public SystemConfigController(SystemConfigService systemConfigService) {
    this.systemConfigService = systemConfigService;
  }

  @GetMapping
  public Map<String, Object> getAll(@CurrentUser String userId) {
    return systemConfigService.getAll(userId);
  }

  @PatchMapping
  public Map<String, Boolean> update(@CurrentUser String userId, @RequestBody Map<String, Object> body) {
    return systemConfigService.update(userId, body);
  }
}
