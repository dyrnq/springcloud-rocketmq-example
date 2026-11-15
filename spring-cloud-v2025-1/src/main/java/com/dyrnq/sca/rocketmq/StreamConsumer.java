package com.dyrnq.sca.rocketmq;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

@Configuration
public class StreamConsumer {
  private static final Logger log = LoggerFactory.getLogger(StreamConsumer.class);

  //    @Bean
  //    Consumer<MessageInfo> consumerEvent() {
  //
  //        return message -> {
  //            log.info("It Received message: {}", message);
  //        };
  //    }
  @Bean
  Consumer<Message<String>> consumerEvent() {

    return message -> {
      log.info("It Received message: {}", message);
    };
  }
}
