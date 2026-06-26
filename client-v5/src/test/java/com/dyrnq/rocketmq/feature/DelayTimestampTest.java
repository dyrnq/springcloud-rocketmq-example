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
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a message delivered at a future timestamp via
 * {@code setDeliveryTimestamp(now + delay)} on the V5 gRPC client.
 *
 * <p>Replaces the body of
 * {@code org.apache.rocketmq.client.java.example.ProducerDelayMessageExample.main}.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelayTimestampTest {

    private static final Logger log = LoggerFactory.getLogger(DelayTimestampTest.class);

    /** 10s delivery delay (matches the original example). */
    private static final Duration DELAY = Duration.ofSeconds(10);
    /** Tolerance: delivery should be at least 8s after send, but not more than 30s. */
    private static final long LOWER_BOUND_MS = 8_000;
    private static final long UPPER_BOUND_MS = 30_000;

    private Producer producer;
    private PushConsumer consumer;
    private final AtomicLong firstDeliveryAt = new AtomicLong(0);
    // Unique body marker + tag per test instance so we don't pick up backlog from
    // prior runs of the original ProducerDelayMessageExample main.
    private final String bodyMarker = "v5-delay-" + UUID.randomUUID();
    private final String uniqueTag = "v5DelayTag" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void setUp() throws Exception {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        org.apache.rocketmq.client.apis.ClientConfiguration clientConfiguration =
            org.apache.rocketmq.client.apis.ClientConfiguration.newBuilder()
                .setEndpoints(com.dyrnq.rocketmq.testsupport.Addresses.PROXY)
                .build();
        producer = provider.newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics(Topics.TOPIC_DELAY)
            .build();

        String consumerGroup = Groups.uniquePush();
        consumer = provider.newPushConsumerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setConsumerGroup(consumerGroup)
            .setSubscriptionExpressions(Collections.singletonMap(
                Topics.TOPIC_DELAY, new FilterExpression(uniqueTag, FilterExpressionType.TAG)))
            .setMessageListener((MessageView messageView) -> {
                String body = bytesToString(messageView.getBody());
                if (body.equals(bodyMarker)) {
                    log.info("Received matching delayed message, id={}", messageView.getMessageId());
                    firstDeliveryAt.compareAndSet(0, System.currentTimeMillis());
                }
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
    void sendDelayedMessageArrivesAfterDelay() throws Exception {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        Message message = provider.newMessageBuilder()
            .setTopic(Topics.TOPIC_DELAY)
            .setTag(uniqueTag)
            .setKeys("v5-delay-key-" + UUID.randomUUID())
            .setDeliveryTimestamp(System.currentTimeMillis() + DELAY.toMillis())
            .setBody(bodyMarker.getBytes(StandardCharsets.UTF_8))
            .build();

        long sentAt = System.currentTimeMillis();
        SendReceipt receipt = producer.send(message);
        assertNotNull(receipt, "SendReceipt should not be null");

        // Poll until delivery or timeout (max ~30s).
        long deadline = sentAt + UPPER_BOUND_MS;
        while (firstDeliveryAt.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        long deliveryAt = firstDeliveryAt.get();
        assertTrue(deliveryAt > 0, "Delayed message should have been delivered within " + UPPER_BOUND_MS + "ms");
        long elapsed = deliveryAt - sentAt;
        log.info("Elapsed: {}ms", elapsed);
        assertTrue(elapsed >= LOWER_BOUND_MS,
            "Delivery should be at least " + LOWER_BOUND_MS + "ms after send, was " + elapsed + "ms");
    }
}
