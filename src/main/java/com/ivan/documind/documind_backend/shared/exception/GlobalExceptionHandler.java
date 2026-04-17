package com.ivan.documind.documind_backend.shared.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ivan.documind.documind_backend.shared.observability.ObservabilityLogValues;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidationException(
      MethodArgumentNotValidException exception,
      HttpServletRequest request) {
    List<String> details = exception.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .toList();

    ApiErrorResponse response = new ApiErrorResponse(
        LocalDateTime.now(),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        "Validation failed",
        request.getRequestURI(),
        details);

    logRequestFailure(request, HttpStatus.BAD_REQUEST, exception, "Validation failed");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleMalformedJsonException(
      HttpMessageNotReadableException exception,
      HttpServletRequest request) {
    ApiErrorResponse response = new ApiErrorResponse(
        LocalDateTime.now(),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        "Malformed JSON request",
        request.getRequestURI(),
        List.of(exception.getMostSpecificCause().getMessage()));

    logRequestFailure(request, HttpStatus.BAD_REQUEST, exception, "Malformed JSON request");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(QuestionProcessingException.class)
  public ResponseEntity<ApiErrorResponse> handleQuestionProcessingException(
      QuestionProcessingException exception,
      HttpServletRequest request) {
    ApiErrorResponse response = new ApiErrorResponse(
        LocalDateTime.now(),
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        exception.getMessage(),
        request.getRequestURI(),
        List.of());

    logRequestFailure(request, HttpStatus.BAD_REQUEST, exception, exception.getMessage());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleGenericException(
      Exception exception,
      HttpServletRequest request) {
    ApiErrorResponse response = new ApiErrorResponse(
        LocalDateTime.now(),
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
        "An unexpected error occurred",
        request.getRequestURI(),
        List.of(exception.getMessage()));

    logRequestFailure(request, HttpStatus.INTERNAL_SERVER_ERROR, exception, "An unexpected error occurred");

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }

  private void logRequestFailure(
      HttpServletRequest request,
      HttpStatus status,
      Exception exception,
      String message) {
    logger.warn(
        "event=request_failed requestId={} method={} path={} status={} exceptionType={} exceptionMessage={}",
        ObservabilityLogValues.requestId(request),
        request.getMethod(),
        request.getRequestURI(),
        status.value(),
        exception.getClass().getSimpleName(),
        ObservabilityLogValues.sanitizeMessage(message));
  }
}
