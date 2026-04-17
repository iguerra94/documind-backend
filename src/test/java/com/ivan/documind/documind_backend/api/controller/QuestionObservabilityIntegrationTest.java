package com.ivan.documind.documind_backend.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.ivan.documind.documind_backend.shared.exception.GlobalExceptionHandler;
import com.ivan.documind.documind_backend.shared.observability.RequestCompletionLoggingFilter;
import com.ivan.documind.documind_backend.shared.observability.RequestId;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest(properties = {
    "app.environment=test",
    "app.release=test-release"
})
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class QuestionObservabilityIntegrationTest {
  private static final String AUTHORIZATION_HEADER_VALUE = "Bearer secret-token";
  private static final String SENSITIVE_QUESTION = "sensitive question text";
  private static final String SUCCESS_REQUEST_BODY = "{\"question\":\"" + SENSITIVE_QUESTION + "\"}";
  private static final String MOCK_RESPONSE_TEXT =
      "The orders service authenticates requests using JWT tokens passed through the Authorization header.";

  @Autowired
  private MockMvc mockMvc;

  private Logger requestCompletionLogger;
  private Logger exceptionHandlerLogger;
  private ListAppender<ILoggingEvent> requestCompletionAppender;
  private ListAppender<ILoggingEvent> exceptionHandlerAppender;

  @BeforeEach
  void setUp() {
    requestCompletionLogger = (Logger) LoggerFactory.getLogger(RequestCompletionLoggingFilter.class);
    exceptionHandlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    requestCompletionAppender = new ListAppender<>();
    exceptionHandlerAppender = new ListAppender<>();
    requestCompletionAppender.start();
    exceptionHandlerAppender.start();
    requestCompletionLogger.addAppender(requestCompletionAppender);
    exceptionHandlerLogger.addAppender(exceptionHandlerAppender);
  }

  @AfterEach
  void tearDown() {
    requestCompletionLogger.detachAppender(requestCompletionAppender);
    exceptionHandlerLogger.detachAppender(exceptionHandlerAppender);
    requestCompletionAppender.stop();
    exceptionHandlerAppender.stop();
  }

  @Test
  void successfulQuestionRequestReturnsRequestIdHeaderWithoutChangingResponseBody() throws Exception {
    MvcResult result = mockMvc.perform(post("/questions")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
        .content(SUCCESS_REQUEST_BODY))
        .andExpect(status().isOk())
        .andReturn();

    String requestId = result.getResponse().getHeader(RequestId.HEADER_NAME);
    String responseBody = result.getResponse().getContentAsString();
    String logs = allCapturedLogs();

    assertThat(requestId).isNotBlank();
    assertThat(RequestId.isValid(requestId)).isTrue();
    assertThat(responseBody).doesNotContain("requestId");
    assertThat(responseBody).doesNotContain(RequestId.HEADER_NAME);
    assertThat(logs).contains("event=request_completed");
    assertThat(logs).contains("requestId=" + requestId);
    assertThat(logs).contains("method=POST");
    assertThat(logs).contains("path=/questions");
    assertThat(logs).contains("status=200");
    assertThat(logs).contains("outcome=success");
    assertThat(logs).containsPattern("latencyMs=\\d+");
    assertNoSensitiveValuesInLogs(logs);
  }

  @Test
  void successfulQuestionRequestReusesValidRequestIdHeader() throws Exception {
    String incomingRequestId = "client-request_123.abc";

    MvcResult result = mockMvc.perform(post("/questions")
        .contentType(MediaType.APPLICATION_JSON)
        .header(RequestId.HEADER_NAME, incomingRequestId)
        .content(SUCCESS_REQUEST_BODY))
        .andExpect(status().isOk())
        .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    String logs = allCapturedLogs();

    assertThat(result.getResponse().getHeader(RequestId.HEADER_NAME)).isEqualTo(incomingRequestId);
    assertThat(responseBody).doesNotContain("requestId");
    assertThat(logs).contains("requestId=" + incomingRequestId);
    assertNoSensitiveValuesInLogs(logs);
  }

  @Test
  void invalidQuestionRequestReturnsRequestIdHeaderWithoutChangingErrorBody() throws Exception {
    String requestBody = "{\"question\":\"\"}";

    MvcResult result = mockMvc.perform(post("/questions")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", AUTHORIZATION_HEADER_VALUE)
        .content(requestBody))
        .andExpect(status().isBadRequest())
        .andReturn();

    String requestId = result.getResponse().getHeader(RequestId.HEADER_NAME);
    String responseBody = result.getResponse().getContentAsString();
    String logs = allCapturedLogs();

    assertThat(requestId).isNotBlank();
    assertThat(RequestId.isValid(requestId)).isTrue();
    assertThat(responseBody).doesNotContain("requestId");
    assertThat(responseBody).doesNotContain(RequestId.HEADER_NAME);
    assertThat(logs).contains("event=request_failed");
    assertThat(logs).contains("event=request_completed");
    assertThat(logs).contains("requestId=" + requestId);
    assertThat(logs).contains("status=400");
    assertThat(logs).contains("outcome=error");
    assertThat(logs).doesNotContain(requestBody);
    assertNoSensitiveValuesInLogs(logs);
  }

  private String allCapturedLogs() {
    return Stream.concat(requestCompletionAppender.list.stream(), exceptionHandlerAppender.list.stream())
        .map(ILoggingEvent::getFormattedMessage)
        .collect(Collectors.joining("\n"));
  }

  private void assertNoSensitiveValuesInLogs(String logs) {
    List<String> forbiddenValues = List.of(
        "Authorization",
        AUTHORIZATION_HEADER_VALUE,
        SUCCESS_REQUEST_BODY,
        SENSITIVE_QUESTION,
        MOCK_RESPONSE_TEXT);

    for (String forbiddenValue : forbiddenValues) {
      assertThat(logs).doesNotContain(forbiddenValue);
    }
  }
}
