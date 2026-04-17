package com.ivan.documind.documind_backend.shared.observability;

import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestId {
  public static final String HEADER_NAME = "X-Request-Id";
  public static final String ATTRIBUTE_NAME = "requestId";

  private static final Pattern ALLOWED_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,128}");

  private RequestId() {
  }

  public static String resolve(String candidate) {
    if (isValid(candidate)) {
      return candidate;
    }

    return UUID.randomUUID().toString();
  }

  public static boolean isValid(String candidate) {
    return candidate != null && ALLOWED_VALUE.matcher(candidate).matches();
  }
}
