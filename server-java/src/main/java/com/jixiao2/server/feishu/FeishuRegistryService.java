package com.jixiao2.server.feishu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 读取 {@code feishu_subject} / {@code feishu_app}（库内配置），供 OAuth、通讯录、IM 使用。
 */
@Service
public class FeishuRegistryService {

  private final JdbcTemplate jdbc;

  public FeishuRegistryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 有启用登录应用的主体，用于登录页下拉（不含密钥）。 */
  public List<Map<String, Object>> listLoginSubjectOptions() {
    return jdbc.query(
        "SELECT s.code, s.name FROM feishu_subject s "
            + "INNER JOIN feishu_app a ON a.feishu_subject_id = s.id AND a.enabled = 1 AND a.is_login_app = 1 "
            + "WHERE s.enabled = 1 AND TRIM(a.app_id) <> '' AND TRIM(a.app_secret) <> '' AND TRIM(a.redirect_uri) <> '' "
            + "ORDER BY s.sort_order ASC, s.code ASC",
        (rs, rn) -> {
          Map<String, Object> m = new LinkedHashMap<String, Object>();
          m.put("code", rs.getString("code"));
          m.put("name", rs.getString("name"));
          return m;
        });
  }

  public Optional<FeishuLoginAppRow> findLoginAppBySubjectCode(String subjectCode) {
    if (subjectCode == null || subjectCode.trim().isEmpty()) {
      return Optional.empty();
    }
    String code = subjectCode.trim();
    List<FeishuLoginAppRow> rows =
        jdbc.query(
            "SELECT a.app_id, a.app_secret, a.redirect_uri FROM feishu_app a "
                + "INNER JOIN feishu_subject s ON s.id = a.feishu_subject_id "
                + "WHERE s.code = ? AND s.enabled = 1 AND a.enabled = 1 AND a.is_login_app = 1 "
                + "ORDER BY a.sort_order ASC LIMIT 1",
            new Object[] {code},
            (rs, rn) ->
                new FeishuLoginAppRow(
                    rs.getString("app_id"),
                    rs.getString("app_secret"),
                    rs.getString("redirect_uri")));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public Optional<String> findSubjectIdByCode(String subjectCode) {
    if (subjectCode == null || subjectCode.trim().isEmpty()) {
      return Optional.empty();
    }
    List<String> ids =
        jdbc.query(
            "SELECT id FROM feishu_subject WHERE code = ? AND enabled = 1 LIMIT 1",
            new Object[] {subjectCode.trim()},
            (rs, rn) -> rs.getString(1));
    return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
  }

  /** IM：优先 is_im_app，否则回退 is_login_app。 */
  public Optional<FeishuImAppRow> findImAppForSubjectId(String subjectId) {
    if (subjectId == null || subjectId.trim().isEmpty()) {
      return Optional.empty();
    }
    List<FeishuImAppRow> rows =
        jdbc.query(
            "SELECT app_id, app_secret FROM feishu_app "
                + "WHERE feishu_subject_id = ? AND enabled = 1 AND (is_im_app = 1 OR is_login_app = 1) "
                + "ORDER BY is_im_app DESC, sort_order ASC LIMIT 1",
            new Object[] {subjectId.trim()},
            (rs, rn) -> new FeishuImAppRow(rs.getString("app_id"), rs.getString("app_secret")));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /** 通讯录同步等：使用登录应用换 tenant token（与登录同一应用最常见）。 */
  public Optional<FeishuImAppRow> findDirectoryAppForSubjectId(String subjectId) {
    if (subjectId == null || subjectId.trim().isEmpty()) {
      return Optional.empty();
    }
    List<FeishuImAppRow> rows =
        jdbc.query(
            "SELECT app_id, app_secret FROM feishu_app "
                + "WHERE feishu_subject_id = ? AND enabled = 1 AND is_login_app = 1 "
                + "ORDER BY sort_order ASC LIMIT 1",
            new Object[] {subjectId.trim()},
            (rs, rn) -> new FeishuImAppRow(rs.getString("app_id"), rs.getString("app_secret")));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public Optional<String> findSubjectIdByEmployeeId(String employeeId) {
    if (employeeId == null || employeeId.trim().isEmpty()) {
      return Optional.empty();
    }
    List<String> ids =
        jdbc.query(
            "SELECT feishu_subject_id FROM employee_hierarchy WHERE employee_id = ? LIMIT 1",
            new Object[] {employeeId.trim()},
            (rs, rn) -> rs.getString(1));
    if (ids.isEmpty()) {
      return Optional.empty();
    }
    String sid = ids.get(0);
    if (sid == null || sid.trim().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(sid.trim());
  }

  public Optional<FeishuSubjectRow> findSubjectById(String subjectId) {
    if (subjectId == null || subjectId.trim().isEmpty()) {
      return Optional.empty();
    }
    List<FeishuSubjectRow> rows =
        jdbc.query(
            "SELECT id, code, notify_frontend_base_url, notify_feishu_web_app_url, web_app_link_app_id, "
                + "performance_notify_enabled FROM feishu_subject WHERE id = ? LIMIT 1",
            new Object[] {subjectId.trim()},
            (rs, rn) ->
                new FeishuSubjectRow(
                    rs.getString("id"),
                    rs.getString("code"),
                    rs.getString("notify_frontend_base_url"),
                    rs.getString("notify_feishu_web_app_url"),
                    rs.getString("web_app_link_app_id"),
                    rs.getInt("performance_notify_enabled") != 0));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /** 解析 subjectCode → subject UUID（用于 OAuth 回调匹配员工）。 */
  public Optional<String> resolveSubjectIdForLogin(String subjectCode) {
    return findSubjectIdByCode(subjectCode);
  }

  public static final class FeishuLoginAppRow {
    private final String appId;
    private final String appSecret;
    private final String redirectUri;

    public FeishuLoginAppRow(String appId, String appSecret, String redirectUri) {
      this.appId = appId == null ? "" : appId;
      this.appSecret = appSecret == null ? "" : appSecret;
      this.redirectUri = redirectUri == null ? "" : redirectUri;
    }

    public String getAppId() {
      return appId;
    }

    public String getAppSecret() {
      return appSecret;
    }

    public String getRedirectUri() {
      return redirectUri;
    }
  }

  public static final class FeishuImAppRow {
    private final String appId;
    private final String appSecret;

    public FeishuImAppRow(String appId, String appSecret) {
      this.appId = appId == null ? "" : appId;
      this.appSecret = appSecret == null ? "" : appSecret;
    }

    public String getAppId() {
      return appId;
    }

    public String getAppSecret() {
      return appSecret;
    }
  }

  public static final class FeishuSubjectRow {
    private final String id;
    private final String code;
    private final String notifyFrontendBaseUrl;
    private final String notifyFeishuWebAppUrl;
    private final String webAppLinkAppId;
    private final boolean performanceNotifyEnabled;

    public FeishuSubjectRow(
        String id,
        String code,
        String notifyFrontendBaseUrl,
        String notifyFeishuWebAppUrl,
        String webAppLinkAppId,
        boolean performanceNotifyEnabled) {
      this.id = id;
      this.code = code;
      this.notifyFrontendBaseUrl = notifyFrontendBaseUrl;
      this.notifyFeishuWebAppUrl = notifyFeishuWebAppUrl;
      this.webAppLinkAppId = webAppLinkAppId;
      this.performanceNotifyEnabled = performanceNotifyEnabled;
    }

    public String getId() {
      return id;
    }

    public String getCode() {
      return code;
    }

    public String getNotifyFrontendBaseUrl() {
      return notifyFrontendBaseUrl;
    }

    public String getNotifyFeishuWebAppUrl() {
      return notifyFeishuWebAppUrl;
    }

    public String getWebAppLinkAppId() {
      return webAppLinkAppId;
    }

    public boolean isPerformanceNotifyEnabled() {
      return performanceNotifyEnabled;
    }
  }
}
