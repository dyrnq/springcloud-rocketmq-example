package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.sbs.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a V4 scheduled message (deliver at absolute timestamp = now + 10s)
 * sent via a raw {@link DefaultMQProducer} is delivered to a push consumer
 * on {@link Topics#TOPIC_DELAY} at least 9s after send time. V4 uses
 * {@code Message.setDeliverTimeMs} (epoch millis) for scheduled delivery.
 *
 * <p>Each test method uses a fresh consumer and a unique body marker so
 * the timing assertion is not polluted by backlog from previous test
 * runs (delayed messages whose timer has already expired get delivered
 * immediately on consumer subscribe).</p>
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class DelayTimestampTest {

    private static final long DELAY_MS = 10_000L;
    private static final long DELAY_TOLERANCE_MS = 1_000L;

    private DefaultMQProducer producer;
    private DefaultMQPushConsumer consumer;
    private final AtomicInteger matchingReceived = new AtomicInteger();
    private final AtomicLong firstDeliveryAt = new AtomicLong(0);
    private String bodyMarker;

    @BeforeEach
    void setUp() throws Exception {
        bodyMarker = "sbs-delay-ts-" + UUID.randomUUID();

        AclClientRPCHook acl = new AclClientRPCHook(new SessionCredentials("ak", "sk"));
        producer = new DefaultMQProducer(Groups.uniqueProducer(), acl, true, null);
        producer.setNamesrvAddr(Addresses.NAMESERVER);
        producer.start();

        String group = Groups.uniquePush();
        consumer = new DefaultMQPushConsumer(group, acl, new AllocateMessageQueueAveragely(), true, null);
        consumer.setNamesrvAddr(Addresses.NAMESERVER);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(Topics.TOPIC_DELAY, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt m : msgs) {
                String body = new String(m.getBody(), StandardCharsets.UTF_8);
                if (body.equals(bodyMarker)) {
                    firstDeliveryAt.compareAndSet(0, System.currentTimeMillis());
                    matchingReceived.incrementAndGet();
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) consumer.shutdown();
        if (producer != null) producer.shutdown();
    }

    @Test
    void receivesDelayedMessageAfterTimestamp() throws Exception {
        long deliverAt = System.currentTimeMillis() + DELAY_MS;
        Message msg = new Message(Topics.TOPIC_DELAY, bodyMarker.getBytes(StandardCharsets.UTF_8));
        msg.setDeliverTimeMs(deliverAt);

        long sentAt = System.currentTimeMillis();
        SendResult result = producer.send(msg);
        assertNotNull(result, "SendResult should not be null");
        assertTrue(result.getSendStatus() == SendStatus.SEND_OK,
            "SendStatus should be SEND_OK, was " + result.getSendStatus());

        long deadline = System.currentTimeMillis() + 30_000;
        while (matchingReceived.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertTrue(matchingReceived.get() >= 1,
            "Push consumer should have received body " + bodyMarker);

        long elapsed = firstDeliveryAt.get() - sentAt;
        assertTrue(elapsed >= DELAY_MS - DELAY_TOLERANCE_MS,
            "Delivery should be at least ~10s after send, but was " + elapsed + "ms");
    }
}
