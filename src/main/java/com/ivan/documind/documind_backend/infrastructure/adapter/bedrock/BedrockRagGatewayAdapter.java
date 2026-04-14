package com.ivan.documind.documind_backend.infrastructure.adapter.bedrock;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.ivan.documind.documind_backend.application.port.RagGateway;
import com.ivan.documind.documind_backend.domain.model.Citation;
import com.ivan.documind.documind_backend.domain.model.Question;
import com.ivan.documind.documind_backend.domain.model.RagAnswer;
import com.ivan.documind.documind_backend.shared.exception.QuestionProcessingException;

import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockAgentRuntimeException;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateType;

@Component
@Profile("prod")
public class BedrockRagGatewayAdapter implements RagGateway {

  private final BedrockAgentRuntimeClient client;
  private final String knowledgeBaseId;
  private final String modelArn;

  public BedrockRagGatewayAdapter(
      BedrockAgentRuntimeClient client,
      @Value("${bedrock.knowledge-base-id}") String knowledgeBaseId,
      @Value("${bedrock.model-arn}") String modelArn) {
    this.client = client;
    this.knowledgeBaseId = knowledgeBaseId;
    this.modelArn = modelArn;
  }

  @Override
  public RagAnswer ask(Question question) {
    RetrieveAndGenerateRequest request = RetrieveAndGenerateRequest.builder()
        .input((i) -> i.text(question.value()))
        .retrieveAndGenerateConfiguration(
            cfg -> cfg.type(RetrieveAndGenerateType.KNOWLEDGE_BASE)
                .knowledgeBaseConfiguration((kb) -> kb.knowledgeBaseId(knowledgeBaseId).modelArn(modelArn)))
        .build();

    RetrieveAndGenerateResponse response;
    try {
      response = client.retrieveAndGenerate(request);
    } catch (BedrockAgentRuntimeException exception) {
      throw mapBedrockException(exception);
    }

    String answer = response.output().text();
    List<Citation> citations = response.citations()
        .stream()
        .map(c -> new Citation(
            c.retrievedReferences().get(0).location().s3Location().uri(),
            c.retrievedReferences().get(0).content().text()))
        .toList();

    return new RagAnswer(answer, citations);
  }

  private QuestionProcessingException mapBedrockException(BedrockAgentRuntimeException exception) {
    String message = exception.getMessage();

    if (message != null && message.contains("The model arn provided is not supported")) {
      return new QuestionProcessingException(
          "BEDROCK_MODEL_ARN no es valido para Knowledge Bases RetrieveAndGenerate. "
              + "Usa el ARN de un foundation model o inference profile soportado en la misma region que AWS_REGION.");
    }

    return new QuestionProcessingException("Bedrock error: " + message);
  }

}
