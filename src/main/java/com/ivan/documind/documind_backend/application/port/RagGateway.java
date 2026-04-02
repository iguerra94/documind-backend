package com.ivan.documind.documind_backend.application.port;

import com.ivan.documind.documind_backend.domain.model.Question;
import com.ivan.documind.documind_backend.domain.model.RagAnswer;

public interface RagGateway {
  RagAnswer ask(Question question);
}
