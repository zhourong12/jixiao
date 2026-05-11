package com.jixiao2.server.home;

import com.jixiao2.server.web.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeController {

  private final HomeService homeService;

  public HomeController(HomeService homeService) {
    this.homeService = homeService;
  }

  @GetMapping("/todos")
  public Map<String, Object> todos(
      @CurrentUser String userId,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month) {
    return homeService.getTodos(userId, year, month);
  }

  @GetMapping("/overview")
  public Map<String, Object> overview(
      @CurrentUser String userId,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month) {
    return homeService.getOverview(userId, year, month);
  }

  @GetMapping("/action-counts")
  public Map<String, Object> actionCounts(
      @CurrentUser String userId,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month) {
    return homeService.getActionCounts(userId, year, month);
  }
}
