package com.ivan.documind.documind_backend.infrastructure.adapter.mock;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.ivan.documind.documind_backend.application.port.RagGateway;
import com.ivan.documind.documind_backend.domain.model.Citation;
import com.ivan.documind.documind_backend.domain.model.Question;
import com.ivan.documind.documind_backend.domain.model.RagAnswer;

@Component
@Profile("mock")
public class MockRagGatewayAdapter implements RagGateway {

  @Override
  public RagAnswer ask(Question question) {
    return new RagAnswer(
        "The orders service authenticates requests using JWT tokens passed through the Authorization header.",
        List.of(
            new Citation(
                "authentication.md",
                "All internal service requests must include a valid JWT token in the Authorization header."),
            new Citation(
                "orders-service.md",
                "The orders service validates the JWT before processing incoming requests.")));
  }
}