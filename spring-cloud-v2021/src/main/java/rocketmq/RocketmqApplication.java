package rocketmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.converter.ObjectStringMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.converter.MessageConverter;

@SpringBootApplication
@Slf4j
public class RocketmqApplication {

    public static void main(String[] args) {
        SpringApplication.run(RocketmqApplication.class, args);
    }

    @Bean
    public MessageConverter customMessageConverter() {
        return new ObjectStringMessageConverter();
    }
}
