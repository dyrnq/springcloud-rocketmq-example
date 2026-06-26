package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dyrnq.rocketmq.sbsv5.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies FIFO send via {@code syncSendFifoMessage} (replaces {@code
 * ProducerRunner.testSendFIFOMessage()}).
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class FifoMessageTest {

  @Autowired RocketMQClientTemplate rocketMQClientTemplate;

  @Test
  void syncSendFifoMessage() {
    SendReceipt receipt =
        rocketMQClientTemplate.syncSendFifoMessage(
            Topics.TOPIC_FIFO, "sbs-v5-fifo", Groups.MESSAGE_GROUP_FIFO);
    assertNotNull(receipt, "SendReceipt should not be null");
    assertNotNull(receipt.getMessageId(), "MessageId should not be null");
  }
}
