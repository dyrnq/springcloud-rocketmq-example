package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.ClientCreater;
import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies consumer progress is queryable via admin: after one message is consumed, the group's
 * consumer offset advances beyond zero for at least one queue.
 */
class ConsumerProgressTest {

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private DefaultMQAdminExt admin;
  private String group;
  private String bodyMarker;
  private final AtomicBoolean consumed = new AtomicBoolean(false);

  @BeforeEach
  void setUp() throws Exception {
    bodyMarker = "progress-marker-" + UUID.randomUUID();
    group = Groups.uniquePush();
    producer = ClientCreater.createProducer(Groups.uniqueProducer());
    consumer = ClientCreater.createPushConsumer(group);
    consumer.subscribe(Topics.TOPIC_NORMAL, "*");
    consumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
              for (MessageExt m : msgs) {
                if (bodyMarker.equals(new String(m.getBody(), StandardCharsets.UTF_8))) {
                  consumed.set(true);
                }
              }
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
    consumer.start();

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
  void consumerOffsetAdvancesAfterConsume() throws Exception {
    producer.send(new Message(Topics.TOPIC_NORMAL, bodyMarker.getBytes(StandardCharsets.UTF_8)));

    long deadline = System.currentTimeMillis() + 30_000;
    while (!consumed.get() && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }
    assertTrue(consumed.get(), "Consumer should have consumed body " + bodyMarker);

    ConsumeStats stats = admin.examineConsumeStats(group);
    assertNotNull(stats, "ConsumeStats should not be null");
    Map<MessageQueue, OffsetWrapper> offsets = stats.getOffsetTable();
    assertNotNull(offsets, "offset table should not be null");
    assertTrue(!offsets.isEmpty(), "offset table should contain at least one queue");

    long advancedQueues = offsets.values().stream().filter(o -> o.getConsumerOffset() > 0).count();
    assertTrue(advancedQueues >= 1, "at least one queue should have consumerOffset > 0");
  }
}
