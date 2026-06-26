package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a V5 {@link SimpleConsumer} can receive and ack a message.
 *
 * <p>Replaces the body of
 * {@code org.apache.rocketmq.client.java.example.SimpleConsumerExample.main}.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleConsumeTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleConsumeTest.class);

    private Producer producer;
    private SimpleConsumer consumer;
    private final String bodyMarker = "v5-simple-" + UUID.randomUUID();
    // Unique tag per test instance so we don't compete with pre-existing backlog on normalTopic.
    private final String uniqueTag = "v5simpleTag" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void setUp() throws Exception {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        org.apache.rocketmq.client.apis.ClientConfiguration clientConfiguration =
            org.apache.rocketmq.client.apis.ClientConfiguration.newBuilder()
                .setEndpoints(com.dyrnq.rocketmq.testsupport.Addresses.PROXY)
                .build();
        producer = provider.newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics(Topics.TOPIC_NORMAL)
            .build();
        String consumerGroup = Groups.uniqueSimple();
        // SimpleConsumer long-polling: use subscription on the topic. The original example
        // used `yourTopic` which doesn't exist on the broker, so we use TOPIC_NORMAL.
        consumer = provider.newSimpleConsumerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setConsumerGroup(consumerGroup)
            .setAwaitDuration(Duration.ofSeconds(15))
            .setSubscriptionExpressions(Collections.singletonMap(
                Topics.TOPIC_NORMAL, new FilterExpression(uniqueTag, FilterExpressionType.TAG)))
            .build();
        // Wait for the consumer to be ready before the test sends. The gRPC client
        // establishes the long-polling connection asynchronously; sending the message
        // before it's ready may cause the message to be skipped.
        Thread.sleep(2000);
    }

    @AfterAll
    void tearDown() throws Exception {
        if (consumer != null) {
            consumer.close();
        }
        if (producer != null) {
            producer.close();
        }
    }

    private static String bytesToString(java.nio.ByteBuffer buf) {
        java.nio.ByteBuffer copy = buf.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void receiveAndAckSync() throws Exception {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        Message message = provider.newMessageBuilder()
            .setTopic(Topics.TOPIC_NORMAL)
            .setTag(uniqueTag)
            .setKeys("v5-simple-key-" + UUID.randomUUID())
            .setBody(bodyMarker.getBytes(StandardCharsets.UTF_8))
            .build();
        SendReceipt receipt = producer.send(message);
        assertNotNull(receipt, "SendReceipt should not be null");

        // Poll up to 60s with a 15s long-polling awaitDuration per call.
        boolean found = false;
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            List<MessageView> messages = consumer.receive(16, Duration.ofSeconds(15));
            for (MessageView mv : messages) {
                String body = bytesToString(mv.getBody());
                log.info("Received body={}", body);
                if (body.equals(bodyMarker)) {
                    consumer.ack(mv);
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
        }
        assertTrue(found, "SimpleConsumer should have received message with marker " + bodyMarker);
    }
}
