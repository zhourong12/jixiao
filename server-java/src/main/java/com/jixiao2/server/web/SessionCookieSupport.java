package com.jixiao2.server.web;

import com.jixiao2.server.api.SessionController;
import com.jixiao2.server.config.Jixiao2Properties;
import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieSupport {

  private final Jixiao2Properties properties;
  private final Environment environment;

  public SessionCookieSupport(Jixiao2Properties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  public ResponseCookie sessionCookie(String value, Duration maxAge) {
    Jixiao2Properties.Cookie cfg = properties.getSession().getCookie();
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(SessionController.SESSION_COOKIE, value)
            .httpOnly(true)
            .path(cfg.getPath() == null || cfg.getPath().isEmpty() ? "/" : cfg.getPath())
            .maxAge(maxAge)
            .sameSite(cfg.getSameSite() == null || cfg.getSameSite().isEmpty() ? "Lax" : cfg.getSameSite())
            .secure(cfg.resolveSecure(isProductionProfile()));
    if (cfg.getDomain() != null && !cfg.getDomain().isEmpty()) {
      builder.domain(cfg.getDomain());
    }
    return builder.build();
  }

  public ResponseCookie clearSessionCookie() {
    return sessionCookie("", Duration.ZERO);
  }

  private boolean isProductionProfile() {
    for (String profile : environment.getActiveProfiles()) {
      if ("prod".equals(profile) || "production".equals(profile)) {
        return true;
      }
    }
    return false;
  }
}
