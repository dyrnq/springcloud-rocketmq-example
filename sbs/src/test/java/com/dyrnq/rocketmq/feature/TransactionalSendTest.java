package com.dyrnq.rocketmq.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dyrnq.rocketmq.sbs.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Addresses;
import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.MessageFactory;
import com.dyrnq.rocketmq.testsupport.Topics;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the V4 transaction message flow for the sbs module: a {@link TransactionMQProducer}
 * commits in {@code executeLocalTransaction}, and the committed message is delivered to a push
 * consumer on {@link Topics#TOPIC_TRANSACTION}.
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class TransactionalSendTest {

  private TransactionMQProducer producer;
  private DefaultMQPushConsumer consumer;
  private String bodyMarker;
  private final AtomicBoolean consumed = new AtomicBoolean(false);

  @BeforeEach
  void setUp() throws Exception {
    bodyMarker = "sbs-tx-" + UUID.randomUUID();
    AclClientRPCHook acl = new AclClientRPCHook(new SessionCredentials("ak", "sk"));

    producer = new TransactionMQProducer(Groups.uniqueProducer(), acl);
    producer.setNamesrvAddr(Addresses.NAMESERVER);
    producer.setTransactionListener(
        new TransactionListener() {
          @Override
          public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
            return LocalTransactionState.COMMIT_MESSAGE;
          }

          @Override
          public LocalTransactionState checkLocalTransaction(MessageExt msg) {
            return LocalTransactionState.COMMIT_MESSAGE;
          }
        });
    producer.start();

    consumer =
        new DefaultMQPushConsumer(
            Groups.uniquePush(), acl, new AllocateMessageQueueAveragely(), true, null);
    consumer.setNamesrvAddr(Addresses.NAMESERVER);
    consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    consumer.subscribe(Topics.TOPIC_TRANSACTION, "*");
    consumer.registerMessageListener(
        (MessageListenerConcurrently)
            (msgs, context) -> {
              for (MessageExt m : msgs) {
                if (bodyMarker.equals(new String(m.getBody(), StandardCharsets.UTF_8))) {
                  consumed.set(true);
                }
              }
              return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
    consumer.start();
  }

  @AfterEach
  void tearDown() {
    if (consumer != null) {
      consumer.shutdown();
    }
    if (producer != null) {
      producer.shutdown();
    }
  }

  @Test
  void committedTransactionMessageIsDelivered() throws Exception {
    Message message =
        new Message(Topics.TOPIC_TRANSACTION, bodyMarker.getBytes(StandardCharsets.UTF_8));
    message.setTags(MessageFactory.DEFAULT_TAG);

    TransactionSendResult result = producer.sendMessageInTransaction(message, null);
    assertNotNull(result, "TransactionSendResult should not be null");
    assertEquals(
        LocalTransactionState.COMMIT_MESSAGE,
        result.getLocalTransactionState(),
        "local transaction should commit");

    long deadline = System.currentTimeMillis() + 30_000;
    while (!consumed.get() && System.currentTimeMillis() < deadline) {
      Thread.sleep(200);
    }
    assertTrue(consumed.get(), "committed transaction message should be delivered: " + bodyMarker);
  }
}
