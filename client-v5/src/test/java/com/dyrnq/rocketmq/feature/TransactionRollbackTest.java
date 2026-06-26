package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import com.dyrnq.rocketmq.testsupport.V5Clients;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.apis.producer.TransactionChecker;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies V5 transaction rollback semantics: after {@link Transaction#commit rollback()}, the
 * broker discards the message — a push consumer subscribed on the topic must NOT receive it.
 *
 * <p>The companion {@link TransactionalSendTest} only covers the commit path.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionRollbackTest {

  private static final Logger log = LoggerFactory.getLogger(TransactionRollbackTest.class);

  private Producer producer;
  private PushConsumer consumer;
  private final java.util.List<String> receivedBodies = new CopyOnWriteArrayList<>();

  @BeforeAll
  void setUp() throws Exception {
    // Transactional producer with a permissive checker (we commit before the checker ever fires,
    // so its resolution doesn't matter — but the broker still requires one to be set).
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    TransactionChecker checker =
        messageView -> {
          log.info("Transaction check, messageId={}", messageView.getMessageId());
          return TransactionResolution.COMMIT;
        };
    producer =
        provider
            .newProducerBuilder()
            .setClientConfiguration(
                org.apache.rocketmq.client.apis.ClientConfiguration.newBuilder()
                    .setEndpoints(com.dyrnq.rocketmq.testsupport.Addresses.PROXY)
                    .build())
            .setTopics(Topics.TOPIC_TRANSACTION)
            .setTransactionChecker(checker)
            .build();
    consumer =
        V5Clients.pushConsumer(
            Groups.uniquePush(),
            Topics.TOPIC_TRANSACTION,
            "yourMessageTagA",
            (MessageView messageView) -> {
              String body = com.dyrnq.rocketmq.testsupport.MessageViewBytes.toString(messageView);
              log.info("Received push body={}", body);
              receivedBodies.add(body);
              return ConsumeResult.SUCCESS;
            });
  }

  @AfterAll
  void tearDown() throws Exception {
    if (consumer != null) consumer.close();
    if (producer != null) producer.close();
  }

  @Test
  void rolledBackMessageIsNeverDelivered() throws Exception {
    String bodyMarker = "v5-tx-rollback-" + UUID.randomUUID();
    Transaction tx = producer.beginTransaction();
    assertNotNull(tx, "Transaction should not be null");

    Message message =
        ClientServiceProvider.loadService()
            .newMessageBuilder()
            .setTopic(Topics.TOPIC_TRANSACTION)
            .setTag("yourMessageTagA")
            .setKeys("v5-tx-rollback-key-" + UUID.randomUUID())
            .setBody(bodyMarker.getBytes(StandardCharsets.UTF_8))
            .build();
    SendReceipt receipt = producer.send(message, tx);
    assertNotNull(receipt, "SendReceipt should not be null");

    // Rollback — broker must discard the half-committed message.
    tx.rollback();

    // Wait 5 s; the message must never reach the push consumer.
    Thread.sleep(5_000);

    assertTrue(
        !receivedBodies.contains(bodyMarker),
        "After rollback, push consumer must NOT receive message with body "
            + bodyMarker
            + "; received: "
            + receivedBodies);
  }
}
