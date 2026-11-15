package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.producer.ProducerBuilder;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code ClientConfigurationBuilder.setMaxStartupAttempts} is honoured: a producer built
 * against an unreachable endpoint gives up after the configured number of topic-route fetches and
 * reports the attempt count in the exception chain.
 */
class MaxStartupAttemptsTest {

  private static final int MAX_STARTUP_ATTEMPTS = 3;

  @Test
  void buildFailsAfterConfiguredStartupAttempts() {
    ClientConfiguration configuration =
        ClientConfiguration.newBuilder()
            .setEndpoints("127.0.0.1:1") // nothing listens on this port
            .setMaxStartupAttempts(MAX_STARTUP_ATTEMPTS)
            .build();

    ClientServiceProvider provider = ClientServiceProvider.loadService();
    ProducerBuilder builder =
        provider
            .newProducerBuilder()
            .setClientConfiguration(configuration)
            .setTopics("normalTopic");

    Exception thrown = assertThrows(Exception.class, builder::build);
    String flattened = flatten(thrown);
    assertTrue(
        flattened.contains("Failed to fetch topics") && flattened.contains("3 attempts"),
        "Startup failure should report the configured attempt count, got: " + thrown);
  }

  private static String flatten(Throwable throwable) {
    StringBuilder text = new StringBuilder();
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      text.append(current.getClass().getName())
          .append(": ")
          .append(current.getMessage())
          .append(System.lineSeparator());
    }
    return text.toString();
  }
}
