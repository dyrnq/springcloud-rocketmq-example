package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dyrnq.rocketmq.sbs.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
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
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the sbs (V4 starter) orderly send path: {@code RocketMQTemplate.syncSendOrderly} sends
 * every message with the same hash key, and an orderly push consumer receives them in send order.
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class OrderlyConsumeTest {

  private static final int MESSAGE_COUNT = 10;
  private static final String HASH_KEY = "sbs-orderly-fixed-key";

  @Autowired RocketMQTemplate rocketMQTemplate;

  private DefaultMQPushConsumer consumer;
  private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
  private String marker;

  @BeforeEach
  void setUp() throws Exception {
    marker = "sbs-orderly-" + UUID.randomUUID() + "-";
    String group = Groups.uniquePush();
    AclClientRPCHook acl = new AclClientRPCHook(new SessionCredentials("ak", "sk"));
    consumer =
        new DefaultMQPushConsumer(group, acl, new AllocateMessageQueueAveragely(), true, null);
    consumer.setNamesrvAddr(Addresses.NAMESERVER);
    consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    consumer.subscribe(Topics.TOPIC_NORMAL, "*");
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
  }

  @AfterEach
  void tearDown() {
    if (consumer != null) {
      consumer.shutdown();
    }
  }

  @Test
  void orderlySendIsConsumedInOrder() throws Exception {
    for (int i = 0; i < MESSAGE_COUNT; i++) {
      SendResult result =
          rocketMQTemplate.syncSendOrderly(Topics.TOPIC_NORMAL, marker + i, HASH_KEY);
      assertNotNull(result, "SendResult should not be null");
      assertEquals(SendStatus.SEND_OK, result.getSendStatus(), "SendStatus should be SEND_OK");
    }

    long deadline = System.currentTimeMillis() + 30_000;
    while (receivedBodies.size() < MESSAGE_COUNT && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }

    assertEquals(
        MESSAGE_COUNT,
        receivedBodies.size(),
        "orderly consumer should receive all " + MESSAGE_COUNT + " messages");
    for (int i = 0; i < MESSAGE_COUNT; i++) {
      assertEquals(
          marker + i, receivedBodies.get(i), "message at index " + i + " should keep send order");
    }
  }
}
