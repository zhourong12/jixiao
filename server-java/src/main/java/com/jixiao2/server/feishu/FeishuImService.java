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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 使用自建应用 tenant_access_token 调用飞书 IM，按 open_id 向用户发文本或交互卡片消息（需在开放平台为应用申请发消息相关权限）。
 */
@Service
public class FeishuImService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Jixiao2Properties properties;

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

  public String fetchTenantAccessToken(String appId, String appSecret) {
    if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置飞书应用凭证");
    }
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
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, msg + " (feishu_code=" + feishuCode + ")");
      }
      String tok = res.path("tenant_access_token").asText("");
      if (tok.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书 tenant token 为空");
      }
      return tok;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书 tenant token 失败");
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
    if (sourceOpenId == null || sourceOpenId.trim().isEmpty()) {
      return Optional.empty();
    }
    if (directoryAppId == null
        || directoryAppId.isEmpty()
        || directoryAppSecret == null
        || directoryAppSecret.isEmpty()) {
      return Optional.empty();
    }
    if (imAppId == null || imAppId.isEmpty() || imAppSecret == null || imAppSecret.isEmpty()) {
      return Optional.empty();
    }
    String oid = sourceOpenId.trim();
    if (!oid.startsWith("ou_")) {
      return Optional.empty();
    }
    if (directoryAppId.equals(imAppId)) {
      return Optional.of(oid);
    }
    try {
      String dirTok = fetchTenantAccessToken(directoryAppId, directoryAppSecret);
      JsonNode j1 = getContactUser(dirTok, "open_id", oid);
      if (j1.path("code").asInt(-1) != 0) {
        return Optional.empty();
      }
      String union = j1.path("data").path("user").path("union_id").asText("").trim();
      if (union.isEmpty()) {
        return Optional.empty();
      }
      String imTok = fetchTenantAccessToken(imAppId, imAppSecret);
      JsonNode j2 = getContactUser(imTok, "union_id", union);
      if (j2.path("code").asInt(-1) != 0) {
        return Optional.empty();
      }
      String out = j2.path("data").path("user").path("open_id").asText("").trim();
      return out.isEmpty() ? Optional.empty() : Optional.of(out);
    } catch (Exception e) {
      return Optional.empty();
    }
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
    if (receiveOpenId == null || receiveOpenId.trim().isEmpty()) {
      return "接收人为空";
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
      if (res.path("code").asInt(-1) != 0) {
        return res.path("msg").asText("飞书发消息失败");
      }
      return null;
    } catch (Exception e) {
      return e.getMessage() == null ? "飞书发消息异常" : e.getMessage();
    }
  }

  /**
   * 发送飞书交互卡片（{@code msg_type=interactive}）。{@code cardRoot} 为卡片 JSON 1.0 根对象（如 {@code
   * config}/{@code header}/{@code elements}，可选 {@code card_link}），将序列化为 {@code content} 字符串。
   *
   * @return 错误信息，成功返回 null
   */
  public String sendInteractiveCard(String tenantToken, String receiveOpenId, Map<String, ?> cardRoot) {
    if (receiveOpenId == null || receiveOpenId.trim().isEmpty()) {
      return "接收人为空";
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
      if (res.path("code").asInt(-1) != 0) {
        return res.path("msg").asText("飞书发消息失败");
      }
      return null;
    } catch (Exception e) {
      return e.getMessage() == null ? "飞书发消息异常" : e.getMessage();
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
