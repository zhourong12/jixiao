package com.jixiao2.server.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jixiao2.server.feishu.FeishuRegistryService.FeishuImAppRow;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 飞书工作台应用角标（网页应用 {@code web_app}）。 */
@Service
public class FeishuAppBadgeService {

  private static final Logger log = LoggerFactory.getLogger(FeishuAppBadgeService.class);
  public static final String KEY_APP_BADGE_ENABLED = "feishu_app_badge_enabled";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern BADGE_VERSION_CONFLICT =
      Pattern.compile("latest version:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

  /** 同一用户角标 version 须单调递增，避免并发用时间戳冲突。 */
  private final ConcurrentHashMap<String, AtomicLong> badgeVersionByOpenId =
      new ConcurrentHashMap<String, AtomicLong>();

  private final JdbcTemplate jdbc;
  private final FeishuRegistryService registry;
  private final FeishuImService feishuIm;

  public FeishuAppBadgeService(
      JdbcTemplate jdbc, FeishuRegistryService registry, FeishuImService feishuIm) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.feishuIm = feishuIm;
  }

  public boolean isAppBadgeEnabled() {
    try {
      String v =
          jdbc.queryForObject(
              "SELECT config_value FROM system_config WHERE config_key = ? LIMIT 1",
              String.class,
              KEY_APP_BADGE_ENABLED);
      if (v == null || v.trim().isEmpty()) {
        return true;
      }
      String t = v.trim();
      return "1".equals(t) || "true".equalsIgnoreCase(t);
    } catch (Exception e) {
      return true;
    }
  }

  public void setAppBadgeEnabled(boolean enabled) {
    String val = enabled ? "1" : "0";
    Integer exists =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM system_config WHERE config_key = ?",
            Integer.class,
            KEY_APP_BADGE_ENABLED);
    if (exists != null && exists > 0) {
      jdbc.update(
          "UPDATE system_config SET config_value = ? WHERE config_key = ?",
          val,
          KEY_APP_BADGE_ENABLED);
    } else {
      jdbc.update(
          "INSERT INTO system_config (config_key, config_value) VALUES (?, ?)",
          KEY_APP_BADGE_ENABLED,
          val);
    }
  }

  /**
   * 为指定主体下的用户设置网页应用角标数。
   *
   * @param subjectId feishu_subject.id
   * @param openId 登录/网页应用下的 open_id
   * @param badgeNum 待办数量，0 清除角标
   */
  public boolean setWebAppBadge(String subjectId, String openId, int badgeNum) {
    if (!isAppBadgeEnabled()) {
      log.info("飞书应用角标跳过 subjectId={} openId={} reason=feishu_app_badge_enabled 关闭", subjectId, openId);
      return false;
    }
    if (subjectId == null
        || subjectId.trim().isEmpty()
        || openId == null
        || openId.trim().isEmpty()) {
      log.info(
          "飞书应用角标跳过 subjectId={} openId={} reason=参数为空",
          subjectId,
          openId);
      return false;
    }
    int n = Math.max(0, Math.min(badgeNum, 2147483647));
    java.util.Optional<FeishuImAppRow> loginApp = registry.findDirectoryAppForSubjectId(subjectId.trim());
    if (!loginApp.isPresent()) {
      log.info("飞书应用角标跳过 subjectId={} reason=未配置登录应用", subjectId);
      return false;
    }
    FeishuImAppRow app = loginApp.get();
    String appId = app.getAppId();
    String appSecret = app.getAppSecret() == null ? "" : app.getAppSecret().trim();
    if (appSecret.isEmpty()) {
      log.warn("飞书应用角标跳过 subjectId={} appId={} reason=登录应用 app_secret 为空", subjectId, appId);
      return false;
    }
    try {
      String token = feishuIm.fetchTenantAccessToken(appId, appSecret);
      String oid = openId.trim();
      long version = nextBadgeVersion(oid);
      log.info(
          "飞书应用角标请求 subjectId={} appId={} openId={} badgeNum={} version={}",
          subjectId.trim(),
          appId,
          oid,
          n,
          version);
      JsonNode res = postBadgeSet(token, oid, n, version);
      int code = res.path("code").asInt(-1);
      if (code == 213001) {
        Long latest = parseBadgeVersionConflictLatest(res.path("msg").asText(""));
        if (latest != null) {
          long retryVersion = Math.max(version + 1, latest + 1);
          badgeVersionByOpenId.put(oid, new AtomicLong(retryVersion));
          log.info(
              "飞书应用角标 version 冲突，重试 subjectId={} openId={} retryVersion={}",
              subjectId.trim(),
              oid,
              retryVersion);
          res = postBadgeSet(token, oid, n, retryVersion);
          code = res.path("code").asInt(-1);
          version = retryVersion;
        }
      }
      if (code != 0) {
        log.warn(
            "飞书应用角标设置失败 subjectId={} appId={} openId={} badgeNum={} version={} feishu_code={} msg={} response={}",
            subjectId.trim(),
            appId,
            oid,
            n,
            version,
            code,
            res.path("msg").asText(""),
            res.toString());
        return false;
      }
      log.info(
          "飞书应用角标已设置 subjectId={} appId={} openId={} badgeNum={} version={}",
          subjectId.trim(),
          appId,
          oid,
          n,
          version);
      return true;
    } catch (ResponseStatusException e) {
      log.warn(
          "飞书应用角标设置失败 subjectId={} appId={} openId={} badgeNum={} reason={}",
          subjectId.trim(),
          appId,
          openId.trim(),
          n,
          e.getReason());
      return false;
    } catch (Exception e) {
      log.warn(
          "飞书应用角标设置异常 subjectId={} appId={} openId={} badgeNum={}",
          subjectId.trim(),
          appId,
          openId.trim(),
          n,
          e);
      return false;
    }
  }

  private long nextBadgeVersion(String openId) {
    return badgeVersionByOpenId
        .computeIfAbsent(openId, k -> new AtomicLong(System.currentTimeMillis()))
        .incrementAndGet();
  }

  private static Long parseBadgeVersionConflictLatest(String msg) {
    if (msg == null || msg.isEmpty()) {
      return null;
    }
    Matcher m = BADGE_VERSION_CONFLICT.matcher(msg);
    if (!m.find()) {
      return null;
    }
    try {
      return Long.parseLong(m.group(1));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private JsonNode postBadgeSet(String token, String openId, int badgeNum, long version)
      throws Exception {
    ObjectNode pc = MAPPER.createObjectNode();
    pc.put("web_app", badgeNum);
    ObjectNode mobile = MAPPER.createObjectNode();
    mobile.put("web_app", badgeNum);
    ObjectNode body = MAPPER.createObjectNode();
    body.put("user_id", openId);
    body.put("version", String.valueOf(version));
    body.put("extra", "{}");
    body.set("pc", pc);
    body.set("mobile", mobile);
    return postJson(
        "https://open.feishu.cn/open-apis/application/v6/app_badge/set?user_id_type=open_id",
        MAPPER.writeValueAsString(body),
        token);
  }

  private static JsonNode postJson(String url, String body, String bearer) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    if (bearer != null) {
      conn.setRequestProperty("Authorization", "Bearer " + bearer);
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    try (OutputStream os = conn.getOutputStream()) {
      os.write(bytes);
    }
    return readJson(conn);
  }

  private static JsonNode readJson(HttpURLConnection conn) throws Exception {
    int code = conn.getResponseCode();
    BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      sb.append(line);
    }
    reader.close();
    return MAPPER.readTree(sb.toString());
  }
}
