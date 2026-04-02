package com.ivan.documind.documind_backend.api.dto.response;

import jakarta.validation.constraints.NotBlank;

public record QuestionRequest(@NotBlank(message = "question must have a value") String question) {
}