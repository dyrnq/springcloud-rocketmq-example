package com.dyrnq.rocketmq.testsupport;

import java.util.UUID;

/**
 * Producer / consumer / FIFO group names used by tests.
 *
 * <p>{@link #uniquePush()} and {@link #uniqueSimple()} return fresh per-test names so repeated runs
 * don't see stale offsets. Stable names are also exported for tests that want to assert on a known
 * group (e.g. demo flow assertions).
 */
public final class Groups {

  /** Default producer group for sbs / sbs-v5 / client / client-v5 modules. */
  public static final String PRODUCER_DEFAULT = "test-producer";

  /** Stable push-consumer group used by demo / sbs / sbs-v5 / spring-cloud-v* code. */
  public static final String CONSUMER_PUSH_DEMO = "demo-group";

  /** Stable push-consumer group used by the legacy spring-cloud-v* raw consumer. */
  public static final String CONSUMER_PUSH_LEGACY = "your_consumer_group";

  /** Stable simple-consumer group used by client-v5 examples. */
  public static final String CONSUMER_SIMPLE = "yourConsumerGroup";

  /** FIFO message group (V5 setMessageGroup). */
  public static final String MESSAGE_GROUP_FIFO = "group1";

  /**
   * A unique consumer group per call; pass it to a {@code PushConsumer} / {@code SimpleConsumer}.
   */
  public static String uniquePush() {
    return "push-" + shortTag();
  }

  /** A unique consumer group per call; use for {@code SimpleConsumer}. */
  public static String uniqueSimple() {
    return "simple-" + shortTag();
  }

  /** A unique producer group per call. */
  public static String uniqueProducer() {
    return "producer-" + shortTag();
  }

  private static String shortTag() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private Groups() {}
}
