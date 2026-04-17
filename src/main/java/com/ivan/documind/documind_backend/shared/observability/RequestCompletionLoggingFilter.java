package com.ivan.documind.documind_backend.shared.observability;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestCompletionLoggingFilter extends OncePerRequestFilter {
  private static final Logger logger = LoggerFactory.getLogger(RequestCompletionLoggingFilter.class);

  private final String service;
  private final String environment;
  private final String release;

  public RequestCompletionLoggingFilter(
      @Value("${spring.application.name:documind-backend}") String service,
      @Value("${app.environment:local}") String environment,
      @Value("${app.release:dev}") String release) {
    this.service = service;
    this.environment = environment;
    this.release = release;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    long start = System.currentTimeMillis();
    Exception failure = null;

    try {
      filterChain.doFilter(request, response);
    } catch (ServletException | IOException | RuntimeException exception) {
      failure = exception;
      throw exception;
    } finally {
      long latencyMs = System.currentTimeMillis() - start;
      int status = resolveStatus(response, failure);
      String outcome = status >= 400 ? "error" : "success";

      logger.info(
          "event=request_completed service={} environment={} release={} requestId={} method={} path={} status={} outcome={} latencyMs={}",
          service,
          environment,
          release,
          resolveRequestId(request),
          request.getMethod(),
          request.getRequestURI(),
          status,
          outcome,
          latencyMs);
    }
  }

  private int resolveStatus(HttpServletResponse response, Exception failure) {
    int status = response.getStatus();

    if (failure != null && status < 400) {
      return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    return status;
  }

  private String resolveRequestId(HttpServletRequest request) {
    return ObservabilityLogValues.requestId(request);
  }
}
