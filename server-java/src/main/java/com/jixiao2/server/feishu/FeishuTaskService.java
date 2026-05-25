package com.jixiao2.server.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 飞书任务（待办）v2：需自建应用具备 task 写权限；失败由调用方记录日志，不抛业务异常。
 */
@Service
public class FeishuTaskService {

  private static final Logger log = LoggerFactory.getLogger(FeishuTaskService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FeishuImService feishuIm;
  private final RestTemplate restTemplate = feishuTaskRestTemplate();

  public FeishuTaskService(FeishuImService feishuIm) {
    this.feishuIm = feishuIm;
  }

  private static RestTemplate feishuTaskRestTemplate() {
    HttpComponentsClientHttpRequestFactory f = new HttpComponentsClientHttpRequestFactory();
    f.setConnectTimeout(30_000);
    f.setConnectionRequestTimeout(30_000);
    f.setReadTimeout(60_000);
    return new RestTemplate(f);
  }

  /**
   * @return task guid，失败返回 null
   */
  public String createTask(
      String tenantToken,
      String summary,
      String description,
      String assigneeOpenId,
      long dueEpochMs) {
    TaskApiResult r = createTaskResult(tenantToken, summary, description, assigneeOpenId, dueEpochMs);
    return r.guid;
  }

  /** token 失效时刷新 tenant_access_token 并重试一次。 */
  public String createTaskWithRetry(
      String appId,
      String appSecret,
      String summary,
      String description,
      String assigneeOpenId,
      long dueEpochMs) {
    if (appId == null
        || appId.trim().isEmpty()
        || appSecret == null
        || appSecret.trim().isEmpty()) {
      return null;
    }
    String token = feishuIm.fetchTenantAccessToken(appId, appSecret);
    TaskApiResult r = createTaskResult(token, summary, description, assigneeOpenId, dueEpochMs);
    if (r.success() || !FeishuImService.isTokenRelatedFailure(r.feishuCode, r.errorMsg)) {
      return r.guid;
    }
    log.info("飞书创建待办 token 失效，刷新后重试 appId={} feishu_code={}", appId, r.feishuCode);
    feishuIm.invalidateTenantAccessToken(appId);
    token = feishuIm.fetchTenantAccessToken(appId, appSecret);
    r = createTaskResult(token, summary, description, assigneeOpenId, dueEpochMs);
    return r.guid;
  }

  public boolean completeTask(String tenantToken, String taskGuid) {
    return completeTaskResult(tenantToken, taskGuid).success();
  }

  /** token 失效时刷新 tenant_access_token 并重试一次。 */
  public boolean completeTaskWithRetry(String appId, String appSecret, String taskGuid) {
    if (appId == null
        || appId.trim().isEmpty()
        || appSecret == null
        || appSecret.trim().isEmpty()
        || taskGuid == null
        || taskGuid.trim().isEmpty()) {
      return false;
    }
    String token = feishuIm.fetchTenantAccessToken(appId, appSecret);
    TaskApiResult r = completeTaskResult(token, taskGuid);
    if (r.success() || !FeishuImService.isTokenRelatedFailure(r.feishuCode, r.errorMsg)) {
      return r.success();
    }
    log.info("飞书完成待办 token 失效，刷新后重试 appId={} feishu_code={}", appId, r.feishuCode);
    feishuIm.invalidateTenantAccessToken(appId);
    token = feishuIm.fetchTenantAccessToken(appId, appSecret);
    return completeTaskResult(token, taskGuid).success();
  }

  private static final class TaskApiResult {
    private final int feishuCode;
    private final String errorMsg;
    private final String guid;

    private TaskApiResult(int feishuCode, String errorMsg, String guid) {
      this.feishuCode = feishuCode;
      this.errorMsg = errorMsg;
      this.guid = guid;
    }

    private static TaskApiResult ok(String guid) {
      return new TaskApiResult(0, null, guid);
    }

    private static TaskApiResult fail(int feishuCode, String errorMsg) {
      return new TaskApiResult(feishuCode, errorMsg, null);
    }

    private boolean success() {
      return feishuCode == 0;
    }
  }

  private TaskApiResult createTaskResult(
      String tenantToken,
      String summary,
      String description,
      String assigneeOpenId,
      long dueEpochMs) {
    if (tenantToken == null || tenantToken.trim().isEmpty()) {
      return TaskApiResult.fail(-1, "token 为空");
    }
    if (assigneeOpenId == null || assigneeOpenId.trim().isEmpty()) {
      return TaskApiResult.fail(-1, "assignee 为空");
    }
    try {
      Map<String, Object> due = new LinkedHashMap<String, Object>();
      due.put("timestamp", String.valueOf(dueEpochMs));
      due.put("is_all_day", Boolean.FALSE);
      Map<String, Object> member = new LinkedHashMap<String, Object>();
      member.put("id", assigneeOpenId.trim());
      member.put("role", "assignee");
      member.put("type", "user");
      List<Map<String, Object>> members = new ArrayList<Map<String, Object>>();
      members.add(member);
      Map<String, Object> root = new LinkedHashMap<String, Object>();
      root.put("summary", summary == null ? "" : summary);
      root.put("description", description == null ? "" : description);
      root.put("due", due);
      root.put("members", members);
      String payload = MAPPER.writeValueAsString(root);
      String url = "https://open.feishu.cn/open-apis/task/v2/tasks?user_id_type=open_id";
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
      headers.setBearerAuth(tenantToken.trim());
      HttpEntity<String> entity = new HttpEntity<String>(payload, headers);
      ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
      JsonNode node = MAPPER.readTree(res.getBody());
      int code = node.path("code").asInt(-1);
      if (code != 0) {
        String msg = node.path("msg").asText("");
        log.warn("飞书创建任务失败 code={} msg={}", code, msg);
        return TaskApiResult.fail(code, msg);
      }
      String guid = node.path("data").path("task").path("guid").asText("").trim();
      return guid.isEmpty() ? TaskApiResult.fail(-1, "guid 为空") : TaskApiResult.ok(guid);
    } catch (Exception e) {
      log.warn("飞书创建任务异常", e);
      return TaskApiResult.fail(-1, e.getMessage() == null ? "异常" : e.getMessage());
    }
  }

  private TaskApiResult completeTaskResult(String tenantToken, String taskGuid) {
    if (tenantToken == null || tenantToken.trim().isEmpty() || taskGuid == null || taskGuid.trim().isEmpty()) {
      return TaskApiResult.fail(-1, "参数为空");
    }
    try {
      Map<String, Object> task = new LinkedHashMap<String, Object>();
      task.put("completed_at", String.valueOf(System.currentTimeMillis()));
      Map<String, Object> root = new LinkedHashMap<String, Object>();
      root.put("task", task);
      root.put("update_fields", Collections.singletonList("completed_at"));
      String payload = MAPPER.writeValueAsString(root);
      String url =
          "https://open.feishu.cn/open-apis/task/v2/tasks/"
              + java.net.URLEncoder.encode(taskGuid.trim(), "UTF-8")
              + "?user_id_type=open_id";
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
      headers.setBearerAuth(tenantToken.trim());
      HttpEntity<String> entity = new HttpEntity<String>(payload, headers);
      ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.PATCH, entity, String.class);
      JsonNode node = MAPPER.readTree(res.getBody());
      int code = node.path("code").asInt(-1);
      if (code != 0) {
        String msg = node.path("msg").asText("");
        log.warn("飞书完成任务失败 guid={} code={} msg={}", taskGuid, code, msg);
        return TaskApiResult.fail(code, msg);
      }
      return TaskApiResult.ok(null);
    } catch (Exception e) {
      log.warn("飞书完成任务异常 guid={}", taskGuid, e);
      return TaskApiResult.fail(-1, e.getMessage() == null ? "异常" : e.getMessage());
    }
  }
}
