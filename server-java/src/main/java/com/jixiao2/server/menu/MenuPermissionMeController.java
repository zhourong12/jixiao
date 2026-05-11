package com.jixiao2.server.menu;

import com.jixiao2.server.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu-permissions")
public class MenuPermissionMeController {

  private final MenuPermissionService menuPermissionService;

  public MenuPermissionMeController(MenuPermissionService menuPermissionService) {
    this.menuPermissionService = menuPermissionService;
  }

  @GetMapping("/me")
  public MenuPermissionService.MenuPermissionsMe me(@CurrentUser String userId) {
    return menuPermissionService.getEffectiveMenusForUser(userId);
  }
}
