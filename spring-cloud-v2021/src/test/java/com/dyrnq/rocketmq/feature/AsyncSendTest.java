package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.sca.rocketmq.RocketmqApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Spring Cloud Stream binder sends a message via {@link StreamBridge}. Body is inlined
 * (rather than calling {@code com.dyrnq.rocketmq.testsupport.StreamBridgeSendHelper}) because the
 * four spring-cloud-v* modules use different spring-cloud-stream majors and the helper was compiled
 * against 4.0.0.
 *
 * <p>Replaces the body of {@code com.dyrnq.sca.rocketmq.StreamProducerRunner.run} for
 * spring-cloud-v2021.
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class AsyncSendTest {

  private static final String BINDING = "consumerEvent-out-0";

  @Autowired StreamBridge streamBridge;

  @Test
  void streamBridgeSendSucceeds() {
    String body = "v2021-stream-" + UUID.randomUUID();
    Message<String> info = MessageBuilder.withPayload(body).build();
    boolean sent = streamBridge.send(BINDING, info);
    assertTrue(sent, "StreamBridge should report successful send to binding " + BINDING);
    assertNotNull(info, "Message should not be null after send");
  }
}
