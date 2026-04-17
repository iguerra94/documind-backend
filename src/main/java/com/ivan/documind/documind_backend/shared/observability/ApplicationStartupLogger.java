package com.ivan.documind.documind_backend.shared.observability;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupLogger {
  private static final Logger logger = LoggerFactory.getLogger(ApplicationStartupLogger.class);

  private final String service;
  private final String appEnvironment;
  private final String release;
  private final String awsRegion;
  private final Environment springEnvironment;

  public ApplicationStartupLogger(
      @Value("${spring.application.name:documind-backend}") String service,
      @Value("${app.environment:local}") String appEnvironment,
      @Value("${app.release:dev}") String release,
      @Value("${aws.region:us-east-1}") String awsRegion,
      Environment springEnvironment) {
    this.service = service;
    this.appEnvironment = appEnvironment;
    this.release = release;
    this.awsRegion = awsRegion;
    this.springEnvironment = springEnvironment;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void logApplicationStarted() {
    logger.info(
        "event=application_started service={} environment={} release={} aws.region={} spring.profiles.active={}",
        service,
        appEnvironment,
        release,
        awsRegion,
        activeProfiles());
  }

  private String activeProfiles() {
    String[] profiles = springEnvironment.getActiveProfiles();

    if (profiles.length == 0) {
      return "default";
    }

    return String.join(",", Arrays.stream(profiles).sorted().toList());
  }
}
