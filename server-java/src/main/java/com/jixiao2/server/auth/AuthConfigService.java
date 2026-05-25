package com.jixiao2.server.auth;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthConfigService {

  public static final String KEY_PASSWORD_LOGIN_ENABLED = "password_login_enabled";

  private final JdbcTemplate jdbc;

  public AuthConfigService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 是否允许账密登录；库表未配置、为空或为 0/false 时均视为关闭。 */
  public boolean isPasswordLoginEnabled() {
    try {
      List<String> values =
          jdbc.query(
              "SELECT config_value FROM system_config WHERE config_key = ? LIMIT 1",
              (rs, rn) -> rs.getString(1),
              KEY_PASSWORD_LOGIN_ENABLED);
      if (values.isEmpty()) {
        return false;
      }
      return parseEnabledFlag(values.get(0));
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean parseEnabledFlag(String raw) {
    if (raw == null || raw.trim().isEmpty()) {
      return false;
    }
    String t = raw.trim().toLowerCase();
    return "1".equals(t) || "true".equals(t) || "yes".equals(t) || "on".equals(t);
  }

  public void setPasswordLoginEnabled(boolean enabled) {
    String val = enabled ? "1" : "0";
    Integer exists =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM system_config WHERE config_key = ?",
            Integer.class,
            KEY_PASSWORD_LOGIN_ENABLED);
    if (exists != null && exists > 0) {
      jdbc.update(
          "UPDATE system_config SET config_value = ? WHERE config_key = ?",
          val,
          KEY_PASSWORD_LOGIN_ENABLED);
    } else {
      jdbc.update(
          "INSERT INTO system_config (config_key, config_value) VALUES (?, ?)",
          KEY_PASSWORD_LOGIN_ENABLED,
          val);
    }
  }
}
