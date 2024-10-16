package com.dyrnq.rocketmq.sbsv5;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
@Slf4j
public class V5PushConsumerRunner implements CommandLineRunner {
    @Override
    public void run(String... args) {
    }

    @Service
    @RocketMQMessageListener(consumerGroup = "demo-group", topic = "normalTopic")
    public class MyConsumer1 implements RocketMQListener {
        @Override
        public ConsumeResult consume(MessageView messageView) {
            log.info("received message: {}" , messageView);
            return ConsumeResult.SUCCESS;
        }
    }
}
