package com.dyrnq.rocketmq.sbsv5;

import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
@Profile("!test")
public class V5PushConsumerRunner implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(V5PushConsumerRunner.class);

  @Override
  public void run(String... args) {}

  @Service
  @RocketMQMessageListener(consumerGroup = "demo-group", topic = "normalTopic")
  public class MyConsumer1 implements RocketMQListener {
    @Override
    public ConsumeResult consume(MessageView messageView) {
      log.info("received message: {}", messageView);
      return ConsumeResult.SUCCESS;
    }
  }
}
