package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the dead-letter-queue path: a clustering consumer that exhausts its retries has the
 * failed message moved to {@code %DLQ%<group>}.
 *
 * <p>{@code setMaxReconsumeTimes(0)} makes the first {@code RECONSUME_LATER} go straight to the DLQ
 * topic (the broker routes to DLQ when {@code reconsumeTimes >= maxReconsumeTimes}), keeping the
 * test fast and deterministic.
 */
class DeadLetterTest {

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer failingConsumer;
  private DefaultMQPushConsumer dlqConsumer;
  private String failGroup;
  private String bodyMarker;
  private final AtomicInteger failedDeliveryCount = new AtomicInteger();
  private final AtomicInteger dlqReceived = new AtomicInteger();

  @BeforeEach
  void setUp() throws Exception {
    bodyMarker = "dlq-marker-" + UUID.randomUUID();
    failGroup = Groups.uniquePush();
    producer = ClientCreater.createProducer(Groups.uniqueProducer());

    failingConsumer = ClientCreater.createPushConsumer(failGroup);
    failingConsumer.setMaxReconsumeTimes(0);
    failingConsumer.subscribe(Topics.TOPIC_NORMAL, "*");
    failingConsumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, ctx) -> {
              boolean containsMarker = false;
              for (MessageExt m : msgs) {
                if (bodyMarker.equals(new String(m.getBody(), StandardCharsets.UTF_8))) {
                  containsMarker = true;
                  failedDeliveryCount.incrementAndGet();
                }
              }
              return containsMarker
                  ? ConsumeConcurrentlyStatus.RECONSUME_LATER
                  : ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
    failingConsumer.start();

    // Subscribe before the %DLQ% topic exists so the consumer is already polling by the time the
    // broker creates it; a short poll interval keeps route discovery fast.
    dlqConsumer = ClientCreater.createPushConsumer(Groups.uniquePush());
    dlqConsumer.setPollNameServerInterval(3_000);
    dlqConsumer.subscribe(MixAll.getDLQTopic(failGroup), "*");
    dlqConsumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, ctx) -> {
              for (MessageExt m : msgs) {
                if (bodyMarker.equals(new String(m.getBody(), StandardCharsets.UTF_8))) {
                  dlqReceived.incrementAndGet();
                }
              }
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
    dlqConsumer.start();
  }

  @AfterEach
  void tearDown() {
    if (dlqConsumer != null) dlqConsumer.shutdown();
    if (failingConsumer != null) failingConsumer.shutdown();
    if (producer != null) producer.shutdown();
  }

  @Test
  void failedMessageReachesDeadLetterQueue() throws Exception {
    SendResult result =
        producer.send(
            new Message(Topics.TOPIC_NORMAL, bodyMarker.getBytes(StandardCharsets.UTF_8)));
    assertNotNull(result);

    ReceiptAwaiter.waitingFor(failedDeliveryCount, 1, Duration.ofSeconds(30))
        .poll("failing consumer to consume the marker message");

    ReceiptAwaiter.waitingFor(dlqReceived, 1, Duration.ofSeconds(60))
        .poll("DLQ consumer to receive the marker message from %DLQ% topic");

    assertEquals(1, dlqReceived.get(), "failed message should arrive exactly once in the DLQ");
  }
}
