package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.ClientCreater;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.ReceiptAwaiter;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a push consumer subscribes to {@link Topics#TOPIC_A} and receives
 * the message that was sent to it.
 *
 * <p>Replaces the body of {@code com.dyrnq.rocketmq.simple.SimplePushConsumer.main}.</p>
 */
class PushConsumeTest {

    private DefaultMQProducer producer;
    private DefaultMQPushConsumer consumer;
    private final AtomicInteger matchingReceived = new AtomicInteger();
    private String bodyMarker;

    @BeforeEach
    void setUp() throws Exception {
        bodyMarker = "marker-" + UUID.randomUUID();
        producer = ClientCreater.createProducer(Groups.uniqueProducer());
        consumer = ClientCreater.createPushConsumer(Groups.uniquePush());
        consumer.subscribe(Topics.TOPIC_A, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (org.apache.rocketmq.common.message.MessageExt m : msgs) {
                if (new String(m.getBody(), StandardCharsets.UTF_8).contains(bodyMarker)) {
                    matchingReceived.incrementAndGet();
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.shutdown();
        }
        if (producer != null) {
            producer.shutdown();
        }
    }

    @Test
    void receivesOneMessageAfterProducerSends() throws Exception {
        Message msg = new Message(Topics.TOPIC_A, "yourMessageTagA", bodyMarker.getBytes(StandardCharsets.UTF_8));
        SendResult result = producer.send(msg, 3_000);
        assertNotNull(result);
        assertEquals(SendStatus.SEND_OK, result.getSendStatus());

        ReceiptAwaiter.waitingFor(matchingReceived, 1, Duration.ofSeconds(30))
            .poll("push consumer to receive 1 message with marker on " + Topics.TOPIC_A);
        assertTrue(matchingReceived.get() >= 1, "Should have received at least one matching message");
    }
}
