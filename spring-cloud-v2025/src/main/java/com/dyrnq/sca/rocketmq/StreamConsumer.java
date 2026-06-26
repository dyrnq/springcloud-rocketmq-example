package com.dyrnq.sca.rocketmq;

import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

@Slf4j
@Configuration
public class StreamConsumer {
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
