package com.dyrnq.rocketmq.testsupport;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.apis.message.MessageView;

/**
 * Helpers for decoding the {@link ByteBuffer} body of a V5 {@link MessageView} as UTF-8 text.
 *
 * <p>V5's {@code MessageView.getBody()} returns a {@link ByteBuffer} (not {@code byte[]} like V4's
 * {@code Message.getBody()}). Tests usually compare against a marker string, so this small helper
 * exists to keep the conversion in one place.
 */
public final class MessageViewBytes {

  private MessageViewBytes() {}

  /** Decode the V5 message body as UTF-8 text. The buffer's position is left unchanged. */
  public static String toString(MessageView messageView) {
    ByteBuffer buf = messageView.getBody();
    ByteBuffer copy = buf.duplicate();
    byte[] bytes = new byte[copy.remaining()];
    copy.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
