package com.jixiao2.server.feishu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 飞书角标 H5 前端调试日志（写入独立文件，便于飞书内无法开 Console 时排查） */
@Service
public class FeishuBadgeClientLogService {

  private static final Path LOG_FILE = Paths.get("logs", "feishu-badge-client.log");
  private static final int MAX_LINES_PER_REQUEST = 40;
  private static final int MAX_LINE_LENGTH = 2000;

  private final Object lock = new Object();

  public int append(String employeeId, List<?> rawLines) {
    if (rawLines == null || rawLines.isEmpty()) {
      return 0;
    }
    List<String> lines = new ArrayList<String>();
    int n = 0;
    for (Object o : rawLines) {
      if (n >= MAX_LINES_PER_REQUEST) {
        break;
      }
      if (o == null) {
        continue;
      }
      String s = String.valueOf(o).replace('\r', ' ').replace('\n', ' ');
      if (s.length() > MAX_LINE_LENGTH) {
        s = s.substring(0, MAX_LINE_LENGTH) + "...";
      }
      if (!s.isEmpty()) {
        lines.add(s);
        n++;
      }
    }
    if (lines.isEmpty()) {
      return 0;
    }
    String ts = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    String emp = employeeId != null && !employeeId.isEmpty() ? employeeId : "-";
    StringBuilder batch = new StringBuilder();
    for (String line : lines) {
      batch.append(ts).append(" employeeId=").append(emp).append(" ").append(line).append('\n');
    }
    synchronized (lock) {
      try {
        Files.createDirectories(LOG_FILE.getParent());
        Files.write(
            LOG_FILE,
            batch.toString().getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      } catch (IOException e) {
        throw new IllegalStateException("写入角标客户端日志失败", e);
      }
    }
    return lines.size();
  }
}
