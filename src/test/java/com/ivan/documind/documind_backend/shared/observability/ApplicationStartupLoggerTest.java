package com.ivan.documind.documind_backend.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ApplicationStartupLoggerTest {
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(ApplicationStartupLogger.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void logsStartupMetadata() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    ApplicationStartupLogger startupLogger = new ApplicationStartupLogger(
        "documind-backend",
        "test",
        "release-123",
        "us-east-1",
        environment);

    startupLogger.logApplicationStarted();

    String log = onlyLogMessage();

    assertThat(log).contains("event=application_started");
    assertThat(log).contains("service=documind-backend");
    assertThat(log).contains("environment=test");
    assertThat(log).contains("release=release-123");
    assertThat(log).contains("aws.region=us-east-1");
    assertThat(log).contains("spring.profiles.active=prod");
  }

  private String onlyLogMessage() {
    List<ILoggingEvent> logs = appender.list;

    assertThat(logs).hasSize(1);

    return logs.get(0).getFormattedMessage();
  }
}
