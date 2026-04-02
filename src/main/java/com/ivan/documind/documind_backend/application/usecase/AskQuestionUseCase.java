package com.ivan.documind.documind_backend.application.usecase;

import org.springframework.stereotype.Service;

import com.ivan.documind.documind_backend.application.port.RagGateway;
import com.ivan.documind.documind_backend.domain.model.Question;
import com.ivan.documind.documind_backend.domain.model.RagAnswer;

@Service
public class AskQuestionUseCase {
  private final RagGateway ragGateway;

  public AskQuestionUseCase(RagGateway ragGateway) {
    this.ragGateway = ragGateway;
  }

  public RagAnswer execute(String rawQuestion) {
    Question question = new Question(rawQuestion);
    return ragGateway.ask(question);
  }
}
