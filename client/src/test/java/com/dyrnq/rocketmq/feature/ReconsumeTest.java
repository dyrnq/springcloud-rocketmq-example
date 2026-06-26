package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.ClientCreater;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies broker-driven retry: a listener that returns {@code RECONSUME_LATER} causes the broker
 * to redeliver the message; {@link MessageExt#getReconsumeTimes()} increments per redelivery.
 *
 * <p>Strategy: on the first two deliveries of a unique body marker return {@code RECONSUME_LATER};
 * on the third delivery return {@code SUCCESS}. Assert {@code reconsumeTimes} reaches at least 2
 * before the success.
 */
class ReconsumeTest {

  /** Total deliveries we observe before returning SUCCESS. */
  private static final int RETRIES_BEFORE_SUCCESS = 2;

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private final AtomicInteger matchingReceived = new AtomicInteger();
  private final AtomicInteger maxReconsumeTimes = new AtomicInteger(0);
  private String bodyMarker;

  @BeforeEach
  void setUp() throws Exception {
    bodyMarker = "reconsume-marker-" + java.util.UUID.randomUUID();
    producer = ClientCreater.createProducer(Groups.uniqueProducer());
    consumer = ClientCreater.createPushConsumer(Groups.uniquePush());
    consumer.subscribe(Topics.TOPIC_NORMAL, "*");
    consumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
              for (MessageExt m : msgs) {
                String body = new String(m.getBody(), StandardCharsets.UTF_8);
                if (body.equals(bodyMarker)) {
                  int observed = m.getReconsumeTimes();
                  if (observed > maxReconsumeTimes.get()) {
                    maxReconsumeTimes.set(observed);
                  }
                  matchingReceived.incrementAndGet();
                  if (observed < RETRIES_BEFORE_SUCCESS) {
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                  }
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
  void brokerRedeliversUntilListenerSucceeds() throws Exception {
    Message msg = new Message(Topics.TOPIC_NORMAL, bodyMarker.getBytes(StandardCharsets.UTF_8));
    msg.setTags("reconsume");
    SendResult result = producer.send(msg);
    assertNotNull(result);

    // Wait for RETRIES_BEFORE_SUCCESS + 1 deliveries (the two RETRY_LATER + the final SUCCESS).
    com.dyrnq.rocketmq.testsupport.ReceiptAwaiter.waitingFor(
            matchingReceived, RETRIES_BEFORE_SUCCESS + 1, Duration.ofSeconds(60))
        .poll("push consumer to receive " + (RETRIES_BEFORE_SUCCESS + 1) + " deliveries");

    assertTrue(
        maxReconsumeTimes.get() >= RETRIES_BEFORE_SUCCESS,
        "Expected reconsumeTimes to reach at least "
            + RETRIES_BEFORE_SUCCESS
            + " (saw "
            + maxReconsumeTimes.get()
            + ")");
  }
}
