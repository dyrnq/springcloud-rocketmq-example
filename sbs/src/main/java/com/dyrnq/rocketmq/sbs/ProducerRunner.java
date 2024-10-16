package com.dyrnq.rocketmq.sbs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProducerRunner implements CommandLineRunner {
    private final RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.producer.simple.demo.topic}")
    private String topic;

    @Override
    public void run(String... args) throws Exception {
        SendResult sendResult = rocketMQTemplate.syncSend(topic, "Hello, World!");
        log.info("{}", sendResult);
    }
}
