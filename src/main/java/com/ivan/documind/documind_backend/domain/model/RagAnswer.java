package com.ivan.documind.documind_backend.domain.model;

import java.util.List;

public record RagAnswer(String answer, List<Citation> citations) {
}
