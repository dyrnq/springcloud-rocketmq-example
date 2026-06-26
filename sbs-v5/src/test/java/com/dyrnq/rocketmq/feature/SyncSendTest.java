package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.sbsv5.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that {@link RocketMQClientTemplate#syncSendNormalMessage(String, Object)}
 * succeeds (replaces the body of {@code ProducerRunner.testSendNormalMessage()}).
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class SyncSendTest {

    @Autowired
    RocketMQClientTemplate rocketMQClientTemplate;

    @Test
    void syncSendNormalMessage() {
        SendReceipt receipt = rocketMQClientTemplate.syncSendNormalMessage(
            Topics.TOPIC_NORMAL, "sbs-v5-sync");
        assertNotNull(receipt, "SendReceipt should not be null");
        assertNotNull(receipt.getMessageId(), "MessageId should not be null");
    }
}
