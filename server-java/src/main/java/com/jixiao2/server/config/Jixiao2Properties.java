package com.jixiao2.server.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jixiao2")
public class Jixiao2Properties {

  private final Cors cors = new Cors();
  private final Session session = new Session();
  private final Auth auth = new Auth();
  private final Feishu feishu = new Feishu();

  public Cors getCors() {
    return cors;
  }

  public Session getSession() {
    return session;
  }

  public Auth getAuth() {
    return auth;
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

  public static class Auth {
    /** 是否允许账密登录；false 时仅飞书 OAuth */
    private boolean passwordLoginEnabled = true;

    public boolean isPasswordLoginEnabled() {
      return passwordLoginEnabled;
    }

    public void setPasswordLoginEnabled(boolean passwordLoginEnabled) {
      this.passwordLoginEnabled = passwordLoginEnabled;
    }
  }

  public static class Feishu {
    private String appId = "";
    private String appSecret = "";
    private String redirectUri = "";
    /** 绩效飞书通知文末可选入口（浏览器打开的站点），如 https://app.example.com */
    private String notifyFrontendBaseUrl = "";
    /**
     * 飞书网页应用首页/入口（开放平台「网页应用」托管或 applink 等）。若配置则卡片 {@code card_link} 与文末链接优先用此地址。
     */
    private String notifyFeishuWebAppUrl = "";
    /** 是否在绩效流转时自动发飞书私聊；false 时仅保留手工通知管理 */
    private boolean performanceNotifyEnabled = true;
    /**
     * 绩效卡片「打开网页应用」走 AppLink 时使用的应用 ID（一般为「网页应用」独立应用，与登录/OAuth/发 IM 的 {@link
     * #appId} 可不同）。未配置时回退为 {@link #appId}。
     */
    private String webAppLinkAppId = "";
    /**
     * 与 {@link #webAppLinkAppId} 配套的 App Secret；当前仅作配置占位（发 IM 仍用主应用 {@link #appSecret}），便于与开放平台凭证一致管理。
     */
    private String webAppLinkAppSecret = "";

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

    public String getNotifyFrontendBaseUrl() {
      return notifyFrontendBaseUrl;
    }

    public void setNotifyFrontendBaseUrl(String notifyFrontendBaseUrl) {
      this.notifyFrontendBaseUrl = notifyFrontendBaseUrl;
    }

    public String getNotifyFeishuWebAppUrl() {
      return notifyFeishuWebAppUrl;
    }

    public void setNotifyFeishuWebAppUrl(String notifyFeishuWebAppUrl) {
      this.notifyFeishuWebAppUrl = notifyFeishuWebAppUrl;
    }

    public boolean isPerformanceNotifyEnabled() {
      return performanceNotifyEnabled;
    }

    public void setPerformanceNotifyEnabled(boolean performanceNotifyEnabled) {
      this.performanceNotifyEnabled = performanceNotifyEnabled;
    }

    public String getWebAppLinkAppId() {
      return webAppLinkAppId;
    }

    public void setWebAppLinkAppId(String webAppLinkAppId) {
      this.webAppLinkAppId = webAppLinkAppId;
    }

    public String getWebAppLinkAppSecret() {
      return webAppLinkAppSecret;
    }

    public void setWebAppLinkAppSecret(String webAppLinkAppSecret) {
      this.webAppLinkAppSecret = webAppLinkAppSecret;
    }
  }
}
