package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.ClientCreater;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.MessageFactory;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the V4 send-retry configuration is applied to a producer and that a plain synchronous
 * send still completes with {@link SendStatus#SEND_OK}.
 *
 * <p>The broker topology is healthy, so a retry cannot be provoked deterministically; instead this
 * asserts the retry knobs ({@link DefaultMQProducer#getRetryTimesWhenSendFailed()}, {@link
 * DefaultMQProducer#getRetryTimesWhenSendAsyncFailed()} and {@link
 * DefaultMQProducer#isRetryAnotherBrokerWhenNotStoreOK()}) are honoured.
 */
class SendRetryTest {

  private DefaultMQProducer producer;

  @BeforeEach
  void setUp() throws Exception {
    producer = ClientCreater.createProducer(Groups.uniqueProducer());
    producer.setRetryTimesWhenSendFailed(3);
    producer.setRetryTimesWhenSendAsyncFailed(3);
    producer.setRetryAnotherBrokerWhenNotStoreOK(true);
  }

  @AfterEach
  void tearDown() {
    if (producer != null) {
      producer.shutdown();
    }
  }

  @Test
  void retryConfigIsAppliedAndSendSucceeds() throws Exception {
    assertEquals(3, producer.getRetryTimesWhenSendFailed());
    assertEquals(3, producer.getRetryTimesWhenSendAsyncFailed());
    assertTrue(producer.isRetryAnotherBrokerWhenNotStoreOK());

    Message message = MessageFactory.v4(Topics.TOPIC_NORMAL);
    SendResult result = producer.send(message);
    assertNotNull(result, "SendResult should not be null");
    assertEquals(SendStatus.SEND_OK, result.getSendStatus(), "SendStatus should be SEND_OK");
  }
}
