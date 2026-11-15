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
import org.apache.rocketmq.remoting.protocol.RemotingSysResponseCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Documents the open-source throttling surface.
 *
 * <p>The public docs describe throttling as reply-code 530 ({@code TOO_MANY_REQUESTS}); that code
 * only exists in the commercial (Alibaba Cloud) product. Open-source RocketMQ signals backpressure
 * with {@link RemotingSysResponseCode#SYSTEM_BUSY} (broker/store too busy) or {@link
 * ResponseCode#FLOW_CONTROL}. Triggering those on a healthy single-broker test topology is not
 * deterministic, so this test asserts the producer retry knobs applied when a retryable/throttled
 * failure is encountered and keeps a normal-send baseline.
 */
class SendThrottleTest {

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
  void throttleSurfaceAndRetryKnobsAreAvailable() throws Exception {
    assertEquals(2, RemotingSysResponseCode.SYSTEM_BUSY);
    assertEquals(215, ResponseCode.FLOW_CONTROL);

    assertEquals(3, producer.getRetryTimesWhenSendFailed());
    assertEquals(3, producer.getRetryTimesWhenSendAsyncFailed());
    assertTrue(producer.isRetryAnotherBrokerWhenNotStoreOK());

    SendResult result = producer.send(MessageFactory.v4(Topics.TOPIC_NORMAL));
    assertNotNull(result, "SendResult should not be null");
    assertEquals(SendStatus.SEND_OK, result.getSendStatus(), "SendStatus should be SEND_OK");
  }
}
