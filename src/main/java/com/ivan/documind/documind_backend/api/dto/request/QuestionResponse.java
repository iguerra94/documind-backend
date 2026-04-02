package com.ivan.documind.documind_backend.api.dto.request;

import java.util.List;

import com.ivan.documind.documind_backend.api.dto.response.CitationResponse;

public record QuestionResponse(String answer, List<CitationResponse> citations, long latencyMs) {
}
