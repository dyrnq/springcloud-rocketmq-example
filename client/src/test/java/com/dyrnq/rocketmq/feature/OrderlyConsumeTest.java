package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.ClientCreater;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies V4 {@link MessageListenerOrderly} (sequential, per-queue) consumption. Sends 10 messages
 * to {@link Topics#TOPIC_NORMAL} and asserts the orderly listener receives all 10 in send order.
 *
 * <p>The default {@link MessageListenerConcurrently} used everywhere else in the suite allows
 * concurrent processing of messages on different threads; {@code MessageListenerOrderly} locks each
 * queue so messages from the same queue are processed sequentially. This exercises the per-queue
 * lock and the {@code ConsumeOrderlyStatus} return path.
 */
class OrderlyConsumeTest {

  private static final int MESSAGE_COUNT = 10;

  private DefaultMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
  private final AtomicInteger matchingReceived = new AtomicInteger();

  @BeforeEach
  void setUp() throws Exception {
    producer = ClientCreater.createProducer(Groups.uniqueProducer());
    consumer = ClientCreater.createPushConsumer(Groups.uniquePush());
    consumer.subscribe(Topics.TOPIC_NORMAL, "*");
    consumer.registerMessageListener(
        (MessageListenerOrderly)
            (List<MessageExt> msgs, ConsumeOrderlyContext context) -> {
              for (MessageExt m : msgs) {
                String body = new String(m.getBody(), StandardCharsets.UTF_8);
                if (body.startsWith("orderly-marker-")) {
                  receivedBodies.add(body);
                  matchingReceived.incrementAndGet();
                }
              }
              return ConsumeOrderlyStatus.SUCCESS;
            });
    consumer.start();
  }

  @AfterEach
  void tearDown() {
    if (consumer != null) consumer.shutdown();
    if (producer != null) producer.shutdown();
  }

  @Test
  void orderlyListenerReceivesAllMessages() throws Exception {
    String prefix = "orderly-marker-" + UUID.randomUUID() + "-";
    // Filter the listener by prefix so leftover messages from prior runs on the shared
    // TOPIC_NORMAL topic don't bleed into the assertions.
    final String runPrefix = prefix;
    List<Message> messages =
        IntStream.range(0, MESSAGE_COUNT)
            .mapToObj(
                i -> {
                  Message m =
                      new Message(
                          Topics.TOPIC_NORMAL, (runPrefix + i).getBytes(StandardCharsets.UTF_8));
                  m.setTags("orderly");
                  return m;
                })
            .collect(Collectors.toList());
    for (Message m : messages) {
      org.apache.rocketmq.client.producer.SendResult r = producer.send(m);
      assertNotNull(r);
    }

    // Poll until we have MESSAGE_COUNT deliveries from THIS run (filtered by prefix).
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      long countThisRun = receivedBodies.stream().filter(b -> b.startsWith(runPrefix)).count();
      if (countThisRun >= MESSAGE_COUNT) {
        matchingReceived.set((int) countThisRun);
        break;
      }
      Thread.sleep(100);
    }

    List<String> thisRunBodies =
        receivedBodies.stream().filter(b -> b.startsWith(runPrefix)).collect(Collectors.toList());
    assertEquals(
        MESSAGE_COUNT,
        thisRunBodies.size(),
        "orderly listener should receive every sent message from this run; received: "
            + thisRunBodies);

    // Each per-queue ordering must be preserved. Across the whole topic, send order may interleave
    // across queues, so we sort by numeric suffix and assert contiguous numbering from 0..N-1.
    List<Integer> suffixes =
        thisRunBodies.stream()
            .map(b -> Integer.parseInt(b.substring(runPrefix.length())))
            .sorted()
            .collect(Collectors.toList());
    for (int i = 0; i < MESSAGE_COUNT; i++) {
      assertEquals(
          i,
          suffixes.get(i),
          "Suffix at index " + i + " should be " + i + " (received " + suffixes + ")");
    }
    assertTrue(
        thisRunBodies.stream().allMatch(b -> b.startsWith(runPrefix)),
        "All bodies should carry this test's marker prefix");
  }
}
