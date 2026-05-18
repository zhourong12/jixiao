package com.jixiao2.server.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiao2.server.feishu.FeishuEmployeeMessagingService;
import com.jixiao2.server.feishu.FeishuImService;
import com.jixiao2.server.feishu.FeishuRegistryService;
import com.jixiao2.server.menu.MenuPermissionService;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbc;
  private final FeishuImService feishuImService;
  private final FeishuEmployeeMessagingService feishuEmployeeMessaging;
  private final FeishuRegistryService feishuRegistry;
  private final MenuPermissionService menuPermissionService;

  public NotificationService(
      JdbcTemplate jdbc,
      FeishuImService feishuImService,
      FeishuEmployeeMessagingService feishuEmployeeMessaging,
      FeishuRegistryService feishuRegistry,
      MenuPermissionService menuPermissionService) {
    this.jdbc = jdbc;
    this.feishuImService = feishuImService;
    this.feishuEmployeeMessaging = feishuEmployeeMessaging;
    this.feishuRegistry = feishuRegistry;
    this.menuPermissionService = menuPermissionService;
  }

  public Map<String, Object> list(String userId, int page, int pageSize) {
    if (userId == null || userId.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }
    menuPermissionService.assertMenuAllowed(userId, "admin_employees");
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
    menuPermissionService.assertMenuAllowed(userId, "admin_employees");
    String title = body.get("title") == null ? "" : String.valueOf(body.get("title")).trim();
    String content = body.get("content") == null ? "" : String.valueOf(body.get("content")).trim();
    if (title.isEmpty() || content.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题与内容不能为空");
    }
    String sendType = body.get("sendType") == null ? "" : String.valueOf(body.get("sendType")).trim();
    Object targetRaw = body.get("targetIds");
    List<String> targetIds = new ArrayList<String>();
    if (targetRaw instanceof List) {
      for (Object o : (List<Object>) targetRaw) {
        if (o != null) {
          targetIds.add(String.valueOf(o));
        }
      }
    }
    if ("specified".equals(sendType) && targetIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "接收人列表不能为空");
    }
    if ("department".equals(sendType)
        && (targetIds.isEmpty() || targetIds.get(0) == null || targetIds.get(0).trim().isEmpty())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择部门");
    }
    Object sc0 = body.get("subjectCode");
    if (sc0 == null || String.valueOf(sc0).trim().isEmpty()) {
      sc0 = body.get("feishuSubjectCode");
    }
    String subjectCode = sc0 == null ? "" : String.valueOf(sc0).trim();
    if (subjectCode.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先选择飞书主体");
    }
    String subjectId =
        feishuRegistry
            .findSubjectIdByCode(subjectCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书主体不存在或已停用"));
    List<String> recipients = resolveRecipientEmployeeIds(sendType, targetIds, subjectId);
    if (recipients.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可发送的员工");
    }

    String fullText = title + "\n\n" + content;

    String id = UUID.randomUUID().toString();
    String recipientsJson;
    try {
      recipientsJson = MAPPER.writeValueAsString(recipients);
    } catch (Exception e) {
      recipientsJson = "[]";
    }
    jdbc.update(
        "INSERT INTO notification (id, title, content, send_type, target_ids, sender_id, total_count, read_count, _created_by) VALUES (?,?,?,?,CAST(? AS JSON),?,?,?,?)",
        id,
        title,
        content,
        sendType,
        recipientsJson,
        userId,
        recipients.size(),
        0,
        userName == null || userName.isEmpty() ? userId : userName);

    int sentOk = 0;
    final String appPairSep = "\u0001";
    Map<String, List<String>> receiveIdsByAppPair = new LinkedHashMap<String, List<String>>();
    for (String empId : recipients) {
      Optional<FeishuEmployeeMessagingService.FeishuMessagingContext> opt =
          feishuEmployeeMessaging.resolveForEmployeeId(empId);
      FeishuEmployeeMessagingService.FeishuMessagingContext ctx =
          opt.orElseGet(() -> FeishuEmployeeMessagingService.FeishuMessagingContext.skipped("无上下文"));
      if (!ctx.isOk()) {
        continue;
      }
      String appKey = ctx.getImAppId() + appPairSep + ctx.getImAppSecret();
      List<String> list = receiveIdsByAppPair.get(appKey);
      if (list == null) {
        list = new ArrayList<String>();
        receiveIdsByAppPair.put(appKey, list);
      }
      list.add(ctx.getReceiveOpenId());
    }
    for (Map.Entry<String, List<String>> en : receiveIdsByAppPair.entrySet()) {
      int sep = en.getKey().indexOf(appPairSep);
      String appId = sep <= 0 ? "" : en.getKey().substring(0, sep);
      String appSecret = sep < 0 || sep >= en.getKey().length() - 1 ? "" : en.getKey().substring(sep + 1);
      String tenantToken;
      try {
        tenantToken = feishuImService.fetchTenantAccessToken(appId, appSecret);
      } catch (Exception ex) {
        continue;
      }
      for (String receiveId : en.getValue()) {
        String err = feishuImService.sendTextToUser(tenantToken, receiveId, fullText);
        if (err == null) {
          sentOk++;
        }
      }
    }
    jdbc.update("UPDATE notification SET read_count = ? WHERE id = ?", sentOk, id);

    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("success", Boolean.TRUE);
    out.put("notificationId", id);
    out.put("feishuSent", sentOk);
    out.put("feishuFailed", recipients.size() - sentOk);
    if (sentOk < recipients.size()) {
      out.put(
          "message",
          "部分用户未在飞书收到消息：请确认员工已绑定飞书主体且已配置 IM 应用，飞书 open_id 可通过同步或飞书选人获得。");
    }
    return out;
  }

  private List<String> resolveRecipientEmployeeIds(String sendType, List<String> targetIds, String subjectId) {
    if ("all".equals(sendType)) {
      return jdbc.query(
          "SELECT employee_id FROM employee_hierarchy WHERE TRIM(COALESCE(employee_id,'')) <> '' AND (feishu_subject_id = ? OR (feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = ? AND fs.code = 'default'))) ORDER BY employee_id",
          new Object[] {subjectId, subjectId},
          (rs, rn) -> rs.getString(1));
    }
    if ("department".equals(sendType)) {
      String deptId = targetIds.get(0).trim();
      return jdbc.query(
          "SELECT employee_id FROM employee_hierarchy WHERE department_id = ? AND TRIM(COALESCE(employee_id,'')) <> '' AND (feishu_subject_id = ? OR (feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = ? AND fs.code = 'default'))) ORDER BY employee_id",
          new Object[] {deptId, subjectId, subjectId},
          (rs, rn) -> rs.getString(1));
    }
    if ("specified".equals(sendType)) {
      LinkedHashSet<String> out = new LinkedHashSet<String>();
      for (String tid : targetIds) {
        if (tid == null) {
          continue;
        }
        String t = tid.trim();
        if (t.isEmpty()) {
          continue;
        }
        Integer c =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM employee_hierarchy WHERE employee_id = ? AND (feishu_subject_id = ? OR (feishu_subject_id IS NULL AND EXISTS (SELECT 1 FROM feishu_subject fs WHERE fs.id = ? AND fs.code = 'default')))",
                Integer.class,
                t,
                subjectId,
                subjectId);
        if (c != null && c > 0) {
          out.add(t);
        }
      }
      return new ArrayList<String>(out);
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的 sendType: " + sendType);
  }
}
