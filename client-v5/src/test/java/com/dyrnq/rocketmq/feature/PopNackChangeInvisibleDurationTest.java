package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.MessageViewBytes;
import com.dyrnq.rocketmq.testsupport.Topics;
import com.dyrnq.rocketmq.testsupport.V5Clients;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies the V5 {@link SimpleConsumer} negative-ack idiom: {@code changeInvisibleDuration(view,
 * Duration.ZERO)} requeues a message so the same body becomes visible again.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PopNackChangeInvisibleDurationTest {

  private static final Logger log =
      LoggerFactory.getLogger(PopNackChangeInvisibleDurationTest.class);

  private final String uniqueTag = "v5NackTag" + UUID.randomUUID().toString().substring(0, 8);
  private Producer producer;
  private SimpleConsumer consumer;

  @BeforeAll
  void setUp() throws Exception {
    producer = V5Clients.producer(Topics.TOPIC_NORMAL);
    consumer = V5Clients.simpleConsumer(Groups.uniqueSimple(), Topics.TOPIC_NORMAL, uniqueTag);
    // Wait for the gRPC long-polling subscription to fully establish.
    Thread.sleep(2000);
  }

  @AfterAll
  void tearDown() throws Exception {
    if (consumer != null) {
      consumer.close();
    }
    if (producer != null) {
      producer.close();
    }
  }

  @Test
  void negativeAckRequeuesMessage() throws Exception {
    String bodyMarker = "v5-nack-" + UUID.randomUUID();
    org.apache.rocketmq.client.apis.message.Message message =
        ClientServiceProvider.loadService()
            .newMessageBuilder()
            .setTopic(Topics.TOPIC_NORMAL)
            .setTag(uniqueTag)
            .setBody(bodyMarker.getBytes(StandardCharsets.UTF_8))
            .build();
    SendReceipt receipt = producer.send(message);
    assertNotNull(receipt, "SendReceipt should not be null");

    MessageView first = receiveMatching(bodyMarker, Duration.ofSeconds(60));
    assertNotNull(first, "Should have received the message the first time");

    // RocketMQ 5 has no explicit nack API; setting the invisible duration to zero makes the
    // message immediately visible again (the negative-ack idiom).
    consumer.changeInvisibleDuration(first, Duration.ZERO);

    MessageView second = receiveMatching(bodyMarker, Duration.ofSeconds(60));
    assertNotNull(second, "Negative ack should requeue the message for a second delivery");

    consumer.ack(second);
  }

  private MessageView receiveMatching(String bodyMarker, Duration timeout) throws Exception {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      List<MessageView> messages = consumer.receive(16, Duration.ofSeconds(15));
      for (MessageView view : messages) {
        String body = MessageViewBytes.toString(view);
        log.info("Received body={}", body);
        if (body.equals(bodyMarker)) {
          return view;
        }
        consumer.ack(view);
      }
    }
    return null;
  }
}
