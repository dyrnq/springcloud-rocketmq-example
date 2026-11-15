package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.ClientCreater;
import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies queue-based consumer load balancing. Two push consumers in the same group use the
 * default {@link AllocateMessageQueueAveragely} strategy, so each takes a subset of the topic
 * queues; a burst of messages is then split between them.
 */
class ConsumerLoadBalanceTest {

  private static final int MESSAGE_COUNT = 40;

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer consumerA;
  private DefaultMQPushConsumer consumerB;
  private final AtomicInteger receivedA = new AtomicInteger();
  private final AtomicInteger receivedB = new AtomicInteger();
  private String marker;

  @BeforeEach
  void setUp() throws Exception {
    marker = "lb-marker-" + UUID.randomUUID() + "-";
    String group = Groups.uniquePush();
    AclClientRPCHook acl = new AclClientRPCHook(new SessionCredentials("ak", "sk"));

    producer = ClientCreater.createProducer(Groups.uniqueProducer());

    consumerA =
        new DefaultMQPushConsumer(group, acl, new AllocateMessageQueueAveragely(), true, null);
    consumerA.setNamesrvAddr(Addresses.NAMESERVER);
    consumerA.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    consumerA.subscribe(Topics.TOPIC_NORMAL, "*");
    consumerA.registerMessageListener(listener(receivedA));
    consumerA.start();

    consumerB =
        new DefaultMQPushConsumer(group, acl, new AllocateMessageQueueAveragely(), true, null);
    consumerB.setNamesrvAddr(Addresses.NAMESERVER);
    consumerB.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    consumerB.subscribe(Topics.TOPIC_NORMAL, "*");
    consumerB.registerMessageListener(listener(receivedB));
    consumerB.start();

    // Give both consumers time to register and complete one rebalance round.
    Thread.sleep(5000);
  }

  private MessageListenerConcurrently listener(AtomicInteger counter) {
    return (msgs, context) -> {
      for (MessageExt m : msgs) {
        String body = new String(m.getBody(), StandardCharsets.UTF_8);
        if (body.startsWith(marker)) {
          counter.incrementAndGet();
        }
      }
      return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    };
  }

  @AfterEach
  void tearDown() {
    if (consumerA != null) {
      consumerA.shutdown();
    }
    if (consumerB != null) {
      consumerB.shutdown();
    }
    if (producer != null) {
      producer.shutdown();
    }
  }

  @Test
  void twoConsumersShareQueues() throws Exception {
    for (int i = 0; i < MESSAGE_COUNT; i++) {
      producer.send(
          new Message(Topics.TOPIC_NORMAL, (marker + i).getBytes(StandardCharsets.UTF_8)));
    }

    long deadline = System.currentTimeMillis() + 30_000;
    while (receivedA.get() + receivedB.get() < MESSAGE_COUNT
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }

    assertEquals(
        MESSAGE_COUNT,
        receivedA.get() + receivedB.get(),
        "both consumers together should receive all " + MESSAGE_COUNT + " messages");

    int queueCount = producer.fetchPublishMessageQueues(Topics.TOPIC_NORMAL).size();
    if (queueCount >= 2) {
      assertTrue(
          receivedA.get() >= 1,
          "Consumer A should receive at least 1 message, got " + receivedA.get());
      assertTrue(
          receivedB.get() >= 1,
          "Consumer B should receive at least 1 message, got " + receivedB.get());
    }
  }
}
