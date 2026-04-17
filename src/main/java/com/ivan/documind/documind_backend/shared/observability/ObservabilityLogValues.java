package com.ivan.documind.documind_backend.shared.observability;

import jakarta.servlet.http.HttpServletRequest;

public final class ObservabilityLogValues {
  private static final int MAX_MESSAGE_LENGTH = 300;

  private ObservabilityLogValues() {
  }

  public static String requestId(HttpServletRequest request) {
    Object requestId = request.getAttribute(RequestId.ATTRIBUTE_NAME);

    if (requestId instanceof String value && RequestId.isValid(value)) {
      return value;
    }

    return "unknown";
  }

  public static String sanitizeMessage(String message) {
    if (message == null || message.isBlank()) {
      return "none";
    }

    String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();

    if (sanitized.length() <= MAX_MESSAGE_LENGTH) {
      return sanitized;
    }

    return sanitized.substring(0, MAX_MESSAGE_LENGTH) + "...";
  }
}
