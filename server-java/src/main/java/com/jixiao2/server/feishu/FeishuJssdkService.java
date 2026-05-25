package com.jixiao2.server.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiao2.server.feishu.FeishuRegistryService.FeishuImAppRow;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 飞书 H5 JSSDK 鉴权（jsapi_ticket + 签名）。 */
@Service
public class FeishuJssdkService {

  private static final Logger log = LoggerFactory.getLogger(FeishuJssdkService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FeishuRegistryService registry;
  private final FeishuImService feishuIm;
  private final ConcurrentHashMap<String, TicketCache> ticketCache = new ConcurrentHashMap<String, TicketCache>();

  public FeishuJssdkService(FeishuRegistryService registry, FeishuImService feishuIm) {
    this.registry = registry;
    this.feishuIm = feishuIm;
  }

  public Map<String, Object> buildConfigForEmployee(String employeeId, String pageUrl) {
    if (employeeId == null || employeeId.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
    String url = normalizeUrl(pageUrl);
    if (url.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 url 参数");
    }
    Optional<String> subjectIdOpt = registry.findSubjectIdByEmployeeId(employeeId.trim());
    if (!subjectIdOpt.isPresent()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前用户未绑定飞书主体");
    }
    String subjectId = subjectIdOpt.get();
    FeishuImAppRow app =
        registry
            .findDirectoryAppForSubjectId(subjectId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "该主体未配置飞书登录应用"));
    String appId = app.getAppId();
    String ticket = fetchJsapiTicket(appId, app.getAppSecret());
    String nonceStr = randomHex(16);
    // h5sdk.config 要求毫秒级 timestamp，签名串须与 config 传入值一致
    long timestamp = System.currentTimeMillis();
    String signature = sign(ticket, nonceStr, timestamp, url);
    log.info(
        "飞书 JSSDK 签名完成 employeeId={} subjectId={} appId={} signUrl={}",
        employeeId.trim(),
        subjectId,
        appId,
        url);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("appId", appId);
    out.put("timestamp", timestamp);
    out.put("nonceStr", nonceStr);
    out.put("signature", signature);
    return out;
  }

  private String fetchJsapiTicket(String appId, String appSecret) {
    String key = appId == null ? "" : appId.trim();
    long now = System.currentTimeMillis();
    TicketCache cached = ticketCache.get(key);
    if (cached != null && cached.expireAtMs > now + 60_000L) {
      return cached.ticket;
    }
    try {
      String token = feishuIm.fetchTenantAccessToken(appId, appSecret);
      HttpURLConnection conn =
          (HttpURLConnection)
              new URL("https://open.feishu.cn/open-apis/jssdk/ticket/get").openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Authorization", "Bearer " + token);
      JsonNode res = readJson(conn);
      int code = res.path("code").asInt(-1);
      if (code != 0) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, res.path("msg").asText("获取 jsapi_ticket 失败"));
      }
      String ticket = res.path("data").path("ticket").asText("").trim();
      if (ticket.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jsapi_ticket 为空");
      }
      long expireIn = res.path("data").path("expire_in").asLong(7200L);
      ticketCache.put(key, new TicketCache(ticket, now + expireIn * 1000L));
      return ticket;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.warn("获取 jsapi_ticket 异常 appId={}", appId, e);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "获取 jsapi_ticket 失败");
    }
  }

  private static String sign(String ticket, String nonceStr, long timestamp, String url) {
    try {
      String raw =
          "jsapi_ticket="
              + ticket
              + "&noncestr="
              + nonceStr
              + "&timestamp="
              + timestamp
              + "&url="
              + url;
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JSSDK 签名失败");
    }
  }

  private static String normalizeUrl(String pageUrl) {
    if (pageUrl == null) {
      return "";
    }
    String u = pageUrl.trim();
    int hash = u.indexOf('#');
    if (hash >= 0) {
      u = u.substring(0, hash);
    }
    return u;
  }

  private static String randomHex(int bytes) {
    byte[] buf = new byte[bytes];
    new java.security.SecureRandom().nextBytes(buf);
    StringBuilder sb = new StringBuilder(bytes * 2);
    for (byte b : buf) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
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

  private static final class TicketCache {
    final String ticket;
    final long expireAtMs;

    TicketCache(String ticket, long expireAtMs) {
      this.ticket = ticket;
      this.expireAtMs = expireAtMs;
    }
  }
}
