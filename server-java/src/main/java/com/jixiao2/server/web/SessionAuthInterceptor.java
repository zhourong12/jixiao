package com.jixiao2.server.web;

import com.jixiao2.server.api.SessionController;
import com.jixiao2.server.security.SessionPayload;
import com.jixiao2.server.security.SessionTokenCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 对 /api/** 要求会话（与 Nest SessionUserGuard 对齐），匿名白名单见 {@link
 * #isAnonymousAllowed(String, String)}。
 */
@Component
public class SessionAuthInterceptor implements HandlerInterceptor {

  public static final String ATTR_USER_ID = "jixiao2UserId";
  public static final String ATTR_USER_NAME = "jixiao2UserName";

  private final SessionTokenCodec codec;

  public SessionAuthInterceptor(SessionTokenCodec codec) {
    this.codec = codec;
  }

  private static boolean isAnonymousAllowed(String method, String uri) {
    if ("GET".equals(method) && "/api/session/me".equals(uri)) {
      return true;
    }
    if ("POST".equals(method) && "/api/session/logout".equals(uri)) {
      return true;
    }
    if ("GET".equals(method) && uri.startsWith("/api/admin/templates")) {
      return true;
    }
    return false;
  }

  private void attachIfPresent(HttpServletRequest request, String rawCookie) {
    SessionPayload p = rawCookie == null ? null : codec.verify(rawCookie);
    if (p != null && p.getSub() != null && !p.getSub().isEmpty()) {
      request.setAttribute(ATTR_USER_ID, p.getSub());
      String nm = p.getName();
      request.setAttribute(ATTR_USER_NAME, nm == null || nm.isEmpty() ? p.getSub() : nm);
    }
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    String uri = request.getRequestURI();
    String method = request.getMethod();
    String raw = readCookie(request, SessionController.SESSION_COOKIE);

    if (isAnonymousAllowed(method, uri)) {
      attachIfPresent(request, raw);
      return true;
    }

    SessionPayload p = raw == null ? null : codec.verify(raw);
    if (p == null || p.getSub() == null || p.getSub().isEmpty()) {
      write401(response);
      return false;
    }
    request.setAttribute(ATTR_USER_ID, p.getSub());
    String nm = p.getName();
    request.setAttribute(ATTR_USER_NAME, nm == null || nm.isEmpty() ? p.getSub() : nm);
    return true;
  }

  private static String readCookie(HttpServletRequest req, String name) {
    if (req.getCookies() == null) {
      return null;
    }
    for (javax.servlet.http.Cookie c : req.getCookies()) {
      if (name.equals(c.getName())) {
        return c.getValue();
      }
    }
    return null;
  }

  private static void write401(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"statusCode\":401,\"message\":\"请先登录\"}");
  }
}
