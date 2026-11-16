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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MessageSelector;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies SQL92 consumer-side filtering on a V4 push consumer.
 *
 * <p>Only messages whose {@code v4SqlFilterValue} user property falls within {@code [0, 500]} are
 * delivered. Requires the broker to run with {@code enablePropertyFilter=true}.
 */
class SqlFilterConsumeTest {

  private static final String FILTER_PROPERTY = "v4SqlFilterValue";

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private final AtomicInteger matchingReceived = new AtomicInteger();
  private final AtomicInteger nonMatchingReceived = new AtomicInteger();
  private String matchingMarker;
  private String nonMatchingMarker;

  @BeforeEach
  void setUp() throws Exception {
    matchingMarker = "sql-match-" + UUID.randomUUID();
    nonMatchingMarker = "sql-nomatch-" + UUID.randomUUID();
    producer = ClientCreater.createProducer(Groups.uniqueProducer());
    consumer = ClientCreater.createPushConsumer(Groups.uniquePush());
    consumer.subscribe(
        Topics.TOPIC_A, MessageSelector.bySql(FILTER_PROPERTY + " between 0 and 500"));
    consumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
              for (org.apache.rocketmq.common.message.MessageExt m : msgs) {
                if (isMarker(m, matchingMarker)) {
                  matchingReceived.incrementAndGet();
                } else if (isMarker(m, nonMatchingMarker)) {
                  nonMatchingReceived.incrementAndGet();
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
  void sqlFilterOnlyDeliversMatchingMessages() throws Exception {
    Message matching =
        new Message(
            Topics.TOPIC_A, "yourMessageTagA", matchingMarker.getBytes(StandardCharsets.UTF_8));
    matching.putUserProperty(FILTER_PROPERTY, "42");
    SendResult result = producer.send(matching, 3_000);
    assertNotNull(result);
    assertEquals(SendStatus.SEND_OK, result.getSendStatus());

    ReceiptAwaiter.waitingFor(matchingReceived, 1, Duration.ofSeconds(30))
        .poll("push consumer to receive SQL-matching message on " + Topics.TOPIC_A);

    Message nonMatching =
        new Message(
            Topics.TOPIC_A, "yourMessageTagA", nonMatchingMarker.getBytes(StandardCharsets.UTF_8));
    nonMatching.putUserProperty(FILTER_PROPERTY, "1000");
    result = producer.send(nonMatching, 3_000);
    assertNotNull(result);
    assertEquals(SendStatus.SEND_OK, result.getSendStatus());

    // Give the broker a short window to (wrongly) deliver the filtered-out message.
    TimeUnit.SECONDS.sleep(5);
    assertTrue(matchingReceived.get() >= 1, "Matching message should be delivered");
    assertEquals(0, nonMatchingReceived.get(), "Non-matching message should be filtered out");
  }

  @Test
  void booleanPropertyFilterDeliversOnlyMatchingMessages() throws Exception {
    String boolProperty = "v4SqlBoolean";
    String trueMarker = "sql-bool-true-" + UUID.randomUUID();
    String falseMarker = "sql-bool-false-" + UUID.randomUUID();
    AtomicInteger trueReceived = new AtomicInteger();
    AtomicInteger falseReceived = new AtomicInteger();

    DefaultMQPushConsumer boolConsumer = ClientCreater.createPushConsumer(Groups.uniquePush());
    boolConsumer.subscribe(Topics.TOPIC_A, MessageSelector.bySql(boolProperty + " = true"));
    boolConsumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
              for (org.apache.rocketmq.common.message.MessageExt m : msgs) {
                if (isMarker(m, trueMarker)) {
                  trueReceived.incrementAndGet();
                } else if (isMarker(m, falseMarker)) {
                  falseReceived.incrementAndGet();
                }
              }
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
    boolConsumer.start();

    Message trueMsg =
        new Message(Topics.TOPIC_A, "yourMessageTagA", trueMarker.getBytes(StandardCharsets.UTF_8));
    trueMsg.putUserProperty(boolProperty, "true");
    producer.send(trueMsg, 3_000);

    Message falseMsg =
        new Message(
            Topics.TOPIC_A, "yourMessageTagA", falseMarker.getBytes(StandardCharsets.UTF_8));
    falseMsg.putUserProperty(boolProperty, "false");
    producer.send(falseMsg, 3_000);

    ReceiptAwaiter.waitingFor(trueReceived, 1, Duration.ofSeconds(30))
        .poll("push consumer to receive boolean 'true' property message");

    TimeUnit.SECONDS.sleep(5);
    boolConsumer.shutdown();

    assertTrue(trueReceived.get() >= 1, "Boolean 'true' property message should be delivered");
    assertEquals(0, falseReceived.get(), "Boolean 'false' property message should be filtered out");
  }

  private static boolean isMarker(
      org.apache.rocketmq.common.message.MessageExt message, String marker) {
    return marker != null && new String(message.getBody(), StandardCharsets.UTF_8).contains(marker);
  }
}
