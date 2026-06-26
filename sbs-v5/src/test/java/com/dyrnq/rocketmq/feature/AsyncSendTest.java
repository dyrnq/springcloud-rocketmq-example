package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dyrnq.rocketmq.sbsv5.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies async send via {@code RocketMQClientTemplate.asyncSendNormalMessage} (replaces {@code
 * ProducerRunner.testAsyncSendMessage()}).
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class AsyncSendTest {

  @Autowired RocketMQClientTemplate rocketMQClientTemplate;

  @Test
  void asyncSendNormalMessage() throws Exception {
    CompletableFuture<SendReceipt> future = new CompletableFuture<>();
    rocketMQClientTemplate.asyncSendNormalMessage(Topics.TOPIC_NORMAL, "sbs-v5-async", future);
    SendReceipt receipt = future.get(10, TimeUnit.SECONDS);
    assertNotNull(receipt, "Async send should resolve with a SendReceipt");
    assertNotNull(receipt.getMessageId(), "MessageId should not be null");
  }
}
