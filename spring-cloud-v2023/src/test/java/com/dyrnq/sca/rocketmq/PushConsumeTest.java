package com.dyrnq.sca.rocketmq;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Spring Cloud Stream binder end-to-end for spring-cloud-v2023. Sends a message via
 * {@link StreamBridge} and asserts a raw V4 push consumer on the same topic ({@code demo}) receives
 * it. The function-bean wiring ({@code StreamConsumer.consumerEvent}) is exercised by the
 * {@code @SpringBootTest} context boot; this test verifies the broker round-trip.
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushConsumeTest {

  private static final String OUT_BINDING = "consumerEvent-out-0";
  private static final String TOPIC = "demo";

  @Autowired StreamBridge streamBridge;

  private DefaultMQPushConsumer consumer;
  private final List<String> receivedBodies = new CopyOnWriteArrayList<>();

  @BeforeAll
  void setUp() throws Exception {
    String group = Groups.uniquePush();
    consumer = new DefaultMQPushConsumer(group);
    consumer.setNamesrvAddr(Addresses.NAMESERVER);
    consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
    consumer.subscribe(TOPIC, "*");
    consumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
              for (MessageExt msg : msgs) {
                receivedBodies.add(new String(msg.getBody(), StandardCharsets.UTF_8));
              }
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
    consumer.start();
  }

  @AfterAll
  void tearDown() throws Exception {
    if (consumer != null) {
      consumer.shutdown();
    }
  }

  @Test
  void receivesOneMessageAfterStreamBridgeSend() throws Exception {
    String body = "v2023-push-marker-" + UUID.randomUUID();
    boolean sent = streamBridge.send(OUT_BINDING, MessageBuilder.withPayload(body).build());
    assertTrue(sent, "send should succeed");

    long deadline = System.currentTimeMillis() + 30_000;
    while (!receivedBodies.contains(body) && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }
    assertTrue(
        receivedBodies.contains(body),
        "Received bodies should contain " + body + ", got " + receivedBodies);
  }
}
