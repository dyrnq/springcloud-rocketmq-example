package com.dyrnq.sca.rocketmq;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.ReceiptAwaiter;
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
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies broker-driven consumption retry for spring-cloud-v2025-1: the binder producer sends via
 * {@code StreamBridge}, and a push consumer returns {@code RECONSUME_LATER} twice before {@code
 * SUCCESS}; {@link MessageExt#getReconsumeTimes()} increments per redelivery.
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReconsumeTest {

  private static final String OUT_BINDING = "consumerEvent-out-0";
  private static final String TOPIC = "demo";
  private static final int RETRIES_BEFORE_SUCCESS = 2;

  @Autowired StreamBridge streamBridge;

  private DefaultMQPushConsumer consumer;
  private final AtomicInteger matchingReceived = new AtomicInteger();
  private final AtomicInteger maxReconsumeTimes = new AtomicInteger(0);
  private String bodyMarker;

  @BeforeAll
  void setUp() throws Exception {
    bodyMarker = "sca-reconsume-" + UUID.randomUUID();
    AclClientRPCHook acl = new AclClientRPCHook(new SessionCredentials("ak", "sk"));
    consumer =
        new DefaultMQPushConsumer(
            Groups.uniquePush(), acl, new AllocateMessageQueueAveragely(), true, null);
    consumer.setNamesrvAddr(Addresses.NAMESERVER);
    consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    consumer.subscribe(TOPIC, "*");
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
    // Let the consumer register its starting offset before producing.
    Thread.sleep(2000);
  }

  @AfterAll
  void tearDown() {
    if (consumer != null) {
      consumer.shutdown();
    }
  }

  @Test
  void brokerRedeliversUntilListenerSucceeds() throws Exception {
    boolean sent = streamBridge.send(OUT_BINDING, MessageBuilder.withPayload(bodyMarker).build());
    assertTrue(sent, "send should succeed");

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
