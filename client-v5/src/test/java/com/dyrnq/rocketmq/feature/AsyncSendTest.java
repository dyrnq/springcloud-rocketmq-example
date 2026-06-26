package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Verifies asynchronous message send via the V5 gRPC client and asserts the send callback completes
 * with a {@link SendReceipt}.
 *
 * <p>Replaces the body of {@code
 * org.apache.rocketmq.client.java.example.AsyncProducerExample.main}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsyncSendTest {

  private Producer producer;

  @BeforeAll
  void startProducer() throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    org.apache.rocketmq.client.apis.ClientConfiguration clientConfiguration =
        org.apache.rocketmq.client.apis.ClientConfiguration.newBuilder()
            .setEndpoints(com.dyrnq.rocketmq.testsupport.Addresses.PROXY)
            .build();
    producer =
        provider
            .newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics(Topics.TOPIC_NORMAL)
            .build();
  }

  @AfterAll
  void stopProducer() throws Exception {
    if (producer != null) {
      producer.close();
    }
  }

  @Test
  void sendAsyncNormalMessage() throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    byte[] body = "v5-async-send-".getBytes(StandardCharsets.UTF_8);
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(Topics.TOPIC_NORMAL)
            .setTag("yourMessageTagA")
            .setKeys("v5-async-key-" + java.util.UUID.randomUUID())
            .setBody(body)
            .build();

    CompletableFuture<SendReceipt> future = producer.sendAsync(message);
    SendReceipt receipt = future.get(10, TimeUnit.SECONDS);
    assertNotNull(receipt, "Async send should resolve with a SendReceipt");
    assertNotNull(receipt.getMessageId(), "MessageId should not be null");

    // Sanity: the future's throwable path stays null on success.
    assertNull(
        future.isCompletedExceptionally() ? new RuntimeException("x") : null,
        "Future should not be completed exceptionally");
  }
}
