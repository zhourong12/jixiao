package com.jixiao2.server.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jixiao2")
public class Jixiao2Properties {

  private final Cors cors = new Cors();
  private final Session session = new Session();
  private final Feishu feishu = new Feishu();

  public Cors getCors() {
    return cors;
  }

  public Session getSession() {
    return session;
  }

  public Feishu getFeishu() {
    return feishu;
  }

  public static class Cors {
    /** Comma-separated origin patterns, e.g. https://app.example.com,http://localhost:* */
    private String allowedOriginPatterns = "http://localhost:*,http://127.0.0.1:*";

    public String getAllowedOriginPatterns() {
      return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(String allowedOriginPatterns) {
      this.allowedOriginPatterns = allowedOriginPatterns;
    }

    public List<String> originPatternList() {
      if (allowedOriginPatterns == null || allowedOriginPatterns.trim().isEmpty()) {
        return new ArrayList<String>();
      }
      List<String> out = new ArrayList<String>();
      for (String part : allowedOriginPatterns.split(",")) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          out.add(trimmed);
        }
      }
      return out;
    }
  }

  public static class Session {
    private String secret = "";
    private final Cookie cookie = new Cookie();

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }

    public Cookie getCookie() {
      return cookie;
    }
  }

  public static class Cookie {
    private String path = "/";
    private String domain = "";
    private String sameSite = "Lax";
    /** true | false | auto (secure when prod profile) */
    private String secure = "auto";

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    public String getDomain() {
      return domain;
    }

    public void setDomain(String domain) {
      this.domain = domain;
    }

    public String getSameSite() {
      return sameSite;
    }

    public void setSameSite(String sameSite) {
      this.sameSite = sameSite;
    }

    public String getSecure() {
      return secure;
    }

    public void setSecure(String secure) {
      this.secure = secure;
    }

    public boolean resolveSecure(boolean productionProfile) {
      if (secure == null || secure.isEmpty() || "auto".equalsIgnoreCase(secure)) {
        return productionProfile;
      }
      return Boolean.parseBoolean(secure);
    }
  }

  public static class Feishu {
    private String appId = "";
    private String appSecret = "";
    private String redirectUri = "";

    public String getAppId() {
      return appId;
    }

    public void setAppId(String appId) {
      this.appId = appId;
    }

    public String getAppSecret() {
      return appSecret;
    }

    public void setAppSecret(String appSecret) {
      this.appSecret = appSecret;
    }

    public String getRedirectUri() {
      return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
      this.redirectUri = redirectUri;
    }
  }
}
