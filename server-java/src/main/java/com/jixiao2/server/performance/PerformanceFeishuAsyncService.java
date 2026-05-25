package com.jixiao2.server.performance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 绩效飞书 IM 通知与待办任务异步派发，不阻塞主流程 HTTP/批处理事务。 */
@Service
public class PerformanceFeishuAsyncService {

  private static final Logger log = LoggerFactory.getLogger(PerformanceFeishuAsyncService.class);

  private final PerformanceFeishuNotifier notifier;
  private final PerformanceFeishuTaskService taskService;
  private final PerformanceFeishuLogNames logNames;
  private final ExecutorService executor =
      Executors.newFixedThreadPool(
          4,
          new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
              Thread t = new Thread(r, "feishu-perf-notify-" + n.incrementAndGet());
              t.setDaemon(true);
              return t;
            }
          });

  public PerformanceFeishuAsyncService(
      PerformanceFeishuNotifier notifier,
      PerformanceFeishuTaskService taskService,
      PerformanceFeishuLogNames logNames) {
    this.notifier = notifier;
    this.taskService = taskService;
    this.logNames = logNames;
  }

  public PerformanceFeishuNotifier notifier() {
    return notifier;
  }

  public PerformanceFeishuTaskService taskService() {
    return taskService;
  }

  public void dispatch(Runnable action) {
    if (action == null) {
      return;
    }
    executor.execute(
        () -> {
          try {
            action.run();
          } catch (Exception e) {
            log.warn("绩效飞书异步任务失败", e);
          }
        });
  }

  public static final class RecordCreatedItem {
    public final String employeeId;
    public final String recordId;
    public final String period;
    public final String nodeDeadlinesJson;

    public RecordCreatedItem(
        String employeeId, String recordId, String period, String nodeDeadlinesJson) {
      this.employeeId = employeeId;
      this.recordId = recordId;
      this.period = period;
      this.nodeDeadlinesJson = nodeDeadlinesJson;
    }
  }

  /** 批量创建绩效：主流程结束后一次性异步批量发送创建通知。 */
  public void dispatchRecordCreatedBatch(List<RecordCreatedItem> items) {
    if (items == null || items.isEmpty()) {
      return;
    }
    List<RecordCreatedItem> copy = Collections.unmodifiableList(new ArrayList<>(items));
    dispatch(
        () -> {
          log.info("绩效飞书批量创建通知开始 count={}", copy.size());
          int sent = 0;
          for (RecordCreatedItem it : copy) {
            try {
              notifier.notifyRecordCreated(
                  it.employeeId, it.recordId, it.period, it.nodeDeadlinesJson);
              sent++;
            } catch (Exception e) {
              log.warn(
                  "绩效飞书批量创建通知失败 nodeKey=goal recordId={} {}",
                  it.recordId,
                  logNames.assignee(it.employeeId),
                  e);
            }
          }
          log.info("绩效飞书批量创建通知结束 total={} sent={}", copy.size(), sent);
        });
  }
}
