package com.jixiao2.server.performance;

import com.jixiao2.server.config.Jixiao2Properties;
import com.jixiao2.server.feishu.FeishuEmployeeMessagingService;
import com.jixiao2.server.feishu.FeishuImService;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 绩效节点飞书私聊：全局开关见 {@link Jixiao2Properties#getFeishu()}；凭证与卡片入口按接收人所属飞书主体从库解析。
 */
@Service
public class PerformanceFeishuNotifier {

  private static final Logger log = LoggerFactory.getLogger(PerformanceFeishuNotifier.class);

  private final JdbcTemplate jdbc;
  private final FeishuImService feishuImService;
  private final Jixiao2Properties properties;
  private final FeishuEmployeeMessagingService feishuEmployeeMessaging;
  private final PerformanceFeishuTaskService performanceFeishuTaskService;

  public PerformanceFeishuNotifier(
      JdbcTemplate jdbc,
      FeishuImService feishuImService,
      Jixiao2Properties properties,
      FeishuEmployeeMessagingService feishuEmployeeMessaging,
      PerformanceFeishuTaskService performanceFeishuTaskService) {
    this.jdbc = jdbc;
    this.feishuImService = feishuImService;
    this.properties = properties;
    this.feishuEmployeeMessaging = feishuEmployeeMessaging;
    this.performanceFeishuTaskService = performanceFeishuTaskService;
  }

  /**
   * @return null 表示可继续按员工解析飞书；非 null 为全局跳过原因（打日志用）
   */
  private String perfNotifyBlockReason() {
    if (!properties.getFeishu().isPerformanceNotifyEnabled()) {
      return "JIXIAO2_FEISHU_PERFORMANCE_NOTIFY=false";
    }
    return null;
  }

  /**
   * 绩效卡片入口 URL：默认在飞书内打开。优先级：主体 {@code notifyFeishuWebAppUrl} 已是 AppLink 则原样使用；否则配置的
   * {@code http(s)} 网页地址包一层 {@code web_url} AppLink；再否则用「网页应用」{@code web_app} AppLink（{@code
   * web_app_link_app_id} / IM 应用 app_id）。
   */
  private String effectiveNotifyEntranceUrl(FeishuEmployeeMessagingService.FeishuMessagingContext ctx) {
    String webApp = ctx.getNotifyFeishuWebAppUrl();
    if (webApp != null && !webApp.trim().isEmpty()) {
      String w = webApp.trim().replaceAll("/$", "");
      if (isFeishuApplinkUrl(w)) {
        return w;
      }
      return wrapPageUrlForFeishuInApp(w);
    }
    String pub = ctx.getNotifyFrontendBaseUrl();
    if (pub != null && !pub.trim().isEmpty()) {
      return wrapPageUrlForFeishuInApp(pub.trim().replaceAll("/$", ""));
    }
    return buildFeishuWebAppApplink(ctx.getWebAppLinkAppId(), ctx.getImAppId());
  }

  private static boolean isFeishuApplinkUrl(String url) {
    if (url == null) {
      return false;
    }
    String u = url.trim().toLowerCase();
    return u.startsWith("https://applink.feishu.cn/")
        || u.startsWith("http://applink.feishu.cn/");
  }

  private String buildFeishuWebAppApplink(String linkAppId, String fallbackAppId) {
    String use =
        linkAppId != null && !linkAppId.trim().isEmpty()
            ? linkAppId.trim()
            : (fallbackAppId == null ? "" : fallbackAppId.trim());
    if (use.isEmpty()) {
      return "";
    }
    try {
      return "https://applink.feishu.cn/client/web_app/open?appId="
          + URLEncoder.encode(use, StandardCharsets.UTF_8.name())
          + "&mode=appCenter";
    } catch (UnsupportedEncodingException e) {
      return "https://applink.feishu.cn/client/web_app/open?appId=" + use + "&mode=appCenter";
    }
  }

  /**
   * 将业务页 {@code http(s)} 包成飞书 AppLink {@code web_url/open}，在客户端内打开；已是 applink 或非 http(s) 则原样返回。
   */
  private static String wrapPageUrlForFeishuInApp(String pageUrl) {
    if (pageUrl == null || pageUrl.isEmpty()) {
      return "";
    }
    String u = pageUrl.trim();
    if (isFeishuApplinkUrl(u)) {
      return u;
    }
    if (!u.startsWith("http://") && !u.startsWith("https://")) {
      return u;
    }
    try {
      return "https://applink.feishu.cn/client/web_url/open?mode=appCenter&url="
          + URLEncoder.encode(u, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      return "https://applink.feishu.cn/client/web_url/open?mode=appCenter&url=" + u;
    }
  }

  /**
   * 飞书卡片 1.0：标题「绩效待办」+ 黄色标题栏；有入口时设置 {@code card_link} 与「绩效」按钮。官方按钮无纯黄底枚举，用
   * {@code column_set} 单列 {@code background_style: yellow-350} 包裹按钮，形成黄色操作区。
   */
  private Map<String, Object> buildPerformanceTodoCard(String textBody, String entrance) {
    String ent = entrance == null ? "" : entrance.trim();
    String body = textBody == null ? "" : textBody;

    Map<String, Object> config = new LinkedHashMap<String, Object>();
    config.put("wide_screen_mode", true);
    config.put("update_multi", true);

    Map<String, Object> title = new LinkedHashMap<String, Object>();
    title.put("tag", "plain_text");
    title.put("content", "绩效待办");

    Map<String, Object> header = new LinkedHashMap<String, Object>();
    header.put("template", "yellow");
    header.put("title", title);

    Map<String, Object> md = new LinkedHashMap<String, Object>();
    md.put("tag", "markdown");
    md.put("content", body);

    List<Map<String, Object>> elements = new ArrayList<Map<String, Object>>();
    elements.add(md);

    if (!ent.isEmpty()) {
      Map<String, Object> btnText = new LinkedHashMap<String, Object>();
      btnText.put("tag", "plain_text");
      btnText.put("content", "查看绩效详情");
      Map<String, Object> openBtn = new LinkedHashMap<String, Object>();
      openBtn.put("tag", "button");
      openBtn.put("type", "primary");
      openBtn.put("text", btnText);
      openBtn.put("url", ent);

      List<Map<String, Object>> actions = new ArrayList<Map<String, Object>>();
      actions.add(openBtn);

      Map<String, Object> actionBlock = new LinkedHashMap<String, Object>();
      actionBlock.put("tag", "action");
      actionBlock.put("layout", "flow");
      actionBlock.put("actions", actions);

      elements.add(actionBlock);
    }

    Map<String, Object> card = new LinkedHashMap<String, Object>();
    card.put("config", config);
    card.put("header", header);
    card.put("elements", elements);

    if (!ent.isEmpty()) {
      Map<String, Object> cardLink = new LinkedHashMap<String, Object>();
      cardLink.put("url", ent);
      card.put("card_link", cardLink);
    }
    return card;
  }

  public String fetchEmployeeDisplayName(String employeeId) {
    if (employeeId == null || employeeId.isEmpty()) {
      return "";
    }
    try {
      List<String> rows =
          jdbc.query(
              "SELECT name FROM employee_hierarchy WHERE employee_id = ? LIMIT 1",
              (rs, rn) -> rs.getString(1),
              employeeId);
      if (rows.isEmpty()) {
        return employeeId;
      }
      String n = rows.get(0);
      return n == null || n.trim().isEmpty() ? employeeId : n.trim();
    } catch (Exception e) {
      return employeeId;
    }
  }

  private String periodFromRow(Map<String, Object> r) {
    return r.get("period") == null ? "" : String.valueOf(r.get("period"));
  }

  private String employeeLineFromRow(Map<String, Object> r) {
    String empId = r.get("employeeId") == null ? "" : String.valueOf(r.get("employeeId"));
    String empName = fetchEmployeeDisplayName(empId);
    return "员工：" + empName;
  }

  /** 绩效流程状态（库内英文枚举）→ 卡片展示用中文，与前端 PERFORMANCE_STATUS_LABELS 对齐。 */
  private static String performanceStatusLabelZh(String status) {
    if (status == null || status.trim().isEmpty()) {
      return "未知";
    }
    switch (status.trim()) {
      case "template_selection":
      case "goal_setting":
        return "目标设定中";
      case "goal_pending_review":
        return "待审核目标";
      case "goal_rejected":
        return "目标被驳回";
      case "self_review":
        return "自评中";
      case "manager_review":
        return "上级评分中";
      case "dual_manager_review":
        return "上级并行评分中";
      case "dotted_manager_review":
        return "虚线上级评分中";
      case "final_review":
        return "待校准";
      case "issued":
        return "待员工确认";
      case "completed":
        return "已完成";
      default:
        return status.trim();
    }
  }

  /**
   * 一句说明 + 红色「周期：M月份绩效」+ 行动说明（无员工行，适合本人/管理员）。{@code recordId} 仅用于日志，不出现在正文。
   */
  private String buildTodoBodyLeadPeriodRecordCta(
      String lead, Map<String, Object> r, @SuppressWarnings("unused") String recordId, String cta) {
    String period = periodFromRow(r);
    return lead + "\n" + performancePeriodLineMarkdownRed(period) + "\n\n" + cta;
  }

  /**
   * 同上，含「员工：」一行（仅姓名，不含 open_id）。{@code recordId} 不出现在正文。
   */
  private String buildTodoBodyLeadPeriodRecordEmployeeCta(
      String lead, Map<String, Object> r, @SuppressWarnings("unused") String recordId, String cta) {
    String period = periodFromRow(r);
    return lead
        + "\n"
        + performancePeriodLineMarkdownRed(period)
        + "\n"
        + employeeLineFromRow(r)
        + "\n\n"
        + cta;
  }

  /** 将 {@code YYYY-MM} 等格式化为「3月份绩效」；无法解析时返回原字符串。 */
  private static String formatPerformancePeriodLabel(String period) {
    if (period == null || period.trim().isEmpty()) {
      return "绩效";
    }
    String p = period.trim();
    int dash = p.indexOf('-');
    if (dash <= 0 || dash >= p.length() - 1) {
      return p;
    }
    try {
      String monthPart = p.substring(dash + 1).trim();
      int idx2 = monthPart.indexOf('-');
      if (idx2 > 0) {
        monthPart = monthPart.substring(0, idx2);
      }
      int month = Integer.parseInt(monthPart);
      if (month < 1 || month > 12) {
        return p;
      }
      return month + "月份绩效";
    } catch (NumberFormatException e) {
      return p;
    }
  }

  /** 飞书卡片 Markdown：「周期：M月份绩效」整行红色。 */
  private static String performancePeriodLineMarkdownRed(String period) {
    String label = formatPerformancePeriodLabel(period);
    return "<font color='#DC2626'>周期：" + label + "</font>";
  }

  /**
   * @param recordIdForTask 非空且 {@code nodeKeyForTask} 非空时，在卡片发送成功后同步创建飞书待办
   */
  private void sendOne(
      String employeeId, String title, String textWithoutUrl, String recordIdForTask, String nodeKeyForTask) {
    String block = perfNotifyBlockReason();
    if (block != null) {
      log.info(
          "绩效飞书通知跳过 title={} reason={} employeeId={}",
          title,
          block,
          employeeId == null ? "" : employeeId.trim());
      return;
    }
    if (employeeId == null || employeeId.trim().isEmpty()) {
      log.warn("绩效飞书通知跳过 title={} reason=接收人员工 ID 为空", title);
      return;
    }
    Optional<FeishuEmployeeMessagingService.FeishuMessagingContext> opt =
        feishuEmployeeMessaging.resolveForEmployeeId(employeeId.trim());
    final FeishuEmployeeMessagingService.FeishuMessagingContext ctx =
        opt.orElseGet(() -> FeishuEmployeeMessagingService.FeishuMessagingContext.skipped("无员工上下文"));
    if (!ctx.isOk()) {
      log.info(
          "绩效飞书通知跳过 title={} reason={} employeeId={}",
          title,
          ctx.getSkipReason(),
          employeeId.trim());
      return;
    }
    if (!ctx.isSubjectPerformanceNotifyEnabled()) {
      log.info(
          "绩效飞书通知跳过 title={} reason=该主体已关闭绩效自动通知 employeeId={} subjectPerformanceNotifyEnabled=false",
          title,
          employeeId.trim());
      return;
    }
    String rid = ctx.getReceiveOpenId();
    if (rid == null || rid.trim().isEmpty()) {
      log.warn("绩效飞书通知跳过 title={} reason=接收人 open_id 为空 employeeId={}", title, employeeId.trim());
      return;
    }
    try {
      String token = feishuImService.fetchTenantAccessToken(ctx.getImAppId(), ctx.getImAppSecret());
      String entrance = effectiveNotifyEntranceUrl(ctx);
      Map<String, Object> card = buildPerformanceTodoCard(textWithoutUrl, entrance);
      String err = feishuImService.sendInteractiveCard(token, rid.trim(), card);
      if (err != null) {
        log.warn("绩效飞书通知失败 receiveId(open_id)={} title={} err={}", rid, title, err);
      } else {
        log.info("绩效飞书通知已发送(interactive) receiveId(open_id)={} title={}", rid, title);
      }
      if (recordIdForTask != null
          && nodeKeyForTask != null
          && !recordIdForTask.trim().isEmpty()
          && !nodeKeyForTask.trim().isEmpty()) {
        try {
          performanceFeishuTaskService.createAfterImSuccess(
              ctx,
              token,
              rid.trim(),
              employeeId.trim(),
              recordIdForTask.trim(),
              nodeKeyForTask.trim(),
              title,
              textWithoutUrl,
              entrance);
        } catch (Exception te) {
          log.warn("绩效飞书待办创建异常 recordId={} nodeKey={}", recordIdForTask, nodeKeyForTask, te);
        }
      }
    } catch (ResponseStatusException e) {
      log.warn(
          "绩效飞书通知失败(换 tenant token) receiveId(open_id)={} title={} httpStatus={} reason={}",
          rid,
          title,
          e.getStatus(),
          e.getReason());
    } catch (Exception e) {
      log.warn("绩效飞书通知异常 receiveId(open_id)={} title={}", rid, title, e);
    }
  }

  private void sendDistinct(
      Iterable<String> employeeIds,
      String title,
      String textWithoutUrl,
      String recordIdForTask,
      String nodeKeyForTask) {
    Set<String> seen = new LinkedHashSet<String>();
    for (String id : employeeIds) {
      if (id == null || id.trim().isEmpty()) {
        continue;
      }
      String t = id.trim();
      if (!seen.add(t)) {
        continue;
      }
      sendOne(t, title, textWithoutUrl, recordIdForTask, nodeKeyForTask);
    }
  }

  public List<String> listUserIdsWithMenuAllowed(String menuKey) {
    try {
      return jdbc.query(
          "SELECT DISTINCT ur.user_id FROM user_role ur INNER JOIN role_menu rm ON ur.role_key = rm.role_key WHERE rm.menu_key = ? AND rm.allowed = 1",
          (rs, rn) -> rs.getString(1),
          menuKey);
    } catch (Exception e) {
      log.warn("查询菜单权限用户失败 menuKey={}", menuKey, e);
      return new ArrayList<String>();
    }
  }

  public void notifyRecordCreated(String employeeId, String recordId, String period) {
    log.info(
        "绩效创建飞书通知入口 employeeId={} recordId={} period={} notifyEnabled={}",
        employeeId,
        recordId,
        period,
        properties.getFeishu().isPerformanceNotifyEnabled());
    String title = "【绩效】您有新的绩效待办";
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("period", period);
    String body =
        buildTodoBodyLeadPeriodRecordCta("已为您创建绩效。", row, recordId, "请设置目标设定。");
    sendOne(employeeId, title, body, recordId, "goal");
  }

  /** 选择模板后不再推送飞书（与创建绩效、目标审核等节点区分）。 */
  public void notifySelectTemplate(String employeeId, String recordId, String period) {
    log.info(
        "绩效飞书通知已跳过（选择模板节点不推送） employeeId={} recordId={} period={}",
        employeeId,
        recordId,
        period);
  }

  public void notifyGoalPendingReview(Map<String, Object> r, String recordId) {
    String mgr = r.get("managerId") == null ? null : String.valueOf(r.get("managerId"));
    String dot = r.get("dottedManagerId") == null ? null : String.valueOf(r.get("dottedManagerId"));
    String title = "【绩效】待审核目标设定";
    String body =
        buildTodoBodyLeadPeriodRecordEmployeeCta(
            "有待您审核的目标设定。当前流程状态："
                + performanceStatusLabelZh("goal_pending_review")
                + "。",
            r,
            recordId,
            "请您在系统中尽快通过或驳回。");
    List<String> to = new ArrayList<String>();
    if (mgr != null && !mgr.isEmpty()) {
      to.add(mgr);
    }
    if (dot != null && !dot.isEmpty()) {
      to.add(dot);
    }
    sendDistinct(to, title, body, recordId, "goal_review");
  }

  public void notifySelfSubmitted(Map<String, Object> r, String recordId, String newStatus) {
    String mgr = r.get("managerId") == null ? null : String.valueOf(r.get("managerId"));
    String dot = r.get("dottedManagerId") == null ? null : String.valueOf(r.get("dottedManagerId"));
    String title = "【绩效】待审核员工自评";
    String body =
        buildTodoBodyLeadPeriodRecordEmployeeCta(
            "员工已提交自评。当前流程状态：" + performanceStatusLabelZh(newStatus) + "。",
            r,
            recordId,
            "请您登录系统完成上级评分。");
    List<String> to = new ArrayList<String>();
    if ("manager_review".equals(newStatus) && mgr != null && !mgr.isEmpty()) {
      to.add(mgr);
    } else if ("dual_manager_review".equals(newStatus)) {
      if (mgr != null && !mgr.isEmpty()) {
        to.add(mgr);
      }
      if (dot != null && !dot.isEmpty()) {
        to.add(dot);
      }
    }
    sendDistinct(to, title, body, recordId, "manager");
  }

  public void notifyFinalReviewAdmins(Map<String, Object> r, String recordId) {
    List<String> admins = listUserIdsWithMenuAllowed("performance_review_admin");
    if (admins.isEmpty()) {
      log.info("绩效进入终审但无 performance_review_admin 用户，跳过飞书通知 recordId={}", recordId);
      return;
    }
    String title = "【绩效】待终审";
    String body =
        buildTodoBodyLeadPeriodRecordEmployeeCta(
            "有待您处理的绩效终审。当前流程状态："
                + performanceStatusLabelZh("final_review")
                + "。",
            r,
            recordId,
            "请您在管理端完成终审（通过/退回并填写原因）。");
    sendDistinct(admins, title, body, recordId, "final");
  }

  /**
   * 经理提交评分后的通知：进入终审通知管理员；双评中请对方继续评。
   */
  public void notifyAfterManagerSubmit(
      String actorUserId, Map<String, Object> r, String recordId, String oldStatus, String newStatus) {
    String mgr = r.get("managerId") == null ? null : String.valueOf(r.get("managerId"));
    String dot = r.get("dottedManagerId") == null ? null : String.valueOf(r.get("dottedManagerId"));

    if ("final_review".equals(newStatus) && !"final_review".equals(oldStatus)) {
      notifyFinalReviewAdmins(r, recordId);
      return;
    }
    if ("dual_manager_review".equals(newStatus) && "manager_review".equals(oldStatus) && dot != null && mgr != null && mgr.equals(actorUserId)) {
      String title = "【绩效】请完成虚线上级评分";
      String body =
          buildTodoBodyLeadPeriodRecordEmployeeCta(
              "直属上级已提交评分。当前流程状态："
                  + performanceStatusLabelZh("dual_manager_review")
                  + "。",
              r,
              recordId,
              "请您尽快完成虚线上级评分。");
      sendOne(dot, title, body, recordId, "manager");
      return;
    }
    if ("dual_manager_review".equals(newStatus) && "dual_manager_review".equals(oldStatus)) {
      if (mgr != null && mgr.equals(actorUserId) && dot != null) {
        String title = "【绩效】请完成虚线上级评分";
        String body =
            buildTodoBodyLeadPeriodRecordEmployeeCta(
                "直属上级已更新评分。当前流程状态："
                    + performanceStatusLabelZh("dual_manager_review")
                    + "。",
                r,
                recordId,
                "请您继续完成虚线上级侧评审（若尚未完成）。");
        sendOne(dot, title, body, recordId, "manager");
      }
    }
  }

  /** 虚线上级提交后的通知：进入终审；或双评时请直属上级继续。 */
  public void notifyAfterDottedSubmit(
      String actorUserId, Map<String, Object> r, String recordId, String oldStatus, String newStatus) {
    String mgr = r.get("managerId") == null ? null : String.valueOf(r.get("managerId"));
    String dot = r.get("dottedManagerId") == null ? null : String.valueOf(r.get("dottedManagerId"));

    if ("final_review".equals(newStatus) && !"final_review".equals(oldStatus)) {
      notifyFinalReviewAdmins(r, recordId);
      return;
    }
    if ("dual_manager_review".equals(newStatus) && "dual_manager_review".equals(oldStatus)) {
      if (dot != null && dot.equals(actorUserId) && mgr != null) {
        String title = "【绩效】请完成直属上级评分";
        String body =
            buildTodoBodyLeadPeriodRecordEmployeeCta(
                "虚线上级已更新评分。当前流程状态："
                    + performanceStatusLabelZh("dual_manager_review")
                    + "。",
                r,
                recordId,
                "请您继续完成直属上级侧评审（若尚未完成）。");
        sendOne(mgr, title, body, recordId, "manager");
      }
    }
  }

  public void notifyApproveGoal(
      Map<String, Object> r, String recordId, boolean approved, String rejectionReason) {
    String emp = String.valueOf(r.get("employeeId"));
    String title =
        approved ? "【绩效】目标设定已通过" : "【绩效】驳回待目标设定";
    String body;
    if (approved) {
      body =
          buildTodoBodyLeadPeriodRecordCta(
              "您的目标设定已通过上级审核。当前流程状态："
                  + performanceStatusLabelZh("self_review")
                  + "。",
              r,
              recordId,
              "请进行「自评」并提交。");
    } else {
      String reason = rejectionReason == null ? "" : rejectionReason;
      body =
          buildTodoBodyLeadPeriodRecordCta(
              "您的目标设定未通过审核。当前流程状态："
                  + performanceStatusLabelZh("goal_rejected")
                  + "。",
              r,
              recordId,
              "原因：" + reason + "\n\n请根据反馈修改后重新提交目标。");
    }
    sendOne(emp, title, body, recordId, approved ? "self" : "goal");
  }

  public void notifyRejectToEmployee(Map<String, Object> r, String recordId, String reason) {
    String emp = String.valueOf(r.get("employeeId"));
    String title = "【绩效】驳回待自评";
    String body =
        buildTodoBodyLeadPeriodRecordCta(
            "上级已驳回您的自评/评分，流程已退回至「"
                + performanceStatusLabelZh("self_review")
                + "」。请修改自评后重新提交。",
            r,
            recordId,
            "原因：" + (reason == null ? "" : reason) + "\n\n请登录系统查看并修改后重新提交自评。");
    sendOne(emp, title, body, recordId, "self");
  }

  public void notifyFinalReviewToEmployee(
      Map<String, Object> r, String recordId, boolean approved, String newStatus, String rejectionReason) {
    String emp = String.valueOf(r.get("employeeId"));
    if (approved) {
      String title = "【绩效】终审已通过";
      String body =
          buildTodoBodyLeadPeriodRecordCta(
              "您的绩效终审已通过。当前流程状态：" + performanceStatusLabelZh("completed") + "。",
              r,
              recordId,
              "感谢您的配合。");
      sendOne(emp, title, body, null, null);
    } else {
      String title = "【绩效】终审退回";
      String body =
          buildTodoBodyLeadPeriodRecordCta(
              "终审未通过，流程已退回至：" + performanceStatusLabelZh(newStatus) + "。",
              r,
              recordId,
              "原因：" + (rejectionReason == null ? "" : rejectionReason) + "\n\n请登录系统查看详情并处理。");
      String nk = PerformanceFeishuTaskService.statusToNodeKey(newStatus);
      sendOne(emp, title, body, recordId, nk == null ? "" : nk);
    }
  }

  public void notifyCalibrateToEmployee(
      Map<String, Object> r, String recordId, boolean approved, String newStatus, String rejectionReason) {
    String emp = String.valueOf(r.get("employeeId"));
    if (approved) {
      String title = "【绩效】结果已下发，请确认";
      String body =
          buildTodoBodyLeadPeriodRecordCta(
              "绩效校准已完成，结果已下发。当前流程状态：" + performanceStatusLabelZh("issued") + "。",
              r,
              recordId,
              "请登录系统查看绩效结果并确认。");
      sendOne(emp, title, body, recordId, "confirm");
    } else {
      String title = "【绩效】校准退回";
      String body =
          buildTodoBodyLeadPeriodRecordCta(
              "绩效校准未通过，流程已退回至：" + performanceStatusLabelZh(newStatus) + "。",
              r,
              recordId,
              "原因：" + (rejectionReason == null ? "" : rejectionReason) + "\n\n请登录系统查看并处理。");
      String nk = PerformanceFeishuTaskService.statusToNodeKey(newStatus);
      sendOne(emp, title, body, recordId, nk == null ? "" : nk);
    }
  }
}
