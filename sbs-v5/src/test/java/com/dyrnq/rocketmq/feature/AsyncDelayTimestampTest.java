package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dyrnq.rocketmq.sbsv5.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies async scheduled/delay send via {@code RocketMQClientTemplate.asyncSendDelayMessage} (the
 * V5 client has no fixed delay-level API, so V5 tests are timestamp-based). This complements the
 * sync variant in {@code com.dyrnq.rocketmq.feature.DelayTimestampTest} by exercising the async
 * path that {@code ProducerRunner} also uses.
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class AsyncDelayTimestampTest {

  @Autowired RocketMQClientTemplate rocketMQClientTemplate;

  @Test
  void asyncSendDelayMessage() throws Exception {
    CompletableFuture<SendReceipt> future = new CompletableFuture<>();
    rocketMQClientTemplate.asyncSendDelayMessage(
        Topics.TOPIC_DELAY,
        "sbs-v5-async-delay".getBytes(StandardCharsets.UTF_8),
        Duration.ofSeconds(10),
        future);
    SendReceipt receipt = future.get(15, TimeUnit.SECONDS);
    assertNotNull(receipt, "Async delay send should resolve with a SendReceipt");
    assertNotNull(receipt.getMessageId(), "MessageId should not be null");
  }
}
