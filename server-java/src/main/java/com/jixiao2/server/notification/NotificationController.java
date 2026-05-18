package com.jixiao2.server.notification;

import com.jixiao2.server.web.CurrentUser;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.jixiao2.server.web.SessionAuthInterceptor;

@RestController
@RequestMapping("/api/admin/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public Map<String, Object> list(
      @CurrentUser String userId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return notificationService.list(userId, page, pageSize);
  }

  @PostMapping
  public Map<String, Object> send(
      @CurrentUser String userId, HttpServletRequest request, @RequestBody Map<String, Object> body) {
    Object userName = request.getAttribute(SessionAuthInterceptor.ATTR_USER_NAME);
    return notificationService.send(userId, userName == null ? "" : String.valueOf(userName), body);
  }
}
