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
 * Verifies the Spring Cloud Stream binder sends a message via {@link StreamBridge}. Body inlined —
 * see {@code com.dyrnq.rocketmq.testsupport.StreamBridgeSendHelper} comment for why.
 *
 * <p>Replaces the body of {@code com.dyrnq.sca.rocketmq.StreamProducerRunner.run} for
 * spring-cloud-v2023 (the original sent 100 messages in a tight loop with delay level 19; this test
 * sends a single message and asserts the binder dispatched it without error).
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class AsyncSendTest {

  private static final String BINDING = "consumerEvent-out-0";

  @Autowired StreamBridge streamBridge;

  @Test
  void streamBridgeSendSucceeds() {
    String body = "v2023-stream-" + UUID.randomUUID();
    Message<String> info = MessageBuilder.withPayload(body).build();
    boolean sent = streamBridge.send(BINDING, info);
    assertTrue(sent, "StreamBridge should report successful send to binding " + BINDING);
    assertNotNull(info, "Message should not be null after send");
  }
}
