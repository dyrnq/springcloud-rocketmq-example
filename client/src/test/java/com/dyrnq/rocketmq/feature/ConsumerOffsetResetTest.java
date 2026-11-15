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
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies consumer progress can be reset: after a message is consumed, resetting its queue offset
 * back to the message offset causes the broker to redeliver the same message to a consumer in the
 * same group.
 */
class ConsumerOffsetResetTest {

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private DefaultMQAdminExt admin;
  private String group;
  private String bodyMarker;
  private final AtomicInteger received = new AtomicInteger();

  @BeforeEach
  void setUp() throws Exception {
    group = Groups.uniquePush();
    bodyMarker = "reset-marker-" + UUID.randomUUID();
    producer = ClientCreater.createProducer(Groups.uniqueProducer());

    admin = new DefaultMQAdminExt();
    admin.setNamesrvAddr(Addresses.NAMESERVER);
    admin.start();
  }

  @AfterEach
  void tearDown() {
    if (consumer != null) {
      consumer.shutdown();
    }
    if (producer != null) {
      producer.shutdown();
    }
    if (admin != null) {
      admin.shutdown();
    }
  }

  @Test
  void resetOffsetRedeliversConsumedMessage() throws Exception {
    SendResult result =
        producer.send(
            new Message(Topics.TOPIC_NORMAL, bodyMarker.getBytes(StandardCharsets.UTF_8)));
    MessageQueue queue = result.getMessageQueue();
    long messageOffset = result.getQueueOffset();

    consumer = startConsumer();
    awaitDelivery(1);

    consumer.shutdown();
    consumer = null;

    admin.resetOffsetByQueueId(
        Addresses.BROKER_ADDR_1, group, Topics.TOPIC_NORMAL, queue.getQueueId(), messageOffset);

    received.set(0);
    consumer = startConsumer();
    awaitDelivery(1);

    assertEquals(1, received.get(), "same message should be redelivered once after offset reset");
  }

  private DefaultMQPushConsumer startConsumer() throws Exception {
    DefaultMQPushConsumer c = ClientCreater.createPushConsumer(group);
    c.subscribe(Topics.TOPIC_NORMAL, "*");
    c.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, ctx) -> {
              for (MessageExt m : msgs) {
                if (bodyMarker.equals(new String(m.getBody(), StandardCharsets.UTF_8))) {
                  received.incrementAndGet();
                }
              }
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
    c.start();
    return c;
  }

  private void awaitDelivery(int expected) throws Exception {
    long deadline = System.currentTimeMillis() + 30_000;
    while (received.get() < expected && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }
    assertTrue(received.get() >= expected, "expected at least " + expected + " delivery");
  }
}
