package com.dyrnq.sca.rocketmq;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageConst;
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
 * Verifies that {@link MessageConst#PROPERTY_TAGS} set via a Spring Cloud Stream message header is
 * propagated to the broker and visible to a raw V4 push consumer subscribed with that tag filter.
 *
 * <p>The production {@code StreamProducerRunner} for spring-cloud-v2023 already sets this header
 * (see {@code MessageConst.PROPERTY_TAGS = "test"}), but no test asserts the broker-side reception.
 * This test closes that gap using the same raw-V4-consumer workaround used elsewhere.
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BinderTagHeaderTest {

  private static final String OUT_BINDING = "consumerEvent-out-0";
  private static final String TOPIC = "demo";
  private static final String TAG = "binderTagTest";

  @Autowired StreamBridge streamBridge;

  private DefaultMQPushConsumer consumer;
  private final java.util.List<String> receivedTags = new CopyOnWriteArrayList<>();
  private final java.util.List<String> receivedBodies = new CopyOnWriteArrayList<>();

  @BeforeAll
  void setUp() throws Exception {
    String group = Groups.uniquePush();
    consumer = new DefaultMQPushConsumer(group);
    consumer.setNamesrvAddr(Addresses.NAMESERVER);
    consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    consumer.subscribe(TOPIC, TAG);
    consumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
              for (MessageExt msg : msgs) {
                receivedTags.add(msg.getTags());
                receivedBodies.add(new String(msg.getBody(), StandardCharsets.UTF_8));
              }
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
    consumer.start();
  }

  @AfterAll
  void tearDown() {
    if (consumer != null) consumer.shutdown();
  }

  @Test
  void tagHeaderReachesBroker() throws Exception {
    String body = "binder-tag-body-" + UUID.randomUUID();
    boolean sent =
        streamBridge.send(
            OUT_BINDING,
            MessageBuilder.withPayload(body).setHeader(MessageConst.PROPERTY_TAGS, TAG).build());
    assertTrue(sent, "send should succeed");

    long deadline = System.currentTimeMillis() + 30_000;
    while (!receivedBodies.contains(body) && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }

    assertTrue(
        receivedBodies.contains(body),
        "Raw V4 consumer (subscribed with tag="
            + TAG
            + ") should have received "
            + body
            + "; received bodies: "
            + receivedBodies);
    assertTrue(
        receivedTags.contains(TAG),
        "Received tags should include " + TAG + "; received: " + receivedTags);
  }
}
