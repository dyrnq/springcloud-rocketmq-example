package com.dyrnq.rocketmq.feature;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that a single synchronous send to {@link Topics#TOPIC_A} succeeds.
 *
 * <p>Replaces the body of {@code com.dyrnq.rocketmq.simple.SimpleSyncProducer.main}.</p>
 */
class SyncSendTest {

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
    void sendOneMessageToTopicA() throws Exception {
        Message msg = MessageFactory.v4(Topics.TOPIC_A);
        SendResult result = producer.send(msg, 3_000);
        assertNotNull(result);
        assertEquals(SendStatus.SEND_OK, result.getSendStatus(),
            "Sync send to " + Topics.TOPIC_A + " should succeed");
    }
}
