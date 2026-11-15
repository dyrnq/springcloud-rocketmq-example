package com.dyrnq.rocketmq.sbs;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
@Profile("!test")
public class PushConsumerRunner implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(PushConsumerRunner.class);

  @Override
  public void run(String... args) {}

  @Service
  @RocketMQMessageListener(topic = "demo-topic", consumerGroup = "demo-group")
  public static class DemoConsumer implements RocketMQListener<String> {
    public void onMessage(String message) {
      log.info("received message: {}", message);
    }
  }
}
