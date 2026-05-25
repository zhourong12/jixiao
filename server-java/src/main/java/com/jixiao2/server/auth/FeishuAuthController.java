package com.jixiao2.server.auth;

import com.jixiao2.server.feishu.FeishuRegistryService.FeishuLoginAppRow;
import com.jixiao2.server.web.SessionCookieSupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.LinkedHashMap;
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
  private static final String NEXT_COOKIE = "feishu_oauth_next";
  private static final Duration STATE_MAX_AGE = Duration.ofMinutes(10);
  private static final Duration SESSION_MAX_AGE = Duration.ofDays(7);

  private final FeishuAuthService feishuAuthService;
  private final SessionCookieSupport sessionCookieSupport;
  private final AuthConfigService authConfigService;
  private final SecureRandom secureRandom = new SecureRandom();

  public FeishuAuthController(
      FeishuAuthService feishuAuthService,
      SessionCookieSupport sessionCookieSupport,
      AuthConfigService authConfigService) {
    this.feishuAuthService = feishuAuthService;
    this.sessionCookieSupport = sessionCookieSupport;
    this.authConfigService = authConfigService;
  }

  @GetMapping("/subjects")
  public Map<String, Object> subjects() {
    List<Map<String, Object>> items = feishuAuthService.listLoginSubjects();
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("passwordLoginEnabled", authConfigService.isPasswordLoginEnabled());
    return out;
  }

  @GetMapping("/login")
  public ResponseEntity<Void> login(
      @RequestParam(value = "subjectCode", required = false) String subjectCode,
      @RequestParam(value = "next", required = false) String next,
      HttpServletResponse response) {
    if (subjectCode == null || subjectCode.trim().isEmpty()) {
      return ResponseEntity.status(HttpStatus.FOUND)
          .header(
              HttpHeaders.LOCATION,
              "/login?login_error=" + encode("请先在登录页选择飞书主体"))
          .build();
    }
    feishuAuthService.assertValidSubjectCode(subjectCode);
    FeishuLoginAppRow app = feishuAuthService.resolveLoginApp(subjectCode.trim());
    String appId = app.getAppId().trim();
    String redirectUri = app.getRedirectUri().trim();
    if (redirectUri.isEmpty()) {
      return ResponseEntity.status(HttpStatus.FOUND)
          .header(HttpHeaders.LOCATION, "/login?login_error=" + encode("该主体未配置 redirect_uri"))
          .build();
    }
    String state = subjectCode.trim() + ":" + randomHex(16);
    ResponseCookie stateCookie = stateCookie(STATE_COOKIE, state, STATE_MAX_AGE);
    response.addHeader(HttpHeaders.SET_COOKIE, stateCookie.toString());
    String safeNext = safeNextPath(next);
    if (safeNext != null) {
      response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(NEXT_COOKIE, safeNext, STATE_MAX_AGE).toString());
    } else {
      response.addHeader(HttpHeaders.SET_COOKIE, clearCookie(NEXT_COOKIE).toString());
    }
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
    return completeFeishuOAuth(code, state, request, response);
  }

  /**
   * 前端 OAuth 回调页收到飞书 {@code code} 后，将浏览器跳转到本地址（经与登录相同的站点 / 代理），用 HttpOnly
   * {@code feishu_oauth_state} 与 {@code state} 校验后换票并下发会话 Cookie，再 302 回首页。
   */
  @GetMapping("/exchange")
  public ResponseEntity<Void> exchange(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      HttpServletRequest request,
      HttpServletResponse response) {
    return completeFeishuOAuth(code, state, request, response);
  }

  private ResponseEntity<Void> completeFeishuOAuth(
      String code, String state, HttpServletRequest request, HttpServletResponse response) {
    String home = "/todo";
    String login = "/login";
    if (code == null || code.isEmpty()) {
      return redirectFail(response, login, "缺少授权码");
    }
    String cookieState = readCookie(request, STATE_COOKIE);
    if (state == null || cookieState == null || !state.equals(cookieState)) {
      return redirectFail(response, login, "state 校验失败");
    }
    String subjectCode = FeishuAuthService.extractSubjectCodeFromState(state);
    if (subjectCode == null) {
      return redirectFail(response, login, "state 格式无效");
    }
    response.addHeader(HttpHeaders.SET_COOKIE, clearCookie(STATE_COOKIE).toString());
    String nextTarget = safeNextPath(readCookie(request, NEXT_COOKIE));
    response.addHeader(HttpHeaders.SET_COOKIE, clearCookie(NEXT_COOKIE).toString());
    try {
      String token = feishuAuthService.exchangeCodeForUserAccessToken(code, subjectCode);
      Map<String, String> userInfo = feishuAuthService.fetchFeishuUserInfo(token);
      Map<String, String> emp =
          feishuAuthService.resolveEmployeeByFeishu(
              subjectCode, userInfo.get("openId"), userInfo.get("name"));
      List<String> roles = feishuAuthService.loadRolesForUser(emp.get("employeeId"));
      String sessionVal =
          feishuAuthService.buildSessionCookieValue(
              emp.get("employeeId"), emp.get("name"), roles, userInfo.get("openId"));
      if (sessionVal == null) {
        return redirectFail(response, login, "未配置 SESSION_JWT_SECRET，无法签发会话");
      }
      response.addHeader(
          HttpHeaders.SET_COOKIE, sessionCookieSupport.sessionCookie(sessionVal, SESSION_MAX_AGE).toString());
      String redirectTo = nextTarget != null ? nextTarget : home;
      return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, redirectTo).build();
    } catch (Exception e) {
      return redirectFail(response, login, e.getMessage() == null ? "登录失败" : e.getMessage());
    }
  }

  @GetMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, sessionCookieSupport.clearSessionCookie().toString());
    return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/").build();
  }

  private ResponseEntity<Void> redirectFail(HttpServletResponse response, String base, String msg) {
    response.addHeader(HttpHeaders.SET_COOKIE, clearCookie(NEXT_COOKIE).toString());
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

  private ResponseCookie stateCookie(String name, String value, Duration maxAge) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .path("/")
        .maxAge(maxAge)
        .sameSite("Lax")
        .build();
  }

  private ResponseCookie clearCookie(String name) {
    return stateCookie(name, "", Duration.ZERO);
  }

  /** 仅允许站内相对路径，防止开放重定向。 */
  private static String safeNextPath(String next) {
    if (next == null) {
      return null;
    }
    String n = next.trim();
    if (n.isEmpty() || !n.startsWith("/") || n.startsWith("//")) {
      return null;
    }
    return n;
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
