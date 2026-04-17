package com.ivan.documind.documind_backend.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.ivan.documind.documind_backend.shared.observability.RequestId;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class GlobalExceptionHandlerLoggingTest {
  private static final String REQUEST_ID = "request-123";

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
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
  void logsValidationErrorWithRequestIdAndBadRequestStatus() throws Exception {
    MockHttpServletRequest request = newRequest();
    MethodArgumentNotValidException exception = validationException();

    handler.handleValidationException(exception, request);

    String log = onlyLogMessage();

    assertThat(log).contains("event=request_failed");
    assertThat(log).contains("requestId=" + REQUEST_ID);
    assertThat(log).contains("method=POST");
    assertThat(log).contains("path=/questions");
    assertThat(log).contains("status=400");
    assertThat(log).contains("exceptionType=MethodArgumentNotValidException");
    assertThat(log).contains("exceptionMessage=Validation failed");
  }

  @Test
  void logsMalformedJsonErrorWithoutBodyContent() {
    MockHttpServletRequest request = newRequest();
    request.setContent("{\"question\":\"sensitive question text\"}".getBytes());
    request.addHeader("Authorization", "Bearer secret-token");
    HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
        "Malformed JSON request\n{\"question\":\"sensitive question text\"}",
        null);

    handler.handleMalformedJsonException(exception, request);

    String log = onlyLogMessage();

    assertThat(log).contains("event=request_failed");
    assertThat(log).contains("requestId=" + REQUEST_ID);
    assertThat(log).contains("status=400");
    assertThat(log).contains("exceptionType=HttpMessageNotReadableException");
    assertThat(log).contains("exceptionMessage=Malformed JSON request");
    assertThat(log).doesNotContain("Authorization");
    assertThat(log).doesNotContain("Bearer secret-token");
    assertThat(log).doesNotContain("sensitive question text");
    assertThat(log).doesNotContain("{\"question\":\"sensitive question text\"}");
  }

  @Test
  void logsGenericErrorWithRequestIdAndInternalServerErrorStatus() {
    MockHttpServletRequest request = newRequest();
    request.setContent("{\"question\":\"sensitive question text\"}".getBytes());
    request.addHeader("Authorization", "Bearer secret-token");

    handler.handleGenericException(new IllegalStateException("secret failure details"), request);

    String log = onlyLogMessage();

    assertThat(log).contains("event=request_failed");
    assertThat(log).contains("requestId=" + REQUEST_ID);
    assertThat(log).contains("method=POST");
    assertThat(log).contains("path=/questions");
    assertThat(log).contains("status=500");
    assertThat(log).contains("exceptionType=IllegalStateException");
    assertThat(log).contains("exceptionMessage=An unexpected error occurred");
    assertThat(log).doesNotContain("Authorization");
    assertThat(log).doesNotContain("Bearer secret-token");
    assertThat(log).doesNotContain("sensitive question text");
    assertThat(log).doesNotContain("secret failure details");
  }

  private MockHttpServletRequest newRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/questions");
    request.setAttribute(RequestId.ATTRIBUTE_NAME, REQUEST_ID);
    return request;
  }

  private MethodArgumentNotValidException validationException() throws Exception {
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "questionRequest");
    bindingResult.addError(new FieldError("questionRequest", "question", "question must have a value"));
    Method method = GlobalExceptionHandlerLoggingTest.class.getDeclaredMethod("validationTarget", String.class);

    return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
  }

  @SuppressWarnings("unused")
  private void validationTarget(String question) {
  }

  private String onlyLogMessage() {
    List<ILoggingEvent> logs = appender.list;

    assertThat(logs).hasSize(1);

    return logs.get(0).getFormattedMessage();
  }
}
