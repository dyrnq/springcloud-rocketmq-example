package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
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

/**
 * Verifies a V5 {@link SimpleConsumer} can receive via {@code receiveAsync} and ack via {@code
 * ackAsync}.
 *
 * <p>Replaces the body of {@code
 * org.apache.rocketmq.client.java.example.AsyncSimpleConsumerExample.main}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsyncSimpleConsumeTest {

  private static final Logger log = LoggerFactory.getLogger(AsyncSimpleConsumeTest.class);

  private Producer producer;
  private SimpleConsumer consumer;
  private final String bodyMarker = "v5-async-simple-" + UUID.randomUUID();
  // Unique tag per test instance so we don't compete with backlog from prior runs.
  private final String uniqueTag =
      "v5AsyncSimpleTag" + UUID.randomUUID().toString().substring(0, 8);
  private final AtomicBoolean received = new AtomicBoolean(false);
  private final ScheduledExecutorService pollExecutor =
      Executors.newSingleThreadScheduledExecutor();

  @BeforeAll
  void setUp() throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    org.apache.rocketmq.client.apis.ClientConfiguration clientConfiguration =
        org.apache.rocketmq.client.apis.ClientConfiguration.newBuilder()
            .setEndpoints(com.dyrnq.rocketmq.testsupport.Addresses.PROXY)
            .build();
    producer =
        provider
            .newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics(Topics.TOPIC_NORMAL)
            .build();
    String consumerGroup = Groups.uniqueSimple();
    consumer =
        provider
            .newSimpleConsumerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setConsumerGroup(consumerGroup)
            .setAwaitDuration(Duration.ofSeconds(15))
            .setSubscriptionExpressions(
                Collections.singletonMap(
                    Topics.TOPIC_NORMAL, new FilterExpression(uniqueTag, FilterExpressionType.TAG)))
            .build();
    // Wait for the gRPC long-polling connection to be ready.
    Thread.sleep(2000);
  }

  @AfterAll
  void tearDown() throws Exception {
    pollExecutor.shutdownNow();
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
  void receiveAndAckAsync() throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(Topics.TOPIC_NORMAL)
            .setTag(uniqueTag)
            .setKeys("v5-async-simple-key-" + UUID.randomUUID())
            .setBody(bodyMarker.getBytes(StandardCharsets.UTF_8))
            .build();
    SendReceipt receipt = producer.send(message);
    assertNotNull(receipt, "SendReceipt should not be null");

    // Poll receiveAsync up to 60s.
    long deadline = System.currentTimeMillis() + 60_000;
    CompletableFuture<List<MessageView>> future = consumer.receiveAsync(16, Duration.ofSeconds(15));
    while (System.currentTimeMillis() < deadline) {
      try {
        List<MessageView> messages = future.get(15, TimeUnit.SECONDS);
        for (MessageView mv : messages) {
          String body = bytesToString(mv.getBody());
          if (body.equals(bodyMarker)) {
            log.info("Async-received marker body, acking");
            consumer.ackAsync(mv);
            received.set(true);
          }
        }
        if (received.get()) {
          break;
        }
        // Schedule the next receive attempt.
        future = consumer.receiveAsync(16, Duration.ofSeconds(15));
      } catch (Exception e) {
        log.warn("receiveAsync poll failed: {}", e.toString());
        if (System.currentTimeMillis() >= deadline) {
          break;
        }
        future = consumer.receiveAsync(16, Duration.ofSeconds(15));
      }
    }
    assertTrue(
        received.get(),
        "AsyncSimpleConsumer should have received message with marker " + bodyMarker);
  }
}
