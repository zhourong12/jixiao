package com.jixiao2.server.config;

import com.jixiao2.server.web.CurrentUserArgumentResolver;
import com.jixiao2.server.web.SessionAuthInterceptor;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final SessionAuthInterceptor sessionAuthInterceptor;
  private final CurrentUserArgumentResolver currentUserArgumentResolver;
  private final Jixiao2Properties jixiao2Properties;

  public WebConfig(
      SessionAuthInterceptor sessionAuthInterceptor,
      CurrentUserArgumentResolver currentUserArgumentResolver,
      Jixiao2Properties jixiao2Properties) {
    this.sessionAuthInterceptor = sessionAuthInterceptor;
    this.currentUserArgumentResolver = currentUserArgumentResolver;
    this.jixiao2Properties = jixiao2Properties;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    List<String> patterns = jixiao2Properties.getCors().originPatternList();
    if (patterns.isEmpty()) {
      patterns = Arrays.asList("http://localhost:*", "http://127.0.0.1:*");
    }
    registry
        .addMapping("/**")
        .allowedOriginPatterns(patterns.toArray(new String[0]))
        .allowCredentials(true)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("Set-Cookie");
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(sessionAuthInterceptor).addPathPatterns("/api/**");
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentUserArgumentResolver);
  }
}
