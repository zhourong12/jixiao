package com.jixiao2.server.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbc;

  public NotificationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> list(int page, int pageSize) {
    int offset = (page - 1) * pageSize;
    List<Map<String, Object>> items =
        jdbc.query(
            "SELECT id, title, send_type, _created_at, _created_by, read_count, total_count FROM notification ORDER BY _created_at DESC LIMIT ? OFFSET ?",
            new Object[] {pageSize, offset},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("id", rs.getString("id"));
              m.put("title", rs.getString("title"));
              m.put("sendType", rs.getString("send_type"));
              Timestamp created = rs.getTimestamp("_created_at");
              m.put("sendTime", created == null ? null : created.toInstant().toString());
              m.put("senderName", rs.getString("_created_by"));
              m.put("readCount", rs.getInt("read_count"));
              m.put("totalCount", rs.getInt("total_count"));
              return m;
            });
    Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM notification", Integer.class);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("items", items);
    out.put("total", total == null ? 0 : total);
    out.put("page", page);
    out.put("pageSize", pageSize);
    return out;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> send(String userId, String userName, Map<String, Object> body) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户未登录，无法发送通知");
    }
    Object targetRaw = body.get("targetIds");
    List<String> targetIds = new ArrayList<String>();
    if (targetRaw instanceof List) {
      for (Object o : (List<Object>) targetRaw) {
        if (o != null) {
          targetIds.add(String.valueOf(o));
        }
      }
    }
    if (targetIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "接收人列表不能为空");
    }
    String id = UUID.randomUUID().toString();
    String targetJson;
    try {
      targetJson = MAPPER.writeValueAsString(targetIds);
    } catch (Exception e) {
      targetJson = "[]";
    }
    jdbc.update(
        "INSERT INTO notification (id, title, content, send_type, target_ids, sender_id, total_count, read_count, _created_by) VALUES (?,?,?,?,CAST(? AS JSON),?,?,?,?)",
        id,
        body.get("title"),
        body.get("content"),
        body.get("sendType"),
        targetJson,
        userId,
        targetIds.size(),
        0,
        userName == null || userName.isEmpty() ? userId : userName);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", Boolean.FALSE);
    out.put("notificationId", id);
    out.put("error", "飞书通知插件未在 Java 服务中接入");
    return out;
  }
}
