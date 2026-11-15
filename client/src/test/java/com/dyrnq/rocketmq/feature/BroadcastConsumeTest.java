package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.ClientCreater;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.ReceiptAwaiter;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a broadcast-mode push consumer receives a message sent to {@link Topics#TOPIC_A}.
 *
 * <p>In broadcast mode the consumer registers on every queue of the topic, so a single consumer
 * instance still observes the producer's message without relying on queue rebalance.
 */
class BroadcastConsumeTest {

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private final AtomicInteger received = new AtomicInteger();
  private String bodyMarker;

  @BeforeEach
  void setUp() throws Exception {
    bodyMarker = "marker-" + UUID.randomUUID();
    producer = ClientCreater.createProducer(Groups.uniqueProducer());
    consumer = ClientCreater.createPushConsumer(Groups.uniquePush());
    consumer.setMessageModel(MessageModel.BROADCASTING);
    consumer.subscribe(Topics.TOPIC_A, "*");
    consumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
              for (org.apache.rocketmq.common.message.MessageExt m : msgs) {
                if (new String(m.getBody(), StandardCharsets.UTF_8).contains(bodyMarker)) {
                  received.incrementAndGet();
                }
              }
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
    consumer.start();
  }

  @AfterEach
  void tearDown() {
    if (consumer != null) {
      consumer.shutdown();
    }
    if (producer != null) {
      producer.shutdown();
    }
  }

  @Test
  void broadcastConsumerReceivesSentMessage() throws Exception {
    Message msg =
        new Message(Topics.TOPIC_A, "yourMessageTagA", bodyMarker.getBytes(StandardCharsets.UTF_8));
    SendResult result = producer.send(msg, 3_000);
    assertNotNull(result);
    assertEquals(SendStatus.SEND_OK, result.getSendStatus());

    ReceiptAwaiter.waitingFor(received, 1, Duration.ofSeconds(30))
        .poll("broadcast consumer to receive 1 message with marker on " + Topics.TOPIC_A);
    assertTrue(received.get() >= 1, "Should have received at least one matching message");
  }
}
