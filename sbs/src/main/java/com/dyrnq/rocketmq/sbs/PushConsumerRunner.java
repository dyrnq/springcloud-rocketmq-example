package com.dyrnq.rocketmq.sbs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
@RequiredArgsConstructor
@Slf4j
public class PushConsumerRunner implements CommandLineRunner {
    @Override
    public void run(String... args) {
    }

    @Service
    @RocketMQMessageListener(topic = "demo-topic", consumerGroup = "demo-group")
    public static class DemoConsumer implements RocketMQListener<String> {
        public void onMessage(String message) {
            log.info("received message: {}", message);
        }
    }
}
