package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Topics;
import com.dyrnq.rocketmq.testsupport.V5Clients;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code ClientConfigurationBuilder.setRequestTimeout} is applied to the client
 * configuration and a normal send still succeeds. The deadline behaviour itself cannot be
 * asserted deterministically against a fast local broker, so this test avoids a timing-based
 * failure.
 */
class RequestTimeoutTest {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  @Test
  void requestTimeoutIsAppliedAndAllowsNormalSend() throws Exception {
    ClientConfiguration configuration =
        ClientConfiguration.newBuilder()
            .setEndpoints(Addresses.PROXY)
            .setRequestTimeout(REQUEST_TIMEOUT)
            .build();
    assertEquals(REQUEST_TIMEOUT, configuration.getRequestTimeout());

    try (Producer producer = V5Clients.producer(Topics.TOPIC_NORMAL, configuration)) {
      ClientServiceProvider provider = ClientServiceProvider.loadService();
      Message message =
          provider
              .newMessageBuilder()
              .setTopic(Topics.TOPIC_NORMAL)
              .setKeys("request-timeout-" + UUID.randomUUID())
              .setBody("ping".getBytes(StandardCharsets.UTF_8))
              .build();

      SendReceipt receipt = producer.send(message);
      assertNotNull(receipt.getMessageId());
    }
  }
}
