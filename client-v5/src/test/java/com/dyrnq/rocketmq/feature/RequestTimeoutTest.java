package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Topics;
import com.dyrnq.rocketmq.testsupport.V5Clients;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code ClientConfigurationBuilder.setRequestTimeout} maps a gRPC deadline to a
 * timeout/deadline exception instead of hanging. Sends a large body against the real proxy with a
 * 500ms deadline.
 */
class RequestTimeoutTest {

  private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(500);

  @Test
  void shortRequestTimeoutFailsFastInsteadOfHanging() throws Exception {
    ClientConfiguration configuration =
        ClientConfiguration.newBuilder()
            .setEndpoints(Addresses.PROXY)
            .setRequestTimeout(REQUEST_TIMEOUT)
            .build();
    assertEquals(REQUEST_TIMEOUT, configuration.getRequestTimeout());

    try (Producer producer = V5Clients.producer(Topics.TOPIC_NORMAL, configuration)) {
      ClientServiceProvider provider = ClientServiceProvider.loadService();
      byte[] body = new byte[2 * 1024 * 1024];
      Arrays.fill(body, (byte) 'A');
      Message message =
          provider
              .newMessageBuilder()
              .setTopic(Topics.TOPIC_NORMAL)
              .setKeys("request-timeout-" + UUID.randomUUID())
              .setBody(body)
              .build();

      Exception thrown = assertThrows(Exception.class, () -> producer.send(message));
      assertTrue(isTimeoutRelated(thrown), "Expected a timeout/deadline exception, got: " + thrown);
    }
  }

  private static boolean isTimeoutRelated(Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      String text =
          String.valueOf(current.getClass().getName()) + " " + String.valueOf(current.getMessage());
      if (containsIgnoreCase(text, "timeout") || containsIgnoreCase(text, "deadline")) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsIgnoreCase(String text, String needle) {
    return text.toLowerCase().contains(needle.toLowerCase());
  }
}
