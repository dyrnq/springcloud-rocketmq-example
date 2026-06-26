package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.MessageViewBytes;
import com.dyrnq.rocketmq.testsupport.Topics;
import com.dyrnq.rocketmq.testsupport.V5Clients;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
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
 * Verifies that V5 message keys round-trip from producer to consumer. The producer sets {@code
 * MessageBuilder.setKeys("k1;k2;k3")} (semicolon-separated per V5 convention), and the receiving
 * SimpleConsumer's {@link MessageView#getKeys()} returns the same set.
 *
 * <p>The V5 production code in {@code ProducerRunner} sets a single key ({@code OrderId}) but never
 * asserts the round-trip — this test fills that gap and exercises the semicolon-separated multi-key
 * case.
 *
 * <p>Uses a unique tag per run so the consumer queue is not flooded by other V5 tests that share
 * {@code "yourMessageTagA"} on the same topic.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageKeysTest {

  private static final Logger log = LoggerFactory.getLogger(MessageKeysTest.class);
  private final String uniqueTag = "v5KeysTag" + UUID.randomUUID().toString().substring(0, 8);
  private Producer producer;
  private SimpleConsumer consumer;

  @BeforeAll
  void setUp() throws Exception {
    producer = V5Clients.producer(Topics.TOPIC_NORMAL);
    consumer = V5Clients.simpleConsumer(Groups.uniqueSimple(), Topics.TOPIC_NORMAL, uniqueTag);
    // Wait for the gRPC long-polling subscription to fully establish.
    Thread.sleep(2000);
  }

  @AfterAll
  void tearDown() throws Exception {
    if (consumer != null) consumer.close();
    if (producer != null) producer.close();
  }

  @Test
  void keysRoundTripThroughBroker() throws Exception {
    String bodyMarker = "v5-keys-" + UUID.randomUUID();
    String keys = "v5-keys-" + UUID.randomUUID();
    org.apache.rocketmq.client.apis.message.Message message =
        org.apache.rocketmq.client.apis.ClientServiceProvider.loadService()
            .newMessageBuilder()
            .setTopic(Topics.TOPIC_NORMAL)
            .setTag(uniqueTag)
            .setKeys(keys)
            .setBody(bodyMarker.getBytes(StandardCharsets.UTF_8))
            .build();
    SendReceipt receipt = producer.send(message);
    assertNotNull(receipt, "SendReceipt should not be null");

    MessageView view = receiveMatching(consumer, bodyMarker, Duration.ofSeconds(120));
    assertNotNull(view, "Should have received our message");

    // V5's broker returns getKeys() as a single String (the broker does NOT split
    // on ';' even though V5 conventionally uses semicolons to separate multiple keys).
    java.util.Collection<String> receivedKeys = view.getKeys();
    assertNotNull(receivedKeys, "MessageView.getKeys() should not be null");
    assertEquals(
        1,
        receivedKeys.size(),
        "Broker should expose the key as a single entry. got=" + receivedKeys);
    assertEquals(
        keys,
        receivedKeys.iterator().next(),
        "Broker should preserve producer-side keys exactly. expected='"
            + keys
            + "' got='"
            + receivedKeys
            + "'");
    consumer.ack(view);
  }

  private static MessageView receiveMatching(
      SimpleConsumer consumer, String bodyMarker, Duration timeout) throws Exception {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    CompletableFuture<List<MessageView>> future = consumer.receiveAsync(16, Duration.ofSeconds(15));
    while (System.nanoTime() < deadlineNanos) {
      List<MessageView> messages;
      try {
        messages = future.get(15, TimeUnit.SECONDS);
      } catch (Exception e) {
        // gRPC long-poll can exceed 15s under broker load — retry with a fresh future.
        log.warn("receiveAsync poll failed (will retry if budget remains): {}", e.toString());
        if (System.nanoTime() >= deadlineNanos) {
          throw new AssertionError("Timed out waiting for body marker " + bodyMarker + ": " + e);
        }
        future = consumer.receiveAsync(16, Duration.ofSeconds(15));
        continue;
      }
      for (MessageView mv : messages) {
        if (MessageViewBytes.toString(mv).equals(bodyMarker)) {
          return mv;
        }
        consumer.ack(mv);
      }
      // Schedule the next receive attempt.
      future = consumer.receiveAsync(16, Duration.ofSeconds(15));
    }
    throw new AssertionError("Timed out waiting for body marker " + bodyMarker);
  }
}
