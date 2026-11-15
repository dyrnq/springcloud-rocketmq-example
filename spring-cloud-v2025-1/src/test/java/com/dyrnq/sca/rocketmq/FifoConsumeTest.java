package com.dyrnq.sca.rocketmq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageConst;
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
 * Verifies the Spring Cloud Stream binder FIFO path for spring-cloud-v2025-1: the output binding
 * uses {@code fifoMessageQueueSelector} so every message with the same KEYS header lands on one
 * queue, and an orderly consumer observes them in send order.
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FifoConsumeTest {

  private static final String OUT_BINDING = "consumerEvent-out-0";
  private static final String TOPIC = "demo";
  private static final int MESSAGE_COUNT = 10;
  private static final String FIXED_KEY = "sca-fifo-fixed-key";

  @Autowired StreamBridge streamBridge;

  private DefaultMQPushConsumer consumer;
  private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
  private String marker;

  @BeforeAll
  void setUp() throws Exception {
    marker = "sca-fifo-" + UUID.randomUUID() + "-";
    AclClientRPCHook acl = new AclClientRPCHook(new SessionCredentials("ak", "sk"));
    consumer =
        new DefaultMQPushConsumer(
            Groups.uniquePush(), acl, new AllocateMessageQueueAveragely(), true, null);
    consumer.setNamesrvAddr(Addresses.NAMESERVER);
    consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
    consumer.subscribe(TOPIC, "*");
    consumer.registerMessageListener(
        (MessageListenerOrderly)
            (List<MessageExt> msgs, ConsumeOrderlyContext context) -> {
              for (MessageExt m : msgs) {
                String body = new String(m.getBody(), StandardCharsets.UTF_8);
                if (body.startsWith(marker)) {
                  receivedBodies.add(body);
                }
              }
              return ConsumeOrderlyStatus.SUCCESS;
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
  void fifoMessagesAreConsumedInOrder() throws Exception {
    for (int i = 0; i < MESSAGE_COUNT; i++) {
      boolean sent =
          streamBridge.send(
              OUT_BINDING,
              MessageBuilder.withPayload(marker + i)
                  .setHeader(MessageConst.PROPERTY_KEYS, FIXED_KEY)
                  .build());
      assertTrue(sent, "send should succeed for message " + i);
    }

    long deadline = System.currentTimeMillis() + 30_000;
    while (receivedBodies.size() < MESSAGE_COUNT && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }

    assertEquals(
        MESSAGE_COUNT, receivedBodies.size(), "should receive all " + MESSAGE_COUNT + " messages");
    for (int i = 0; i < MESSAGE_COUNT; i++) {
      assertEquals(
          marker + i, receivedBodies.get(i), "message at index " + i + " should keep send order");
    }
  }
}
