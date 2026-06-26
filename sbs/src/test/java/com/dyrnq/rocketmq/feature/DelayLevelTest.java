package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.sbs.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies a V4 fixed-delay message (delay level 2 = 5s) sent via a raw {@link DefaultMQProducer}
 * is delivered to a push consumer on {@link Topics#TOPIC_DELAY} at least 4s after send time.
 *
 * <p>Uses the raw V4 client APIs (instead of {@code RocketMQTemplate}) because the V4 {@code
 * Message.setDelayTimeLevel} is the canonical V4 fixed-delay API.
 *
 * <p>Each test method uses a fresh consumer and a unique body marker so the timing assertion is not
 * polluted by backlog from previous test runs (delayed messages whose timer has already expired get
 * delivered immediately on consumer subscribe).
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class DelayLevelTest {

  /** V4 delay level 2 = 5s. */
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
    bodyMarker = "sbs-delay-level-" + UUID.randomUUID();

    // ACL is required: the broker is configured to require it, and
    // delayed messages are only delayed for ACL-authed producers.
    AclClientRPCHook acl = new AclClientRPCHook(new SessionCredentials("ak", "sk"));
    producer = new DefaultMQProducer(Groups.uniqueProducer(), acl, true, null);
    producer.setNamesrvAddr(Addresses.NAMESERVER);
    producer.start();

    String group = Groups.uniquePush();
    consumer =
        new DefaultMQPushConsumer(group, acl, new AllocateMessageQueueAveragely(), true, null);
    consumer.setNamesrvAddr(Addresses.NAMESERVER);
    consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    consumer.subscribe(Topics.TOPIC_DELAY, "*");
    consumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
              for (MessageExt m : msgs) {
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
  void receivesDelayedMessageAfterDelayLevel() throws Exception {
    Message msg = new Message(Topics.TOPIC_DELAY, bodyMarker.getBytes(StandardCharsets.UTF_8));
    msg.setDelayTimeLevel(DELAY_LEVEL);

    long sentAt = System.currentTimeMillis();
    SendResult result = producer.send(msg);
    assertNotNull(result, "SendResult should not be null");
    assertTrue(
        result.getSendStatus() == SendStatus.SEND_OK,
        "SendStatus should be SEND_OK, was " + result.getSendStatus());

    long deadline = System.currentTimeMillis() + 30_000;
    while (matchingReceived.get() == 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }
    assertTrue(
        matchingReceived.get() >= 1, "Push consumer should have received body " + bodyMarker);

    long elapsed = firstDeliveryAt.get() - sentAt;
    assertTrue(
        elapsed >= EXPECTED_DELAY_MS - DELAY_TOLERANCE_MS,
        "Delivery should be at least ~5s after send, but was " + elapsed + "ms");
  }
}
