package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.MessageViewBytes;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.rocketmq.client.apis.ClientConfiguration;
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
 * Verifies V5 SQL92 consumer-side filtering via {@link FilterExpressionType#SQL92}. Only messages
 * whose {@code sql92FilterValue} user property is greater than 5 are delivered.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Sql92FilterTest {

  private static final Logger log = LoggerFactory.getLogger(Sql92FilterTest.class);
  private static final String FILTER_PROPERTY = "sql92FilterValue";
  private static final String FILTER_EXPRESSION = FILTER_PROPERTY + " > 5";

  private Producer producer;
  private SimpleConsumer consumer;

  @BeforeAll
  void setUp() throws Exception {
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    ClientConfiguration configuration =
        ClientConfiguration.newBuilder().setEndpoints(Addresses.PROXY).build();
    producer =
        provider
            .newProducerBuilder()
            .setClientConfiguration(configuration)
            .setTopics(Topics.TOPIC_NORMAL)
            .build();
    consumer =
        provider
            .newSimpleConsumerBuilder()
            .setClientConfiguration(configuration)
            .setConsumerGroup(Groups.uniqueSimple())
            .setAwaitDuration(Duration.ofSeconds(15))
            .setSubscriptionExpressions(
                Collections.singletonMap(
                    Topics.TOPIC_NORMAL,
                    new FilterExpression(FILTER_EXPRESSION, FilterExpressionType.SQL92)))
            .build();
    Thread.sleep(2000);
  }

  @AfterAll
  void tearDown() throws Exception {
    if (consumer != null) {
      consumer.close();
    }
    if (producer != null) {
      producer.close();
    }
  }

  @Test
  void sql92FilterOnlyDeliversMatchingMessages() throws Exception {
    String matchingMarker = "v5-sql-match-" + UUID.randomUUID();
    String nonMatchingMarker = "v5-sql-nomatch-" + UUID.randomUUID();
    send(matchingMarker, "7");
    send(nonMatchingMarker, "3");

    boolean matchingReceived = false;
    boolean nonMatchingReceived = false;
    long deadline = System.currentTimeMillis() + 60_000;
    while (System.currentTimeMillis() < deadline) {
      List<MessageView> messages = consumer.receive(16, Duration.ofSeconds(15));
      for (MessageView view : messages) {
        String body = MessageViewBytes.toString(view);
        log.info("Received body={}", body);
        if (body.equals(matchingMarker)) {
          matchingReceived = true;
        } else if (body.equals(nonMatchingMarker)) {
          nonMatchingReceived = true;
        }
        consumer.ack(view);
      }
      if (matchingReceived) {
        break;
      }
    }

    assertTrue(matchingReceived, "SQL92-matching message should be delivered");
    assertTrue(!nonMatchingReceived, "SQL92 non-matching message should be filtered out");
  }

  private void send(String bodyMarker, String filterValue) throws Exception {
    Message message =
        ClientServiceProvider.loadService()
            .newMessageBuilder()
            .setTopic(Topics.TOPIC_NORMAL)
            .setBody(bodyMarker.getBytes(StandardCharsets.UTF_8))
            .addProperty(FILTER_PROPERTY, filterValue)
            .build();
    SendReceipt receipt = producer.send(message);
    assertNotNull(receipt, "SendReceipt should not be null");
  }
}
