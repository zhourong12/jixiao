package com.jixiao2.server.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiao2.server.config.Jixiao2Properties;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 使用自建应用 tenant_access_token 调用飞书 IM，按 open_id 向用户发文本或交互卡片消息（需在开放平台为应用申请发消息相关权限）。
 */
@Service
public class FeishuImService {

  private static final Logger log = LoggerFactory.getLogger(FeishuImService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  /** 提前 60 秒刷新 tenant_access_token，避免边界过期。 */
  private static final long TOKEN_REFRESH_BUFFER_MS = 60_000L;

  private final Jixiao2Properties properties;
  private final ConcurrentHashMap<String, TenantTokenCache> tenantTokenByAppId =
      new ConcurrentHashMap<String, TenantTokenCache>();
  private final ConcurrentHashMap<String, Object> tenantTokenLocks =
      new ConcurrentHashMap<String, Object>();

  public FeishuImService(Jixiao2Properties properties) {
    this.properties = properties;
  }

  public String fetchTenantAccessToken() {
    String appId = properties.getFeishu().getAppId();
    String appSecret = properties.getFeishu().getAppSecret();
    if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置飞书应用凭证");
    }
    return fetchTenantAccessToken(appId, appSecret);
  }

  /** 清除缓存的 tenant_access_token，token 失效后强制重新拉取。 */
  public void invalidateTenantAccessToken(String appId) {
    if (appId == null) {
      return;
    }
    String id = appId.trim();
    if (!id.isEmpty()) {
      tenantTokenByAppId.remove(id);
    }
  }

  /** 飞书返回码或文案表明 access token 无效/过期时，可刷新 token 后重试一次。 */
  public static boolean isTokenRelatedFailure(int feishuCode, String msg) {
    if (feishuCode == 99991663 || feishuCode == 99991668 || feishuCode == 99991661) {
      return true;
    }
    if (msg == null || msg.isEmpty()) {
      return false;
    }
    String m = msg.toLowerCase();
    return m.contains("access token")
        || m.contains("tenant access token")
        || m.contains("invalid token")
        || m.contains("token expired")
        || m.contains("token invalid")
        || m.contains("token is invalid")
        || m.contains("authorization");
  }

  public String fetchTenantAccessToken(String appId, String appSecret) {
    String id = appId == null ? "" : appId.trim();
    String secret = appSecret == null ? "" : appSecret.trim();
    if (id.isEmpty() || secret.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置飞书应用凭证");
    }
    long now = System.currentTimeMillis();
    TenantTokenCache cached = tenantTokenByAppId.get(id);
    if (cached != null && cached.expiresAtMs > now) {
      return cached.token;
    }
    Object lock = tenantTokenLocks.computeIfAbsent(id, k -> new Object());
    synchronized (lock) {
      cached = tenantTokenByAppId.get(id);
      if (cached != null && cached.expiresAtMs > now) {
        return cached.token;
      }
      TenantTokenCache fresh = requestTenantAccessTokenUncached(id, secret);
      tenantTokenByAppId.put(id, fresh);
      return fresh.token;
    }
  }

  private TenantTokenCache requestTenantAccessTokenUncached(String appId, String appSecret) {
    try {
      JsonNode res =
          postJson(
              "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",
              "{\"app_id\":\""
                  + escapeJson(appId)
                  + "\",\"app_secret\":\""
                  + escapeJson(appSecret)
                  + "\"}",
              null);
      int feishuCode = res.path("code").asInt(-1);
      if (feishuCode != 0) {
        String msg = res.path("msg").asText("飞书 tenant token 失败");
        log.warn(
            "飞书 tenant token 接口返回错误 appId={} feishu_code={} msg={} response={}",
            appId,
            feishuCode,
            msg,
            res.toString());
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, msg + " (feishu_code=" + feishuCode + ")");
      }
      String tok = res.path("tenant_access_token").asText("");
      if (tok.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书 tenant token 为空");
      }
      int expireSec = res.path("expire").asInt(7200);
      long expiresAtMs =
          System.currentTimeMillis()
              + Math.max(60, expireSec) * 1000L
              - TOKEN_REFRESH_BUFFER_MS;
      return new TenantTokenCache(tok, expiresAtMs);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.warn("飞书 tenant token 请求异常 appId={}", appId, e);
      String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "飞书 tenant token 失败: " + detail);
    }
  }

  private static final class TenantTokenCache {
    private final String token;
    private final long expiresAtMs;

    private TenantTokenCache(String token, long expiresAtMs) {
      this.token = token;
      this.expiresAtMs = expiresAtMs;
    }
  }

  /**
   * 登录/通讯录应用与发 IM 的应用不同时，库里的 {@code open_id} 属于前者；通过 {@code union_id} 换出 IM 应用下的
   * {@code open_id}，避免 IM 接口报 {@code open_id cross app}。需双方应用具备通讯录读用户能力。
   */
  public Optional<String> convertOpenIdToImAppUser(
      String directoryAppId,
      String directoryAppSecret,
      String sourceOpenId,
      String imAppId,
      String imAppSecret) {
    return convertOpenIdBetweenApps(
        directoryAppId, directoryAppSecret, sourceOpenId, imAppId, imAppSecret);
  }

  public static final class ConvertOpenIdResult {
    private final Optional<String> openId;
    private final String errorDetail;

    private ConvertOpenIdResult(Optional<String> openId, String errorDetail) {
      this.openId = openId;
      this.errorDetail = errorDetail;
    }

    public static ConvertOpenIdResult ok(String openId) {
      return new ConvertOpenIdResult(Optional.of(openId), null);
    }

    public static ConvertOpenIdResult fail(String errorDetail) {
      return new ConvertOpenIdResult(Optional.empty(), errorDetail);
    }

    public Optional<String> getOpenId() {
      return openId;
    }

    public String getErrorDetail() {
      return errorDetail;
    }
  }

  /** 通过 union_id 将源应用 open_id 转换为目标应用 open_id。 */
  public Optional<String> convertOpenIdBetweenApps(
      String sourceAppId,
      String sourceAppSecret,
      String sourceOpenId,
      String targetAppId,
      String targetAppSecret) {
    return convertOpenIdBetweenAppsWithDetail(
            sourceAppId, sourceAppSecret, sourceOpenId, targetAppId, targetAppSecret)
        .getOpenId();
  }

  /** 同上，失败时返回飞书错误详情（用于日志与接口 failures）。 */
  public ConvertOpenIdResult convertOpenIdBetweenAppsWithDetail(
      String sourceAppId,
      String sourceAppSecret,
      String sourceOpenId,
      String targetAppId,
      String targetAppSecret) {
    if (sourceOpenId == null || sourceOpenId.trim().isEmpty()) {
      return ConvertOpenIdResult.fail("sourceOpenId 为空");
    }
    if (sourceAppId == null
        || sourceAppId.isEmpty()
        || sourceAppSecret == null
        || sourceAppSecret.isEmpty()) {
      return ConvertOpenIdResult.fail("源应用凭证未配置");
    }
    if (targetAppId == null || targetAppId.isEmpty() || targetAppSecret == null || targetAppSecret.isEmpty()) {
      return ConvertOpenIdResult.fail("目标应用凭证未配置");
    }
    String oid = sourceOpenId.trim();
    if (!oid.startsWith("ou_")) {
      return ConvertOpenIdResult.fail("sourceOpenId 非 ou_ 格式");
    }
    if (sourceAppId.equals(targetAppId)) {
      return ConvertOpenIdResult.ok(oid);
    }
    try {
      String srcTok = fetchTenantAccessToken(sourceAppId, sourceAppSecret);
      JsonNode j1 = getContactUser(srcTok, "open_id", oid);
      int code1 = j1.path("code").asInt(-1);
      if (code1 != 0) {
        String detail = feishuErr("step1_登录应用查open_id", sourceAppId, code1, j1);
        log.warn("飞书 open_id 映射 step1 失败 sourceOpenId={} {}", oid, detail);
        return ConvertOpenIdResult.fail(detail);
      }
      String union = j1.path("data").path("user").path("union_id").asText("").trim();
      if (union.isEmpty()) {
        String detail = "step1_登录应用查open_id 成功但 union_id 为空 sourceAppId=" + sourceAppId;
        log.warn("飞书 open_id 映射 step1 union_id 为空 sourceOpenId={} {}", oid, detail);
        return ConvertOpenIdResult.fail(detail);
      }
      String tgtTok = fetchTenantAccessToken(targetAppId, targetAppSecret);
      JsonNode j2 = getContactUser(tgtTok, "union_id", union);
      int code2 = j2.path("code").asInt(-1);
      if (code2 != 0) {
        String detail = feishuErr("step2_网页应用查union_id", targetAppId, code2, j2);
        log.warn(
            "飞书 open_id 映射 step2 失败 sourceOpenId={} unionId={} {}",
            oid,
            union,
            detail);
        return ConvertOpenIdResult.fail(detail);
      }
      String out = j2.path("data").path("user").path("open_id").asText("").trim();
      if (out.isEmpty()) {
        String detail = "step2_网页应用查union_id 成功但 open_id 为空 targetAppId=" + targetAppId;
        log.warn("飞书 open_id 映射 step2 open_id 为空 unionId={} {}", union, detail);
        return ConvertOpenIdResult.fail(detail);
      }
      return ConvertOpenIdResult.ok(out);
    } catch (Exception e) {
      String detail = "异常: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      log.warn("飞书 open_id 映射异常 sourceOpenId={} {}", oid, detail, e);
      return ConvertOpenIdResult.fail(detail);
    }
  }

  private static String feishuErr(String step, String appId, int code, JsonNode res) {
    return step
        + " appId="
        + appId
        + " feishu_code="
        + code
        + " msg="
        + res.path("msg").asText("")
        + " log_id="
        + res.path("error").path("log_id").asText(res.path("log_id").asText(""));
  }

  private static JsonNode getContactUser(String tenantToken, String userIdType, String userId)
      throws Exception {
    String enc = URLEncoder.encode(userId, StandardCharsets.UTF_8.name());
    String typeEnc = URLEncoder.encode(userIdType, StandardCharsets.UTF_8.name());
    String url =
        "https://open.feishu.cn/open-apis/contact/v3/users/" + enc + "?user_id_type=" + typeEnc;
    return getJson(url, tenantToken);
  }

  private static JsonNode getJson(String url, String bearer) throws Exception {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("GET");
    if (bearer != null) {
      conn.setRequestProperty("Authorization", "Bearer " + bearer);
    }
    return readJson(conn);
  }

  /**
   * @param receiveOpenId 与通讯录一致的 open_id；从飞书同步的员工其 employee_id 即为 open_id
   * @return 错误信息，成功返回 null
   */
  public String sendTextToUser(String tenantToken, String receiveOpenId, String text) {
    ImSendResult r = sendTextToUserResult(tenantToken, receiveOpenId, text);
    return r.success() ? null : r.errorMsg;
  }

  /** token 失效时刷新 tenant_access_token 并重试一次。 */
  public String sendTextToUserWithRetry(
      String appId, String appSecret, String receiveOpenId, String text) {
    String token = fetchTenantAccessToken(appId, appSecret);
    ImSendResult r = sendTextToUserResult(token, receiveOpenId, text);
    if (r.success() || !isTokenRelatedFailure(r.feishuCode, r.errorMsg)) {
      return r.success() ? null : r.errorMsg;
    }
    log.info("飞书发文本 token 失效，刷新后重试 appId={} feishu_code={}", appId, r.feishuCode);
    invalidateTenantAccessToken(appId);
    token = fetchTenantAccessToken(appId, appSecret);
    r = sendTextToUserResult(token, receiveOpenId, text);
    return r.success() ? null : r.errorMsg;
  }

  /**
   * 发送飞书交互卡片（{@code msg_type=interactive}）。{@code cardRoot} 为卡片 JSON 1.0 根对象（如 {@code
   * config}/{@code header}/{@code elements}，可选 {@code card_link}），将序列化为 {@code content} 字符串。
   *
   * @return 错误信息，成功返回 null
   */
  public String sendInteractiveCard(String tenantToken, String receiveOpenId, Map<String, ?> cardRoot) {
    ImSendResult r = sendInteractiveCardResult(tenantToken, receiveOpenId, cardRoot);
    return r.success() ? null : r.errorMsg;
  }

  /** token 失效时刷新 tenant_access_token 并重试一次。 */
  public String sendInteractiveCardWithRetry(
      String appId, String appSecret, String receiveOpenId, Map<String, ?> cardRoot) {
    String token = fetchTenantAccessToken(appId, appSecret);
    ImSendResult r = sendInteractiveCardResult(token, receiveOpenId, cardRoot);
    if (r.success() || !isTokenRelatedFailure(r.feishuCode, r.errorMsg)) {
      return r.success() ? null : r.errorMsg;
    }
    log.info("飞书发卡片 token 失效，刷新后重试 appId={} feishu_code={}", appId, r.feishuCode);
    invalidateTenantAccessToken(appId);
    token = fetchTenantAccessToken(appId, appSecret);
    r = sendInteractiveCardResult(token, receiveOpenId, cardRoot);
    return r.success() ? null : r.errorMsg;
  }

  private static final class ImSendResult {
    private final int feishuCode;
    private final String errorMsg;

    private ImSendResult(int feishuCode, String errorMsg) {
      this.feishuCode = feishuCode;
      this.errorMsg = errorMsg;
    }

    private static ImSendResult ok() {
      return new ImSendResult(0, null);
    }

    private static ImSendResult fail(int feishuCode, String errorMsg) {
      return new ImSendResult(feishuCode, errorMsg);
    }

    private boolean success() {
      return feishuCode == 0;
    }
  }

  private ImSendResult sendTextToUserResult(String tenantToken, String receiveOpenId, String text) {
    if (receiveOpenId == null || receiveOpenId.trim().isEmpty()) {
      return ImSendResult.fail(-1, "接收人为空");
    }
    try {
      Map<String, Object> inner = new LinkedHashMap<String, Object>();
      inner.put("text", text == null ? "" : text);
      Map<String, Object> body = new LinkedHashMap<String, Object>();
      body.put("receive_id", receiveOpenId.trim());
      body.put("msg_type", "text");
      body.put("content", MAPPER.writeValueAsString(inner));
      String payload = MAPPER.writeValueAsString(body);
      JsonNode res =
          postJson(
              "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id",
              payload,
              tenantToken);
      int code = res.path("code").asInt(-1);
      if (code != 0) {
        return ImSendResult.fail(code, res.path("msg").asText("飞书发消息失败"));
      }
      return ImSendResult.ok();
    } catch (Exception e) {
      return ImSendResult.fail(-1, e.getMessage() == null ? "飞书发消息异常" : e.getMessage());
    }
  }

  private ImSendResult sendInteractiveCardResult(
      String tenantToken, String receiveOpenId, Map<String, ?> cardRoot) {
    if (receiveOpenId == null || receiveOpenId.trim().isEmpty()) {
      return ImSendResult.fail(-1, "接收人为空");
    }
    try {
      Map<String, Object> body = new LinkedHashMap<String, Object>();
      body.put("receive_id", receiveOpenId.trim());
      body.put("msg_type", "interactive");
      body.put("content", MAPPER.writeValueAsString(Objects.requireNonNull(cardRoot, "cardRoot")));
      String payload = MAPPER.writeValueAsString(body);
      JsonNode res =
          postJson(
              "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id",
              payload,
              tenantToken);
      int code = res.path("code").asInt(-1);
      if (code != 0) {
        return ImSendResult.fail(code, res.path("msg").asText("飞书发消息失败"));
      }
      return ImSendResult.ok();
    } catch (Exception e) {
      return ImSendResult.fail(-1, e.getMessage() == null ? "飞书发消息异常" : e.getMessage());
    }
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

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
