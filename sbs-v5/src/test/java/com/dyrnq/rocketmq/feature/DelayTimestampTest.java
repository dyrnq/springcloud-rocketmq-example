package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dyrnq.rocketmq.sbsv5.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.time.Duration;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies scheduled/delay send via {@code syncSendDelayMessage} (replaces {@code
 * ProducerRunner.testSendDelayMessage()}).
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class DelayTimestampTest {

  @Autowired RocketMQClientTemplate rocketMQClientTemplate;

  @Test
  void syncSendDelayMessage() {
    SendReceipt receipt =
        rocketMQClientTemplate.syncSendDelayMessage(
            Topics.TOPIC_DELAY, "sbs-v5-delay", Duration.ofSeconds(10));
    assertNotNull(receipt, "SendReceipt should not be null");
    assertNotNull(receipt.getMessageId(), "MessageId should not be null");
  }
}
