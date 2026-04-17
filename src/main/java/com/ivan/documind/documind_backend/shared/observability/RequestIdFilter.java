package com.ivan.documind.documind_backend.shared.observability;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String requestId = RequestId.resolve(request.getHeader(RequestId.HEADER_NAME));

    request.setAttribute(RequestId.ATTRIBUTE_NAME, requestId);
    response.setHeader(RequestId.HEADER_NAME, requestId);

    filterChain.doFilter(request, response);
  }
}
