package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Verifies synchronous message send via the V5 gRPC client.
 *
 * <p>Replaces the body of {@code
 * org.apache.rocketmq.client.java.example.ProducerNormalMessageExample.main}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncSendTest {

  private Producer producer;

  @BeforeAll
  void startProducer() throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    // V5 producer for a normal message: same builder as ProducerSingleton but inlined
    // so this test doesn't depend on the demo singleton (which would be reused across tests).
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
  void sendNormalMessage() throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    String bodyStr = "v5-sync-send-" + java.util.UUID.randomUUID();
    byte[] body = bodyStr.getBytes(StandardCharsets.UTF_8);
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(Topics.TOPIC_NORMAL)
            .setTag("yourMessageTagA")
            .setKeys("v5-sync-key-" + java.util.UUID.randomUUID())
            .setBody(body)
            .build();
    SendReceipt receipt = producer.send(message);
    assertNotNull(receipt, "SendReceipt should not be null");
    assertNotNull(receipt.getMessageId(), "MessageId should not be null");
  }
}
