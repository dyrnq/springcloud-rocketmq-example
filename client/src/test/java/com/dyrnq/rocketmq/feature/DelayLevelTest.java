package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.ClientCreater;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.ReceiptAwaiter;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies fixed-level delayed messages. Send a message with {@code setDelayTimeLevel(2)} (5s) to
 * {@link Topics#TOPIC_DELAY} and assert that it is delivered at least 4s after send time.
 *
 * <p>Replaces the body of {@code com.dyrnq.rocketmq.delay.DelayMessageProducer.main} and {@code
 * com.dyrnq.rocketmq.delay.ScheduledMessageConsumer.main}.
 */
class DelayLevelTest {

  /** Use level 2 = 5s. The test asserts the message arrives in the [4s, 30s] window. */
  private static final int DELAY_LEVEL = 2;

  private static final long EXPECTED_DELAY_MS = 5_000;
  private static final long DELAY_TOLERANCE_MS = 1_000;

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private final AtomicInteger matchingReceived = new AtomicInteger();
  private final AtomicLong firstDeliveryAt = new AtomicLong(0);
  private String bodyMarker;

  @BeforeEach
  void setUp() throws Exception {
    bodyMarker = "delayed-marker-" + java.util.UUID.randomUUID();
    producer = ClientCreater.createProducer(Groups.uniqueProducer());
    consumer = ClientCreater.createPushConsumer(Groups.uniquePush());
    consumer.subscribe(Topics.TOPIC_DELAY, "*");
    consumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
              for (org.apache.rocketmq.common.message.MessageExt m : msgs) {
                String body = new String(m.getBody(), StandardCharsets.UTF_8);
                if (body.equals(bodyMarker)) {
                  firstDeliveryAt.compareAndSet(0, System.currentTimeMillis());
                  matchingReceived.incrementAndGet();
                }
              }
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
    consumer.start();
  }

  @AfterEach
  void tearDown() {
    if (consumer != null) consumer.shutdown();
    if (producer != null) producer.shutdown();
  }

  @Test
  void sendsAndReceivesLevelDelayedMessage() throws Exception {
    Message msg = new Message(Topics.TOPIC_DELAY, bodyMarker.getBytes(StandardCharsets.UTF_8));
    msg.setTags("DELAY=" + DELAY_LEVEL);
    msg.setDelayTimeLevel(DELAY_LEVEL);

    long sentAt = System.currentTimeMillis();
    SendResult result = producer.send(msg);
    assertNotNull(result);

    ReceiptAwaiter.waitingFor(matchingReceived, 1, Duration.ofSeconds(30))
        .poll("push consumer to receive 1 level-" + DELAY_LEVEL + " delayed message");

    long elapsed = firstDeliveryAt.get() - sentAt;
    assertTrue(
        elapsed >= EXPECTED_DELAY_MS - DELAY_TOLERANCE_MS,
        "Delivery should be at least ~5s after send, but was " + elapsed + "ms");
  }
}
