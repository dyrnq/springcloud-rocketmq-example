package com.dyrnq.rocketmq.testsupport;

import java.time.Duration;
import java.util.Collections;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;

/**
 * Builders for V5 gRPC clients ({@link Producer}, {@link PushConsumer}, {@link SimpleConsumer})
 * wired to the broker proxy at {@link Addresses#PROXY}.
 *
 * <p>Each method returns an open client; the caller is responsible for closing it in
 * {@code @AfterAll}. The builders pre-set defaults that match the existing V5 test conventions
 * (15-second long-poll, tag-based subscription, etc.).
 */
public final class V5Clients {

  private V5Clients() {}

  /**
   * Build a {@link Producer} pre-wired to {@code Addresses.PROXY} with the topic pre-fetched
   * (recommended for throughput but optional).
   */
  public static Producer producer(String topic) throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    return provider
        .newProducerBuilder()
        .setClientConfiguration(clientConfiguration())
        .setTopics(topic)
        .build();
  }

  /**
   * Build a {@link PushConsumer} subscribed to a single topic with tag-based filtering. The
   * listener is invoked on the V5 client's push thread.
   */
  public static PushConsumer pushConsumer(
      String group, String topic, String tag, MessageListener listener) throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    return provider
        .newPushConsumerBuilder()
        .setClientConfiguration(clientConfiguration())
        .setConsumerGroup(group)
        .setSubscriptionExpressions(
            Collections.singletonMap(topic, new FilterExpression(tag, FilterExpressionType.TAG)))
        .setMessageListener(listener)
        .build();
  }

  /**
   * Build a {@link SimpleConsumer} subscribed to a single topic with tag-based filtering and a
   * 15-second long-poll.
   */
  public static SimpleConsumer simpleConsumer(String group, String topic, String tag)
      throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    return provider
        .newSimpleConsumerBuilder()
        .setClientConfiguration(clientConfiguration())
        .setConsumerGroup(group)
        .setAwaitDuration(Duration.ofSeconds(15))
        .setSubscriptionExpressions(
            Collections.singletonMap(topic, new FilterExpression(tag, FilterExpressionType.TAG)))
        .build();
  }

  private static ClientConfiguration clientConfiguration() {
    return ClientConfiguration.newBuilder().setEndpoints(Addresses.PROXY).build();
  }
}
