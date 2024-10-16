package com.dyrnq.rocketmq.sbsv5;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.apache.rocketmq.client.common.Pair;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.apache.rocketmq.client.core.RocketMQTransactionChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProducerRunner implements CommandLineRunner {
    private final RocketMQClientTemplate rocketMQClientTemplate;


    @Value("${demo.rocketmq.fifo-topic}")
    private String fifoTopic;

    @Value("${demo.rocketmq.normal-topic}")
    private String normalTopic;

    @Value("${demo.rocketmq.delay-topic}")
    private String delayTopic;

    @Value("${demo.rocketmq.trans-topic}")
    private String transTopic;

    @Value("${demo.rocketmq.message-group}")
    private String messageGroup;

    @Override
    public void run(String... args) throws Exception {
        testAsyncSendMessage();
        testSendDelayMessage();
        testSendFIFOMessage();
        testSendNormalMessage();
        testSendTransactionMessage();
    }

    void testAsyncSendMessage() {

        CompletableFuture<SendReceipt> future0 = new CompletableFuture<>();
        CompletableFuture<SendReceipt> future1 = new CompletableFuture<>();
        CompletableFuture<SendReceipt> future2 = new CompletableFuture<>();
        ExecutorService sendCallbackExecutor = Executors.newCachedThreadPool();

        future0.whenCompleteAsync((sendReceipt, throwable) -> {
            if (null != throwable) {
                log.error("Failed to send message", throwable);
                return;
            }
            log.info("Send message successfully, messageId={}", sendReceipt.getMessageId());
        }, sendCallbackExecutor);

        future1.whenCompleteAsync((sendReceipt, throwable) -> {
            if (null != throwable) {
                log.error("Failed to send message", throwable);
                return;
            }
            log.info("Send message successfully, messageId={}", sendReceipt.getMessageId());
        }, sendCallbackExecutor);

        future2.whenCompleteAsync((sendReceipt, throwable) -> {
            if (null != throwable) {
                log.error("Failed to send message", throwable);
                return;
            }
            log.info("Send message successfully, messageId={}", sendReceipt.getMessageId());
        }, sendCallbackExecutor);

        CompletableFuture<SendReceipt> completableFuture0 = rocketMQClientTemplate.asyncSendNormalMessage(normalTopic, new UserMessage()
                .setId(1).setUserName("name").setUserAge((byte) 3), future0);
        System.out.printf("normalSend to topic %s sendReceipt=%s %n", normalTopic, completableFuture0);

        CompletableFuture<SendReceipt> completableFuture1 = rocketMQClientTemplate.asyncSendFifoMessage(fifoTopic, "fifo message",
                messageGroup, future1);
        System.out.printf("fifoSend to topic %s sendReceipt=%s %n", fifoTopic, completableFuture1);

        CompletableFuture<SendReceipt> completableFuture2 = rocketMQClientTemplate.asyncSendDelayMessage(delayTopic,
                "delay message".getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(10), future2);
        System.out.printf("delaySend to topic %s sendReceipt=%s %n", delayTopic, completableFuture2);
    }

    void testSendDelayMessage() {
        SendReceipt sendReceipt = rocketMQClientTemplate.syncSendDelayMessage(delayTopic, new UserMessage()
                .setId(1).setUserName("name").setUserAge((byte) 3), Duration.ofSeconds(10));
        System.out.printf("delaySend to topic %s sendReceipt=%s %n", delayTopic, sendReceipt);

        sendReceipt = rocketMQClientTemplate.syncSendDelayMessage(delayTopic, MessageBuilder.
                withPayload("test message".getBytes()).build(), Duration.ofSeconds(30));
        System.out.printf("delaySend to topic %s sendReceipt=%s %n", delayTopic, sendReceipt);

        sendReceipt = rocketMQClientTemplate.syncSendDelayMessage(delayTopic, "this is my message",
                Duration.ofSeconds(60));
        System.out.printf("delaySend to topic %s sendReceipt=%s %n", delayTopic, sendReceipt);

        sendReceipt = rocketMQClientTemplate.syncSendDelayMessage(delayTopic, "byte messages".getBytes(StandardCharsets.UTF_8),
                Duration.ofSeconds(90));
        System.out.printf("delaySend to topic %s sendReceipt=%s %n", delayTopic, sendReceipt);
    }

    void testSendFIFOMessage() {
        SendReceipt sendReceipt = rocketMQClientTemplate.syncSendFifoMessage(fifoTopic, new UserMessage()
                .setId(1).setUserName("name").setUserAge((byte) 3), messageGroup);
        System.out.printf("fifoSend to topic %s sendReceipt=%s %n", fifoTopic, sendReceipt);

        sendReceipt = rocketMQClientTemplate.syncSendFifoMessage(fifoTopic, MessageBuilder.
                withPayload("test message".getBytes()).build(), messageGroup);
        System.out.printf("fifoSend to topic %s sendReceipt=%s %n", fifoTopic, sendReceipt);

        sendReceipt = rocketMQClientTemplate.syncSendFifoMessage(fifoTopic, "fifo message", messageGroup);
        System.out.printf("fifoSend to topic %s sendReceipt=%s %n", fifoTopic, sendReceipt);

        sendReceipt = rocketMQClientTemplate.syncSendFifoMessage(fifoTopic, "byte message".getBytes(StandardCharsets.UTF_8), messageGroup);
        System.out.printf("fifoSend to topic %s sendReceipt=%s %n", fifoTopic, sendReceipt);
    }

    void testSendNormalMessage() {
        SendReceipt sendReceipt = rocketMQClientTemplate.syncSendNormalMessage(normalTopic, new UserMessage()
                .setId(1).setUserName("name").setUserAge((byte) 3));
        System.out.printf("normalSend to topic %s sendReceipt=%s %n", normalTopic, sendReceipt);

        sendReceipt = rocketMQClientTemplate.syncSendNormalMessage(normalTopic, "normal message");
        System.out.printf("normalSend to topic %s sendReceipt=%s %n", normalTopic, sendReceipt);

        sendReceipt = rocketMQClientTemplate.syncSendNormalMessage(normalTopic, "byte message".getBytes(StandardCharsets.UTF_8));
        System.out.printf("normalSend to topic %s sendReceipt=%s %n", normalTopic, sendReceipt);

        sendReceipt = rocketMQClientTemplate.syncSendNormalMessage(normalTopic, MessageBuilder.
                withPayload("test message".getBytes()).build());
        System.out.printf("normalSend to topic %s sendReceipt=%s %n", normalTopic, sendReceipt);
    }

    void testSendTransactionMessage() throws ClientException {
        Pair<SendReceipt, Transaction> pair;
        SendReceipt sendReceipt;
        try {
            pair = rocketMQClientTemplate.sendMessageInTransaction(transTopic, MessageBuilder.
                    withPayload(new UserMessage()
                            .setId(1).setUserName("name").setUserAge((byte) 3)).setHeader("OrderId", 1).build());
        } catch (ClientException e) {
            throw new RuntimeException(e);
        }
        sendReceipt = pair.getSendReceipt();
        System.out.printf("transactionSend to topic %s sendReceipt=%s %n", transTopic, sendReceipt);
        Transaction transaction = pair.getTransaction();
        // executed local transaction
        if (doLocalTransaction(1)) {
            transaction.commit();
        } else {
            transaction.rollback();
        }
    }

    boolean doLocalTransaction(int number) {
        log.info("execute local transaction");
        return number > 0;
    }

    @RocketMQTransactionListener
    static class TransactionListenerImpl implements RocketMQTransactionChecker {
        @Override
        public TransactionResolution check(MessageView messageView) {
            if (Objects.nonNull(messageView.getProperties().get("OrderId"))) {
                log.info("Receive transactional message check, message={}", messageView);
                return TransactionResolution.COMMIT;
            }
            log.info("rollback transaction");
            return TransactionResolution.ROLLBACK;
        }
    }

}
