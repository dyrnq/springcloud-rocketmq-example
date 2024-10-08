package com.dyrnq.sca.rocketmq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class StreamConsumerRunner implements ApplicationRunner {

    @Value("${spring.cloud.stream.rocketmq.binder.name-server}")
    private String rocketmqNameSrv;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("your_consumer_group");
        consumer.setNamesrvAddr(rocketmqNameSrv);
        consumer.subscribe("demo", "*");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                // 获取消息内容
                String messageBody = new String(msg.getBody());
                // 获取消息 ID
                String messageId = msg.getMsgId();
                // 获取 Broker 名称
                String brokerName = msg.getBrokerName();
                // 获取 Queue ID
                int queueId = msg.getQueueId();


                log.info("storeHost: {}, Broker: {} , Queue ID: {} , Message ID: {} , Received message: {}",  msg.getStoreHost().toString(), brokerName, queueId, messageId, messageBody);
            }
            return null; // 返回消费状态
        });

        consumer.start();

    }
}
