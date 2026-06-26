package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.sbsv5.RocketmqApplication;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.common.Pair;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies transactional send via {@code sendMessageInTransaction}
 * (replaces {@code ProducerRunner.testSendTransactionMessage()}).
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class TransactionalSendTest {

    @Autowired
    RocketMQClientTemplate rocketMQClientTemplate;

    @Test
    void sendMessageInTransaction() throws Exception {
        Pair<SendReceipt, Transaction> pair = rocketMQClientTemplate.sendMessageInTransaction(
            Topics.TOPIC_TRANSACTION,
            MessageBuilder.withPayload("sbs-v5-tx").setHeader("OrderId", 1).build());
        assertNotNull(pair, "Pair should not be null");
        assertNotNull(pair.getSendReceipt(), "SendReceipt should not be null");
        Transaction tx = pair.getTransaction();
        assertNotNull(tx, "Transaction should not be null");
        // Replicates the original doLocalTransaction(n>0) => commit.
        tx.commit();
    }
}
