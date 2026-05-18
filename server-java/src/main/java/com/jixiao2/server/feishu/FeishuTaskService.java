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

  private final RestTemplate restTemplate = feishuTaskRestTemplate();

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
    if (tenantToken == null || tenantToken.trim().isEmpty()) {
      return null;
    }
    if (assigneeOpenId == null || assigneeOpenId.trim().isEmpty()) {
      return null;
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
      if (node.path("code").asInt(-1) != 0) {
        log.warn(
            "飞书创建任务失败 code={} msg={}",
            node.path("code").asInt(),
            node.path("msg").asText(""));
        return null;
      }
      String guid = node.path("data").path("task").path("guid").asText("").trim();
      return guid.isEmpty() ? null : guid;
    } catch (Exception e) {
      log.warn("飞书创建任务异常", e);
      return null;
    }
  }

  public boolean completeTask(String tenantToken, String taskGuid) {
    if (tenantToken == null || tenantToken.trim().isEmpty() || taskGuid == null || taskGuid.trim().isEmpty()) {
      return false;
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
      if (node.path("code").asInt(-1) != 0) {
        log.warn(
            "飞书完成任务失败 guid={} code={} msg={}",
            taskGuid,
            node.path("code").asInt(),
            node.path("msg").asText(""));
        return false;
      }
      return true;
    } catch (Exception e) {
      log.warn("飞书完成任务异常 guid={}", taskGuid, e);
      return false;
    }
  }
}
