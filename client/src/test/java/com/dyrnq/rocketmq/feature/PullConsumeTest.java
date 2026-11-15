package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.ClientCreater;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies V4 pull consumption via {@link DefaultLitePullConsumer}. Sends a unique message and then
 * polls for it, committing the offset once received.
 */
class PullConsumeTest {

  private DefaultMQProducer producer;
  private DefaultLitePullConsumer consumer;
  private String bodyMarker;

  @BeforeEach
  void setUp() throws Exception {
    bodyMarker = "pull-marker-" + UUID.randomUUID();
    producer = ClientCreater.createProducer(Groups.uniqueProducer());
    consumer = ClientCreater.createPullConsumer(Groups.uniquePush());
    consumer.subscribe(Topics.TOPIC_NORMAL, "*");
    consumer.start();
    // Let the consumer register its starting offset before the message is produced.
    Thread.sleep(2000);
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
  void pullsSentMessage() throws Exception {
    Message message = new Message(Topics.TOPIC_NORMAL, bodyMarker.getBytes(StandardCharsets.UTF_8));
    assertNotNull(producer.send(message), "SendResult should not be null");

    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      List<MessageExt> messages = consumer.poll(1000);
      for (MessageExt m : messages) {
        if (bodyMarker.equals(new String(m.getBody(), StandardCharsets.UTF_8))) {
          consumer.commitSync();
          assertTrue(true, "pull consumer should receive the sent message");
          return;
        }
      }
    }
    throw new AssertionError("Pull consumer never received body " + bodyMarker);
  }
}
