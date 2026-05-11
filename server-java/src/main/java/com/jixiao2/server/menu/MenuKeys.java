package com.jixiao2.server.menu;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 与 {@code shared/api.interface.ts} 中 {@code MENU_PERMISSION_KEYS} 顺序与键名一致。 */
public final class MenuKeys {

  public static final List<String> ALL =
      Collections.unmodifiableList(
          Arrays.asList(
              "todo",
              "home",
              "performance_list",
              "performance_export",
              "performance_list_all",
              "performance_batch_create",
              "performance_review_admin",
              "admin_performance_calibration",
              "my_performance",
              "admin_templates",
              "admin_notifications",
              "admin_employees",
              "admin_roles",
              "admin_permissions",
              "admin_statistics_months",
              "admin_system_config"));

  private MenuKeys() {}

  public static Map<String, Boolean> allTrue() {
    Map<String, Boolean> m = new LinkedHashMap<String, Boolean>();
    for (String k : ALL) {
      m.put(k, Boolean.TRUE);
    }
    return Collections.unmodifiableMap(m);
  }

  public static Map<String, Boolean> allFalse() {
    Map<String, Boolean> m = new LinkedHashMap<String, Boolean>();
    for (String k : ALL) {
      m.put(k, Boolean.FALSE);
    }
    return Collections.unmodifiableMap(m);
  }
}
