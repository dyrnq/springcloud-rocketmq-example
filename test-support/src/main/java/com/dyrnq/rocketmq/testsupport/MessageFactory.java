package com.dyrnq.rocketmq.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.common.message.Message;

/**
 * Factory for {@link Message} instances used across tests.
 *
 * <p>Each call generates a fresh body so repeated runs don't dedupe at the broker. Both V4 ({@code
 * org.apache.rocketmq.common.message.Message}) and V5 ({@code
 * org.apache.rocketmq.client.apis.message .Message}) builders are provided; callers should import
 * the one they need. The V5 methods return the V5 type fully qualified so {@link MessageFactory}
 * itself can stay imported in tests that only use one of the two APIs.
 */
public final class MessageFactory {

  public static final String DEFAULT_TAG = "yourMessageTagA";

  private MessageFactory() {}

  // ---------- V4 (Remoting) ----------

  /** Build a v4 Remoting message with a unique body. */
  public static Message v4(String topic) {
    return v4(topic, DEFAULT_TAG);
  }

  /** Build a v4 Remoting message with a unique body and the given tag. */
  public static Message v4(String topic, String tag) {
    byte[] body = ("test-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    return new Message(topic, tag, body);
  }

  /** Build a v4 Remoting message with a body of the given size (padded with 'A'). */
  public static Message v4OfSize(String topic, int sizeBytes) {
    byte[] body = new byte[sizeBytes];
    java.util.Arrays.fill(body, (byte) 'A');
    // The first 24 bytes are randomised so identical-size messages still dedupe correctly.
    byte[] prefix = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
    System.arraycopy(prefix, 0, body, 0, Math.min(prefix.length, body.length));
    return new Message(topic, DEFAULT_TAG, body);
  }

  // ---------- V5 (gRPC) ----------

  /** Build a V5 message with a unique body and the default tag. */
  public static org.apache.rocketmq.client.apis.message.Message v5(String topic) throws Exception {
    return v5(topic, DEFAULT_TAG);
  }

  /** Build a V5 message with a unique body and the given tag. */
  public static org.apache.rocketmq.client.apis.message.Message v5(String topic, String tag)
      throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    return provider
        .newMessageBuilder()
        .setTopic(topic)
        .setTag(tag)
        .setKeys("v5-key-" + UUID.randomUUID())
        .setBody(("v5-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8))
        .build();
  }

  /** Build a V5 message with the given user property set. */
  public static org.apache.rocketmq.client.apis.message.Message v5WithProperty(
      String topic, String propertyKey, String propertyValue) throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    return provider
        .newMessageBuilder()
        .setTopic(topic)
        .setTag(DEFAULT_TAG)
        .setKeys("v5-key-" + UUID.randomUUID())
        .setBody(("v5-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8))
        .addProperty(propertyKey, propertyValue)
        .build();
  }

  /** Build a V5 message with the given keys set (use semicolon-separated per V5 convention). */
  public static org.apache.rocketmq.client.apis.message.Message v5WithKeys(
      String topic, String keys) throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    return provider
        .newMessageBuilder()
        .setTopic(topic)
        .setTag(DEFAULT_TAG)
        .setKeys(keys)
        .setBody(("v5-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8))
        .build();
  }
}
