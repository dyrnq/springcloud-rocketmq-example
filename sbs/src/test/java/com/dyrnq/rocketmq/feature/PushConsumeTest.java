package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.sbs.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that a push consumer subscribes to {@link Topics#TOPIC_DEMO_TOPIC} and receives a
 * message — exercised here with a raw V4 consumer (the V4 push consumer is provided by
 * spring-boot-starter; the test uses an independent consumer to keep test wiring self-contained).
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class PushConsumeTest {

  @Autowired org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private final AtomicInteger matchingReceived = new AtomicInteger();
  private String bodyMarker;

  @BeforeEach
  void setUp() throws Exception {
    bodyMarker = "sbs-marker-" + UUID.randomUUID();
    producer = new DefaultMQProducer(Groups.uniqueProducer());
    producer.setNamesrvAddr(com.dyrnq.rocketmq.testsupport.Addresses.NAMESERVER);
    producer.start();
    consumer = new DefaultMQPushConsumer(Groups.uniquePush());
    consumer.setNamesrvAddr(com.dyrnq.rocketmq.testsupport.Addresses.NAMESERVER);
    consumer.subscribe(Topics.TOPIC_DEMO_TOPIC, "*");
    consumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
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
    if (consumer != null) consumer.shutdown();
    if (producer != null) producer.shutdown();
  }

  @Test
  void receivesOneMessageAfterProducerSends() throws Exception {
    Message msg =
        new Message(
            Topics.TOPIC_DEMO_TOPIC,
            "yourMessageTagA",
            bodyMarker.getBytes(StandardCharsets.UTF_8));
    SendResult result = producer.send(msg, 3_000);
    assertNotNull(result);
    assertTrue(result.getSendStatus() == SendStatus.SEND_OK, "SendStatus should be SEND_OK");

    long deadline = System.currentTimeMillis() + 30_000;
    while (matchingReceived.get() == 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }
    assertTrue(
        matchingReceived.get() >= 1,
        "Push consumer should have received the message with marker " + bodyMarker);
  }
}
