package com.dyrnq.rocketmq.sbs;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ProducerRunner implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(ProducerRunner.class);

  private final RocketMQTemplate rocketMQTemplate;

  @Value("${rocketmq.producer.simple.demo.topic}")
  private String topic;

  public ProducerRunner(RocketMQTemplate rocketMQTemplate) {
    this.rocketMQTemplate = rocketMQTemplate;
  }

  @Override
  public void run(String... args) throws Exception {
    SendResult sendResult = rocketMQTemplate.syncSend(topic, "Hello, World!");
    log.info("{}", sendResult);
  }
}
