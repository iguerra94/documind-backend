package com.ivan.documind.documind_backend.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ivan.documind.documind_backend.api.dto.request.QuestionResponse;
import com.ivan.documind.documind_backend.api.dto.response.CitationResponse;
import com.ivan.documind.documind_backend.api.dto.response.QuestionRequest;
import com.ivan.documind.documind_backend.application.usecase.AskQuestionUseCase;
import com.ivan.documind.documind_backend.domain.model.RagAnswer;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/questions")
public class QuestionController {
  private final AskQuestionUseCase askQuestionUseCase;

  public QuestionController(AskQuestionUseCase askQuestionUseCase) {
    this.askQuestionUseCase = askQuestionUseCase;
  }

  @PostMapping
  public QuestionResponse askQuestion(@Valid @RequestBody QuestionRequest questionRequest) {
    long start = System.currentTimeMillis();

    RagAnswer ragAnswer = askQuestionUseCase.execute(questionRequest.question());

    long latencyMs = System.currentTimeMillis() - start;

    List<CitationResponse> citations = ragAnswer.citations()
        .stream()
        .map((citation) -> new CitationResponse(citation.title(), citation.snippet()))
        .toList();

    return new QuestionResponse(
        ragAnswer.answer(),
        citations,
        latencyMs);
  }
}
