package com.ivan.documind.documind_backend.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletResponse;

class RequestCompletionLoggingFilterTest {
  private static final String REQUEST_ID = "request-123";

  private final RequestCompletionLoggingFilter filter = new RequestCompletionLoggingFilter(
      "documind-backend",
      "test",
      "commit-test");

  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(RequestCompletionLoggingFilter.class);
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
  void logsSuccessfulRequestCompletion() throws Exception {
    MockHttpServletRequest request = newRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
      ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
    });

    String log = onlyLogMessage();

    assertThat(log).contains("event=request_completed");
    assertThat(log).contains("service=documind-backend");
    assertThat(log).contains("environment=test");
    assertThat(log).contains("release=commit-test");
    assertThat(log).contains("requestId=" + REQUEST_ID);
    assertThat(log).contains("method=POST");
    assertThat(log).contains("path=/questions");
    assertThat(log).contains("status=200");
    assertThat(log).contains("outcome=success");
    assertThat(log).containsPattern("latencyMs=\\d+");
  }

  @Test
  void logsFailedRequestCompletion() throws Exception {
    MockHttpServletRequest request = newRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
      ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    });

    String log = onlyLogMessage();

    assertThat(log).contains("requestId=" + REQUEST_ID);
    assertThat(log).contains("method=POST");
    assertThat(log).contains("path=/questions");
    assertThat(log).contains("status=400");
    assertThat(log).contains("outcome=error");
    assertThat(log).containsPattern("latencyMs=\\d+");
  }

  @Test
  void doesNotLogRequestBodyOrAuthorizationHeader() throws Exception {
    MockHttpServletRequest request = newRequest();
    request.addHeader("Authorization", "Bearer secret-token");
    request.setContent("{\"question\":\"sensitive question text\"}".getBytes(StandardCharsets.UTF_8));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
      ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
    });

    String log = onlyLogMessage();

    assertThat(log).doesNotContain("Authorization");
    assertThat(log).doesNotContain("Bearer secret-token");
    assertThat(log).doesNotContain("sensitive question text");
    assertThat(log).doesNotContain("{\"question\":\"sensitive question text\"}");
  }

  private MockHttpServletRequest newRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/questions");
    request.setAttribute(RequestId.ATTRIBUTE_NAME, REQUEST_ID);
    return request;
  }

  private String onlyLogMessage() {
    List<ILoggingEvent> logs = appender.list;

    assertThat(logs).hasSize(1);

    return logs.get(0).getFormattedMessage();
  }
}
