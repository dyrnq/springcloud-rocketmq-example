package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.sbs.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.ReceiptAwaiter;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies broker-driven consumption retry for the sbs module: a listener returns {@code
 * RECONSUME_LATER} twice, then {@code SUCCESS}; {@link MessageExt#getReconsumeTimes()} increments
 * per redelivery.
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class ReconsumeTest {

  private static final int RETRIES_BEFORE_SUCCESS = 2;

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private final AtomicInteger matchingReceived = new AtomicInteger();
  private final AtomicInteger maxReconsumeTimes = new AtomicInteger(0);
  private String bodyMarker;

  @BeforeEach
  void setUp() throws Exception {
    bodyMarker = "sbs-reconsume-" + UUID.randomUUID();
    AclClientRPCHook acl = new AclClientRPCHook(new SessionCredentials("ak", "sk"));

    producer = new DefaultMQProducer(Groups.uniqueProducer(), acl, true, null);
    producer.setNamesrvAddr(Addresses.NAMESERVER);
    producer.start();

    consumer =
        new DefaultMQPushConsumer(
            Groups.uniquePush(), acl, new AllocateMessageQueueAveragely(), true, null);
    consumer.setNamesrvAddr(Addresses.NAMESERVER);
    consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
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
    if (consumer != null) {
      consumer.shutdown();
    }
    if (producer != null) {
      producer.shutdown();
    }
  }

  @Test
  void brokerRedeliversUntilListenerSucceeds() throws Exception {
    Message msg = new Message(Topics.TOPIC_NORMAL, bodyMarker.getBytes(StandardCharsets.UTF_8));
    msg.setTags("reconsume");
    SendResult result = producer.send(msg);
    assertNotNull(result, "SendResult should not be null");

    ReceiptAwaiter.waitingFor(matchingReceived, RETRIES_BEFORE_SUCCESS + 1, Duration.ofSeconds(150))
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
