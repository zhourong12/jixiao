package com.jixiao2.server.auth;

import com.jixiao2.server.web.SessionCookieSupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/feishu")
public class FeishuAuthController {

  private static final String STATE_COOKIE = "feishu_oauth_state";
  private static final Duration STATE_MAX_AGE = Duration.ofMinutes(10);
  private static final Duration SESSION_MAX_AGE = Duration.ofDays(7);

  private final FeishuAuthService feishuAuthService;
  private final SessionCookieSupport sessionCookieSupport;
  private final SecureRandom secureRandom = new SecureRandom();

  public FeishuAuthController(FeishuAuthService feishuAuthService, SessionCookieSupport sessionCookieSupport) {
    this.feishuAuthService = feishuAuthService;
    this.sessionCookieSupport = sessionCookieSupport;
  }

  @GetMapping("/login")
  public ResponseEntity<Void> login(HttpServletResponse response) {
    String appId = feishuAuthService.resolveAppId();
    String redirectUri = feishuAuthService.resolveRedirectUri();
    String state = randomHex(16);
    ResponseCookie stateCookie = stateCookie(state, STATE_MAX_AGE);
    response.addHeader(HttpHeaders.SET_COOKIE, stateCookie.toString());
    String url =
        "https://open.feishu.cn/open-apis/authen/v1/index?app_id="
            + encode(appId)
            + "&redirect_uri="
            + encode(redirectUri)
            + "&state="
            + encode(state);
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
  }

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      HttpServletRequest request,
      HttpServletResponse response) {
    String base = "/";
    if (code == null || code.isEmpty()) {
      return redirectFail(response, base, "缺少授权码");
    }
    String cookieState = readCookie(request, STATE_COOKIE);
    if (state == null || cookieState == null || !state.equals(cookieState)) {
      return redirectFail(response, base, "state 校验失败");
    }
    response.addHeader(HttpHeaders.SET_COOKIE, clearStateCookie().toString());
    try {
      String token = feishuAuthService.exchangeCodeForUserAccessToken(code);
      Map<String, String> userInfo = feishuAuthService.fetchFeishuUserInfo(token);
      Map<String, String> emp =
          feishuAuthService.resolveEmployeeByFeishu(userInfo.get("openId"), userInfo.get("name"));
      List<String> roles = feishuAuthService.loadRolesForUser(emp.get("employeeId"));
      String sessionVal =
          feishuAuthService.buildSessionCookieValue(
              emp.get("employeeId"), emp.get("name"), roles, userInfo.get("openId"));
      if (sessionVal == null) {
        return redirectFail(response, base, "未配置 SESSION_JWT_SECRET，无法签发会话");
      }
      response.addHeader(
          HttpHeaders.SET_COOKIE, sessionCookieSupport.sessionCookie(sessionVal, SESSION_MAX_AGE).toString());
      return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, base).build();
    } catch (Exception e) {
      return redirectFail(response, base, e.getMessage() == null ? "登录失败" : e.getMessage());
    }
  }

  @GetMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, sessionCookieSupport.clearSessionCookie().toString());
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/").build();
  }

  private ResponseEntity<Void> redirectFail(HttpServletResponse response, String base, String msg) {
    String location = base + "?login_error=" + encode(msg);
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
  }

  private static String readCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private ResponseCookie stateCookie(String value, Duration maxAge) {
    return ResponseCookie.from(STATE_COOKIE, value)
        .httpOnly(true)
        .path("/")
        .maxAge(maxAge)
        .sameSite("Lax")
        .build();
  }

  private ResponseCookie clearStateCookie() {
    return stateCookie("", Duration.ZERO);
  }

  private String randomHex(int bytes) {
    byte[] buf = new byte[bytes];
    secureRandom.nextBytes(buf);
    StringBuilder sb = new StringBuilder(bytes * 2);
    for (byte b : buf) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (java.io.UnsupportedEncodingException e) {
      return value;
    }
  }
}
