package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.sbsv5.RocketmqApplication;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a V5 push consumer subscribes to {@link Topics#TOPIC_NORMAL} and
 * receives a message (replaces the consumer side of {@code V5PushConsumerRunner}).
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushConsumeTest {

    private Producer producer;
    private PushConsumer consumer;
    private final java.util.List<String> matchingBodies = new CopyOnWriteArrayList<>();
    private final AtomicLong firstDeliveryAt = new AtomicLong(0);
    private final String bodyMarker = "sbs-v5-marker-" + UUID.randomUUID();
    private final String uniqueTag = "sbsV5MarkerTag" + UUID.randomUUID().toString().substring(0, 8);

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
        String consumerGroup = "sbs-v5-push-" + UUID.randomUUID().toString().substring(0, 8);
        consumer = provider.newPushConsumerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setConsumerGroup(consumerGroup)
            .setSubscriptionExpressions(Collections.singletonMap(
                Topics.TOPIC_NORMAL, new FilterExpression(uniqueTag, FilterExpressionType.TAG)))
            .setMessageListener((MessageView messageView) -> {
                String body = bytesToString(messageView.getBody());
                firstDeliveryAt.compareAndSet(0, System.currentTimeMillis());
                matchingBodies.add(body);
                return ConsumeResult.SUCCESS;
            })
            .build();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (consumer != null) consumer.close();
        if (producer != null) producer.close();
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
        Message message = provider.newMessageBuilder()
            .setTopic(Topics.TOPIC_NORMAL)
            .setTag(uniqueTag)
            .setKeys("sbs-v5-push-key-" + UUID.randomUUID())
            .setBody(bodyMarker.getBytes(StandardCharsets.UTF_8))
            .build();
        SendReceipt receipt = producer.send(message);
        assertNotNull(receipt, "SendReceipt should not be null");

        long deadline = System.currentTimeMillis() + 30_000;
        while (firstDeliveryAt.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertTrue(firstDeliveryAt.get() > 0,
            "Push consumer should have received the message with marker " + bodyMarker);
        assertTrue(matchingBodies.contains(bodyMarker),
            "Received bodies should contain marker " + bodyMarker + ", got " + matchingBodies);
    }
}
