package com.jixiao2.server.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiao2.server.config.Jixiao2Properties;
import com.jixiao2.server.menu.MenuPermissionService;
import com.jixiao2.server.security.SessionTokenCodec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FeishuAuthService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbc;
  private final Jixiao2Properties properties;
  private final MenuPermissionService menuPermissionService;
  private final SessionTokenCodec sessionTokenCodec;

  public FeishuAuthService(
      JdbcTemplate jdbc,
      Jixiao2Properties properties,
      MenuPermissionService menuPermissionService,
      SessionTokenCodec sessionTokenCodec) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.menuPermissionService = menuPermissionService;
    this.sessionTokenCodec = sessionTokenCodec;
  }

  public String resolveAppId() {
    String appId = properties.getFeishu().getAppId();
    if (appId == null || appId.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "未配置 FEISHU_APP_ID");
    }
    return appId.trim();
  }

  public String resolveRedirectUri() {
    String redirect = properties.getFeishu().getRedirectUri();
    if (redirect != null && !redirect.trim().isEmpty()) {
      return redirect.trim();
    }
    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "生产环境请配置 FEISHU_REDIRECT_URI");
  }

  public String exchangeCodeForUserAccessToken(String code) {
    String appId = properties.getFeishu().getAppId();
    String appSecret = properties.getFeishu().getAppSecret();
    if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未配置飞书应用凭证");
    }
    try {
      JsonNode json =
          postJson(
              "https://open.feishu.cn/open-apis/authen/v1/access_token",
              "{\"grant_type\":\"authorization_code\",\"code\":\""
                  + escapeJson(code)
                  + "\",\"app_id\":\""
                  + escapeJson(appId)
                  + "\",\"app_secret\":\""
                  + escapeJson(appSecret)
                  + "\"}",
              null);
      if (json.path("code").asInt(-1) != 0 || !json.path("data").has("access_token")) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, json.path("msg").asText("飞书授权换票失败"));
      }
      return json.path("data").path("access_token").asText();
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书授权换票失败");
    }
  }

  public Map<String, String> fetchFeishuUserInfo(String accessToken) {
    try {
      HttpURLConnection conn =
          (HttpURLConnection) new URL("https://open.feishu.cn/open-apis/authen/v1/user_info").openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Authorization", "Bearer " + accessToken);
      JsonNode json = readJson(conn);
      if (json.path("code").asInt(-1) != 0 || !json.path("data").has("open_id")) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, json.path("msg").asText("获取飞书用户信息失败"));
      }
      JsonNode data = json.path("data");
      String name = data.path("name").asText("").trim();
      if (name.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书账号缺少姓名，无法匹配员工");
      }
      Map<String, String> out = new LinkedHashMap<String, String>();
      out.put("name", name);
      out.put("openId", data.path("open_id").asText());
      return out;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "获取飞书用户信息失败");
    }
  }

  public Map<String, String> resolveEmployeeByFeishu(String openId, String feishuName) {
    List<Map<String, String>> byOpen =
        jdbc.query(
            "SELECT employee_id, name FROM employee_hierarchy WHERE employee_id = ? LIMIT 1",
            new Object[] {openId},
            (rs, rn) -> {
              Map<String, String> m = new LinkedHashMap<String, String>();
              m.put("employeeId", rs.getString("employee_id"));
              m.put("name", rs.getString("name"));
              return m;
            });
    if (!byOpen.isEmpty()) {
      Map<String, String> row = byOpen.get(0);
      String name = row.get("name");
      if (name == null || name.trim().isEmpty()) {
        name = feishuName;
      }
      Map<String, String> out = new LinkedHashMap<String, String>();
      out.put("employeeId", row.get("employeeId"));
      out.put("name", name.trim());
      return out;
    }
    List<Map<String, String>> byName =
        jdbc.query(
            "SELECT employee_id, name FROM employee_hierarchy WHERE name = ?",
            new Object[] {feishuName},
            (rs, rn) -> {
              Map<String, String> m = new LinkedHashMap<String, String>();
              m.put("employeeId", rs.getString("employee_id"));
              m.put("name", rs.getString("name"));
              return m;
            });
    if (byName.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "未找到与飞书姓名「" + feishuName + "」或 open_id 对应的员工，请在员工管理中维护");
    }
    if (byName.size() > 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "存在多名员工姓名为「" + feishuName + "」，请用飞书 open_id 作为员工编号同步");
    }
    Map<String, String> row = byName.get(0);
    String name = row.get("name");
    if (name == null || name.trim().isEmpty()) {
      name = feishuName;
    }
    java.util.Map<String, String> out = new java.util.LinkedHashMap<String, String>();
    out.put("employeeId", row.get("employeeId"));
    out.put("name", name.trim());
    return out;
  }

  public List<String> loadRolesForUser(String employeeId) {
    List<String> keys = menuPermissionService.getRoleKeysForUser(employeeId);
    if (keys.isEmpty()) {
      return new ArrayList<String>(Arrays.asList("employee"));
    }
    return new ArrayList<String>(keys);
  }

  public String buildSessionCookieValue(
      String employeeId, String displayName, List<String> roles, String openId) {
    return sessionTokenCodec.sign(employeeId, displayName, roles, openId);
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
