package com.jixiao2.server.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 与 Node {@code server/utils/session-token.ts} 一致：payload 为 base64url(JSON)，签名为
 * HMAC-SHA256(secret, json).digest('base64url')，Cookie 名 {@code jx_session}。
 */
@Component
public class SessionTokenCodec {

  private static final int TTL_SEC = 7 * 24 * 3600;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String secret;

  public SessionTokenCodec(
      Environment environment, @Value("${jixiao2.session.secret:}") String configuredSecret) {
    String s = configuredSecret != null ? configuredSecret.trim() : "";
    boolean prod = false;
    for (String p : environment.getActiveProfiles()) {
      if ("prod".equals(p) || "production".equals(p)) {
        prod = true;
        break;
      }
    }
    if (s.isEmpty()) {
      s = prod ? "" : "dev-session-signing-key-jixiao2-local";
    }
    this.secret = s;
  }

  public boolean hasSecret() {
    return !secret.isEmpty();
  }

  public String sign(String sub, String name, List<String> roles, String feishuOpenId) {
    if (!hasSecret()) {
      return null;
    }
    long exp = Instant.now().getEpochSecond() + TTL_SEC;
    try {
      ObjectNode n = MAPPER.createObjectNode();
      n.put("sub", sub);
      n.put("name", name);
      ArrayNode arr = n.putArray("roles");
      for (String r : roles) {
        arr.add(r);
      }
      if (feishuOpenId != null && !feishuOpenId.isEmpty()) {
        n.put("feishuOpenId", feishuOpenId);
      }
      n.put("exp", exp);
      String json = MAPPER.writeValueAsString(n);
      String jsonB64 =
          Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
      String sig = hmacBase64Url(jsonB64);
      return jsonB64 + "." + sig;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public SessionPayload verify(String token) {
    if (!hasSecret() || token == null || token.isEmpty()) {
      return null;
    }
    int i = token.lastIndexOf('.');
    if (i <= 0) {
      return null;
    }
    String jsonB64 = token.substring(0, i);
    String sig = token.substring(i + 1);
    String expected;
    try {
      expected = hmacBase64Url(jsonB64);
    } catch (Exception e) {
      return null;
    }
    if (!constantTimeEquals(sig, expected)) {
      return null;
    }
    try {
      byte[] jsonBytes = Base64.getUrlDecoder().decode(jsonB64);
      JsonNode root = MAPPER.readTree(jsonBytes);
      String sub = text(root, "sub");
      if (sub == null || sub.isEmpty()) {
        return null;
      }
      if (!root.has("exp") || !root.get("exp").isNumber()) {
        return null;
      }
      long exp = root.get("exp").asLong();
      if (exp < Instant.now().getEpochSecond()) {
        return null;
      }
      String name = text(root, "name");
      if (name == null) {
        name = "";
      }
      List<String> roleList = new ArrayList<String>();
      if (root.has("roles") && root.get("roles").isArray()) {
        for (JsonNode r : root.get("roles")) {
          if (r.isTextual()) {
            roleList.add(r.asText());
          }
        }
      }
      String openId =
          root.has("feishuOpenId") && root.get("feishuOpenId").isTextual()
              ? root.get("feishuOpenId").asText()
              : null;
      return new SessionPayload(sub, name, roleList, openId, exp);
    } catch (Exception e) {
      return null;
    }
  }

  private static String text(JsonNode n, String field) {
    if (!n.has(field) || !n.get(field).isTextual()) {
      return null;
    }
    return n.get(field).asText();
  }

  private String hmacBase64Url(String message) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  private static boolean constantTimeEquals(String a, String b) {
    byte[] x = a.getBytes(StandardCharsets.UTF_8);
    byte[] y = b.getBytes(StandardCharsets.UTF_8);
    if (x.length != y.length) {
      return false;
    }
    int r = 0;
    for (int i = 0; i < x.length; i++) {
      r |= x[i] ^ y[i];
    }
    return r == 0;
  }
}
