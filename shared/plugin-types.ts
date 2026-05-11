// ---- plugin:send_performance_notification_1 ----
// ============================================================
// 插件 send_performance_notification_1 (绩效通知批量发送) 的类型定义
// 由 get_plugin_ai_json 自动生成
// ============================================================

export interface SendPerformanceNotificationOneInput {
  /** 绩效通知标题 */
  notification_title: string;
  /** 绩效通知内容，支持Markdown格式 */
  notification_content: string;
  /** 接收绩效通知的用户ID列表 */
  receiver_user_ids: string[];
}

/**
 * capabilityClient.load('send_performance_notification_1').call<SendPerformanceNotificationOneOutput>('send_feishu_message', input)
 * 直接返回此类型，无 .data 包装，直接解构使用：
 * const { success } = result;
 */
export interface SendPerformanceNotificationOneOutput {
  /** [object Object] */
  success: boolean;
}
// ---- end:send_performance_notification_1 ----