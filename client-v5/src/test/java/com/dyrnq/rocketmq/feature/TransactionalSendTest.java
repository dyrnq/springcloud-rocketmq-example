package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.testsupport.Groups;
import com.dyrnq.rocketmq.testsupport.Topics;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies transactional message send via the V5 gRPC client: begin -> send -> commit.
 *
 * <p>Replaces the body of
 * {@code org.apache.rocketmq.client.java.example.ProducerTransactionMessageExample.main}.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionalSendTest {

    private static final Logger log = LoggerFactory.getLogger(TransactionalSendTest.class);

    private Producer producer;

    @BeforeAll
    void startProducer() throws Exception {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        org.apache.rocketmq.client.apis.ClientConfiguration clientConfiguration =
            org.apache.rocketmq.client.apis.ClientConfiguration.newBuilder()
                .setEndpoints(com.dyrnq.rocketmq.testsupport.Addresses.PROXY)
                .build();
        TransactionChecker checker = messageView -> {
            log.info("Transaction check, messageId={}", messageView.getMessageId());
            return TransactionResolution.COMMIT;
        };
        producer = provider.newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics(Topics.TOPIC_TRANSACTION)
            .setTransactionChecker(checker)
            .build();
    }

    @AfterAll
    void stopProducer() throws Exception {
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    void sendTransactionalMessageAndCommit() throws Exception {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        Transaction transaction = producer.beginTransaction();
        assertNotNull(transaction, "Transaction should not be null");

        byte[] body = "v5-tx-".getBytes(StandardCharsets.UTF_8);
        Message message = provider.newMessageBuilder()
            .setTopic(Topics.TOPIC_TRANSACTION)
            .setTag("yourMessageTagA")
            .setKeys("v5-tx-key-" + java.util.UUID.randomUUID())
            .setBody(body)
            .build();
        SendReceipt receipt = producer.send(message, transaction);
        assertNotNull(receipt, "SendReceipt should not be null");
        assertNotNull(receipt.getMessageId(), "MessageId should not be null");

        transaction.commit();
        // After commit, the local transaction object is no longer usable; we just
        // verified it didn't throw — that's the success criterion for commit.
        assertTrue(true, "commit() should not throw");
    }
}
