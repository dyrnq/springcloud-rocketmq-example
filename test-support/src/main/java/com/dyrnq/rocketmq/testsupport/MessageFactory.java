package com.dyrnq.rocketmq.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.rocketmq.common.message.Message;

/**
 * Factory for {@link Message} instances used across tests.
 *
 * <p>Each call generates a fresh body so repeated runs don't dedupe at the broker.
 */
public final class MessageFactory {

  public static final String DEFAULT_TAG = "yourMessageTagA";

  private MessageFactory() {}

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
}
