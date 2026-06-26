package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the topic config of {@link Topics#TOPIC_A} is queryable from the broker
 * via {@link DefaultMQAdminExt}.
 *
 * <p>Replaces the body of {@code org.apache.rocketmq.tools.command.MQAdminStartup.main}
 * (the mqadmin CLI entry point that dispatches the {@code getTopicConfig} subcommand).</p>
 */
class AdminToolTest {

    private DefaultMQAdminExt admin;

    @BeforeEach
    void startAdmin() throws MQClientException {
        admin = new DefaultMQAdminExt();
        admin.setNamesrvAddr(Addresses.NAMESERVER);
        admin.start();
    }

    @AfterEach
    void stopAdmin() {
        if (admin != null) {
            admin.shutdown();
        }
    }

    @Test
    void getTopicConfigReturnsConfig() throws Exception {
        TopicConfig config = admin.examineTopicConfig(Addresses.BROKER_ADDR_1, Topics.TOPIC_A);
        assertNotNull(config, "TopicConfig for " + Topics.TOPIC_A + " on " + Addresses.BROKER_ADDR_1 + " should not be null");
        assertEquals(Topics.TOPIC_A, config.getTopicName());
        assertTrue(config.getReadQueueNums() > 0, "Topic should have at least 1 read queue");
        assertTrue(config.getWriteQueueNums() > 0, "Topic should have at least 1 write queue");
    }
}
