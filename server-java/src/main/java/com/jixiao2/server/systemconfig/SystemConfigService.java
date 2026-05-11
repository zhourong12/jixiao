package com.jixiao2.server.systemconfig;

import com.jixiao2.server.menu.MenuPermissionService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemConfigService {

  private static final List<Map<String, Object>> CONFIG_SCHEMA = new ArrayList<Map<String, Object>>();

  static {
    Map<String, Object> w1 = new LinkedHashMap<String, Object>();
    w1.put("key", "manager_review_weight");
    w1.put("label", "直属上级评审权重");
    w1.put("description", "直属上级评分在总分中的占比，取值 0~1（如 0.7 表示 70%）");
    w1.put("type", "number");
    CONFIG_SCHEMA.add(w1);
    Map<String, Object> w2 = new LinkedHashMap<String, Object>();
    w2.put("key", "dotted_manager_review_weight");
    w2.put("label", "虚线上级评审权重");
    w2.put("description", "虚线上级评分在总分中的占比，取值 0~1（如 0.3 表示 30%）");
    w2.put("type", "number");
    CONFIG_SCHEMA.add(w2);
  }

  private final JdbcTemplate jdbc;
  private final MenuPermissionService menuPermissionService;

  public SystemConfigService(JdbcTemplate jdbc, MenuPermissionService menuPermissionService) {
    this.jdbc = jdbc;
    this.menuPermissionService = menuPermissionService;
  }

  public Map<String, Object> getAll(String userId) {
    menuPermissionService.assertMenuAllowed(userId, "admin_system_config");
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT config_key, config_value FROM system_config",
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("key", rs.getString("config_key"));
              m.put("value", rs.getString("config_value"));
              return m;
            });
    Map<String, String> valueMap = new LinkedHashMap<String, String>();
    for (Map<String, Object> row : rows) {
      valueMap.put(String.valueOf(row.get("key")), String.valueOf(row.get("value")));
    }
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> schema : CONFIG_SCHEMA) {
      Map<String, Object> item = new LinkedHashMap<String, Object>(schema);
      String key = String.valueOf(schema.get("key"));
      item.put("value", valueMap.containsKey(key) ? valueMap.get(key) : "");
      items.add(item);
    }
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    return out;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Boolean> update(String userId, Map<String, Object> body) {
    menuPermissionService.assertMenuAllowed(userId, "admin_system_config");
    Set<String> validKeys = new HashSet<String>();
    for (Map<String, Object> schema : CONFIG_SCHEMA) {
      validKeys.add(String.valueOf(schema.get("key")));
    }
    Object configsObj = body.get("configs");
    if (!(configsObj instanceof List)) {
      Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
      out.put("success", Boolean.TRUE);
      return out;
    }
    for (Object itemObj : (List<Object>) configsObj) {
      if (!(itemObj instanceof Map)) {
        continue;
      }
      Map<String, Object> item = (Map<String, Object>) itemObj;
      String key = String.valueOf(item.get("key"));
      if (!validKeys.contains(key)) {
        continue;
      }
      String value = String.valueOf(item.get("value"));
      String type = null;
      for (Map<String, Object> schema : CONFIG_SCHEMA) {
        if (key.equals(String.valueOf(schema.get("key")))) {
          type = String.valueOf(schema.get("type"));
          break;
        }
      }
      if ("number".equals(type)) {
        try {
          Double.parseDouble(value);
        } catch (NumberFormatException e) {
          continue;
        }
      }
      Integer count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM system_config WHERE config_key = ?", Integer.class, key);
      if (count != null && count > 0) {
        jdbc.update("UPDATE system_config SET config_value = ? WHERE config_key = ?", value, key);
      } else {
        jdbc.update("INSERT INTO system_config (config_key, config_value) VALUES (?,?)", key, value);
      }
    }
    Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
    out.put("success", Boolean.TRUE);
    return out;
  }
}
