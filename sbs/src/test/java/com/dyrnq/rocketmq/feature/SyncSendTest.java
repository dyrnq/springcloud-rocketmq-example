package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.sbs.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that {@link RocketMQTemplate#syncSend(String, Object)} succeeds
 * via the rocketmq-spring-boot-starter (V4 client).
 *
 * <p>Replaces the body of {@code com.dyrnq.rocketmq.sbs.ProducerRunner.run}.</p>
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class SyncSendTest {

    @Autowired
    RocketMQTemplate rocketMQTemplate;

    @Test
    void syncSendDemoTopic() {
        String body = "sbs-sync-" + UUID.randomUUID();
        SendResult result = rocketMQTemplate.syncSend(Topics.TOPIC_DEMO_TOPIC, body);
        assertNotNull(result, "SendResult should not be null");
        assertEquals(SendStatus.SEND_OK, result.getSendStatus(), "SendStatus should be SEND_OK");
    }
}
