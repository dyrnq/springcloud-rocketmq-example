package com.dyrnq.sca.rocketmq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.converter.MessageConverter;

@SpringBootApplication
public class RocketmqApplication {

  public static void main(String[] args) {
    SpringApplication.run(RocketmqApplication.class, args);
  }

  @Bean
  public MessageConverter customMessageConverter() {
    return new org.springframework.cloud.stream.converter.ObjectStringMessageConverter();
  }
}
