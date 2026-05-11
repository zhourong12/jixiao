package com.jixiao2.server;

import com.jixiao2.server.config.Jixiao2Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(Jixiao2Properties.class)
@EnableScheduling
public class Jixiao2ServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(Jixiao2ServerApplication.class, args);
  }
}
