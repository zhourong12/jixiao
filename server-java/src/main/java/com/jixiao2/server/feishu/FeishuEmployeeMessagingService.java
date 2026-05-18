package com.jixiao2.server.feishu;

import com.jixiao2.server.feishu.FeishuRegistryService.FeishuImAppRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 按员工解析飞书发消息所需：主体通知 URL、IM 应用凭证、receive_id（open_id）。
 */
@Service
public class FeishuEmployeeMessagingService {

  private static final Logger log = LoggerFactory.getLogger(FeishuEmployeeMessagingService.class);

  private final JdbcTemplate jdbc;
  private final FeishuRegistryService registry;
  private final FeishuImService feishuIm;

  public FeishuEmployeeMessagingService(
      JdbcTemplate jdbc, FeishuRegistryService registry, FeishuImService feishuIm) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.feishuIm = feishuIm;
  }

  public Optional<FeishuMessagingContext> resolveForEmployeeId(String employeeId) {
    if (employeeId == null || employeeId.trim().isEmpty()) {
      return Optional.empty();
    }
    String eid = employeeId.trim();
    List<Map<String, Object>> rows =
        jdbc.query(
            "SELECT eh.employee_id, eh.feishu_open_id, eh.feishu_subject_id, "
                + "s.notify_frontend_base_url, s.notify_feishu_web_app_url, s.web_app_link_app_id, "
                + "s.performance_notify_enabled "
                + "FROM employee_hierarchy eh "
                + "LEFT JOIN feishu_subject s ON s.id = eh.feishu_subject_id "
                + "WHERE eh.employee_id = ? LIMIT 1",
            new Object[] {eid},
            (rs, rn) -> {
              Map<String, Object> m = new LinkedHashMap<String, Object>();
              m.put("employeeId", rs.getString("employee_id"));
              m.put("feishuOpenId", rs.getString("feishu_open_id"));
              m.put("feishuSubjectId", rs.getString("feishu_subject_id"));
              m.put("notifyFrontendBaseUrl", rs.getString("notify_frontend_base_url"));
              m.put("notifyFeishuWebAppUrl", rs.getString("notify_feishu_web_app_url"));
              m.put("webAppLinkAppId", rs.getString("web_app_link_app_id"));
              m.put("performanceNotifyEnabled", rs.getObject("performance_notify_enabled"));
              return m;
            });
    if (rows.isEmpty()) {
      log.info("飞书员工消息 resolve 跳过 employeeId={} reason=未找到员工", eid);
      return Optional.of(FeishuMessagingContext.skipped("未找到员工"));
    }
    Map<String, Object> row = rows.get(0);
    String subjectId = row.get("feishuSubjectId") == null ? null : String.valueOf(row.get("feishuSubjectId"));
    if (subjectId == null || subjectId.trim().isEmpty()) {
      log.info("飞书员工消息 resolve 跳过 employeeId={} reason=员工未绑定飞书主体", eid);
      return Optional.of(FeishuMessagingContext.skipped("员工未绑定飞书主体"));
    }
    Optional<FeishuImAppRow> imApp = registry.findImAppForSubjectId(subjectId);
    if (!imApp.isPresent()) {
      log.info(
          "飞书员工消息 resolve 跳过 employeeId={} feishuSubjectId={} reason=主体未配置 IM/登录应用",
          eid,
          subjectId);
      return Optional.of(FeishuMessagingContext.skipped("主体未配置 IM/登录应用"));
    }
    String openCol = row.get("feishuOpenId") == null ? null : String.valueOf(row.get("feishuOpenId"));
    String receive =
        (openCol != null && !openCol.trim().isEmpty()) ? openCol.trim() : eid;
    if (receive.isEmpty()) {
      log.info(
          "飞书员工消息 resolve 跳过 employeeId={} feishuSubjectId={} reason=接收人 open_id 为空",
          eid,
          subjectId);
      return Optional.of(FeishuMessagingContext.skipped("接收人 open_id 为空"));
    }
    Optional<FeishuImAppRow> dirApp = registry.findDirectoryAppForSubjectId(subjectId);
    if (dirApp.isPresent()
        && !dirApp.get().getAppId().equals(imApp.get().getAppId())
        && receive.startsWith("ou_")) {
      Optional<String> imOpen =
          feishuIm.convertOpenIdToImAppUser(
              dirApp.get().getAppId(),
              dirApp.get().getAppSecret(),
              receive,
              imApp.get().getAppId(),
              imApp.get().getAppSecret());
      if (imOpen.isPresent()) {
        log.info(
            "飞书员工消息 resolve 已将 open_id 映射到 IM 应用 employeeId={} feishuSubjectId={}",
            eid,
            subjectId);
        receive = imOpen.get();
      } else {
        log.warn(
            "飞书员工消息 resolve 未能在 IM 应用下解析 open_id（通讯录与 IM 非同应用时需 union_id 与 contact 权限） employeeId={} directoryAppId={} imAppId={}",
            eid,
            dirApp.get().getAppId(),
            imApp.get().getAppId());
      }
    }
    Object perfRaw = row.get("performanceNotifyEnabled");
    boolean subPerf = coalescePerformanceNotifyEnabled(perfRaw);
    log.info(
        "飞书员工消息 resolve employeeId={} feishuSubjectId={} performanceNotifyEnabledRaw={} rawType={} subjectPerformanceNotifyEnabled={}",
        eid,
        subjectId,
        perfRaw,
        perfRaw == null ? "null" : perfRaw.getClass().getName(),
        subPerf);
    FeishuMessagingContext ctx = new FeishuMessagingContext();
    ctx.setEmployeeId(eid);
    ctx.setReceiveOpenId(receive);
    ctx.setImAppId(imApp.get().getAppId());
    ctx.setImAppSecret(imApp.get().getAppSecret());
    ctx.setNotifyFrontendBaseUrl(nullToEmpty(row.get("notifyFrontendBaseUrl")));
    ctx.setNotifyFeishuWebAppUrl(nullToEmpty(row.get("notifyFeishuWebAppUrl")));
    ctx.setWebAppLinkAppId(nullToEmpty(row.get("webAppLinkAppId")));
    ctx.setSubjectPerformanceNotifyEnabled(subPerf);
    ctx.setSkipReason(null);
    return Optional.of(ctx);
  }

  /**
   * MySQL tinyint(1) 可能被映射为 {@link Boolean} 或 {@link Number}；原逻辑仅识别 Number，会把 Boolean.TRUE 误判为关闭通知。
   */
  private static boolean coalescePerformanceNotifyEnabled(Object raw) {
    if (raw == null) {
      return true;
    }
    if (raw instanceof Boolean) {
      return (Boolean) raw;
    }
    if (raw instanceof Number) {
      return ((Number) raw).intValue() != 0;
    }
    return true;
  }

  private static String nullToEmpty(Object o) {
    if (o == null) {
      return "";
    }
    String s = String.valueOf(o);
    return s == null ? "" : s;
  }

  public static final class FeishuMessagingContext {
    private String employeeId;
    private String receiveOpenId;
    private String imAppId;
    private String imAppSecret;
    private String notifyFrontendBaseUrl;
    private String notifyFeishuWebAppUrl;
    private String webAppLinkAppId;
    private boolean subjectPerformanceNotifyEnabled = true;
    private String skipReason;

    public static FeishuMessagingContext skipped(String reason) {
      FeishuMessagingContext c = new FeishuMessagingContext();
      c.skipReason = reason == null ? "" : reason;
      return c;
    }

    public boolean isOk() {
      return skipReason == null || skipReason.isEmpty();
    }

    public String getEmployeeId() {
      return employeeId;
    }

    public void setEmployeeId(String employeeId) {
      this.employeeId = employeeId;
    }

    public String getReceiveOpenId() {
      return receiveOpenId;
    }

    public void setReceiveOpenId(String receiveOpenId) {
      this.receiveOpenId = receiveOpenId;
    }

    public String getImAppId() {
      return imAppId;
    }

    public void setImAppId(String imAppId) {
      this.imAppId = imAppId;
    }

    public String getImAppSecret() {
      return imAppSecret;
    }

    public void setImAppSecret(String imAppSecret) {
      this.imAppSecret = imAppSecret;
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

    public String getWebAppLinkAppId() {
      return webAppLinkAppId;
    }

    public void setWebAppLinkAppId(String webAppLinkAppId) {
      this.webAppLinkAppId = webAppLinkAppId;
    }

    public boolean isSubjectPerformanceNotifyEnabled() {
      return subjectPerformanceNotifyEnabled;
    }

    public void setSubjectPerformanceNotifyEnabled(boolean subjectPerformanceNotifyEnabled) {
      this.subjectPerformanceNotifyEnabled = subjectPerformanceNotifyEnabled;
    }

    public String getSkipReason() {
      return skipReason;
    }

    public void setSkipReason(String skipReason) {
      this.skipReason = skipReason;
    }
  }
}
