package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
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
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a V5 push consumer subscribes to {@link Topics#TOPIC_A} and receives
 * a message sent to it. Uses {@code Addresses.PROXY} (8181) — the original
 * {@code PushConsumerExample} mistakenly pointed at 9876 (the namesrv port); this
 * test reuses the proxy endpoint and the test documents the fix.
 *
 * <p>Replaces the body of
 * {@code org.apache.rocketmq.client.java.example.PushConsumerExample.main}
 * (with the 9876→8181 port bug corrected).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushConsumeTest {

    private static final Logger log = LoggerFactory.getLogger(PushConsumeTest.class);

    private Producer producer;
    private PushConsumer consumer;
    private final java.util.List<String> matchingBodies = new CopyOnWriteArrayList<>();

    @BeforeAll
    void setUp() throws Exception {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        org.apache.rocketmq.client.apis.ClientConfiguration clientConfiguration =
            org.apache.rocketmq.client.apis.ClientConfiguration.newBuilder()
                // Port fix: was 9876 (namesrv) in the original example; V5 client needs
                // the proxy endpoint, not the namesrv endpoint.
                .setEndpoints(com.dyrnq.rocketmq.testsupport.Addresses.PROXY)
                .build();
        producer = provider.newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics(Topics.TOPIC_A)
            .build();
        String consumerGroup = Groups.uniquePush();
        consumer = provider.newPushConsumerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setConsumerGroup(consumerGroup)
            .setSubscriptionExpressions(Collections.singletonMap(
                Topics.TOPIC_A, new FilterExpression("yourMessageTagA", FilterExpressionType.TAG)))
            .setMessageListener((MessageView messageView) -> {
                String body = bytesToString(messageView.getBody());
                log.info("Received push body={}", body);
                matchingBodies.add(body);
                return ConsumeResult.SUCCESS;
            })
            .build();
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
    void receivesOneMessageAfterProducerSends() throws Exception {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        String bodyMarker = "marker-" + UUID.randomUUID();
        Message message = provider.newMessageBuilder()
            .setTopic(Topics.TOPIC_A)
            .setTag("yourMessageTagA")
            .setKeys("v5-push-key-" + UUID.randomUUID())
            .setBody(bodyMarker.getBytes(StandardCharsets.UTF_8))
            .build();
        SendReceipt receipt = producer.send(message);
        assertNotNull(receipt, "SendReceipt should not be null");

        // Wait up to 30s for our specific marker to arrive (other messages may
        // already be on the topic; firstDeliveryAt may fire for them).
        long deadline = System.currentTimeMillis() + 30_000;
        while (!matchingBodies.contains(bodyMarker) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertTrue(matchingBodies.contains(bodyMarker),
            "Received bodies should contain marker " + bodyMarker + ", got " + matchingBodies);
    }
}
