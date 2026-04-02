package com.ivan.documind.documind_backend.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

@Configuration
public class BedrockConfig {

  @Value("${aws.region}")
  private String region;

  @Bean
  public BedrockAgentRuntimeClient bedrockClient() {
    return BedrockAgentRuntimeClient.builder()
        .region(Region.of(region))
        .build();
  }
}
