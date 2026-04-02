package com.ivan.documind.documind_backend.shared.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ApiErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    List<String> details) {
}
