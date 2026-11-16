package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dyrnq.rocketmq.ClientCreater;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.MessageFactory;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a producer can send a batch of messages to {@link Topics#TOPIC_A} in one
 * round-trip.
 *
 * <p>Exercises the {@code DefaultMQProducer.send(Collection, long)} batch-send path.
 */
class BatchSendTest {

  private DefaultMQProducer producer;

  @BeforeEach
  void startProducer() throws Exception {
    producer = ClientCreater.createProducer(Groups.uniqueProducer());
  }

  @AfterEach
  void stopProducer() {
    if (producer != null) {
      producer.shutdown();
    }
  }

  @Test
  void sendBatchMessagesToTopicA() throws Exception {
    List<Message> messages = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      messages.add(MessageFactory.v4(Topics.TOPIC_A));
    }
    SendResult result = producer.send(messages, 3_000);
    assertNotNull(result);
    assertEquals(
        SendStatus.SEND_OK,
        result.getSendStatus(),
        "Batch send to " + Topics.TOPIC_A + " should succeed");
  }

  @Test
  void compressedBatchSendSucceeds() throws Exception {
    producer.setCompressMsgBodyOverHowmuch(1);
    List<Message> messages = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      messages.add(MessageFactory.v4OfSize(Topics.TOPIC_A, 1024));
    }
    SendResult result = producer.send(messages, 3_000);
    assertNotNull(result);
    assertEquals(
        SendStatus.SEND_OK,
        result.getSendStatus(),
        "Compressed batch send to " + Topics.TOPIC_A + " should succeed");
  }
}
