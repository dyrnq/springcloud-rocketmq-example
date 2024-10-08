package com.dyrnq.sca.rocketmq;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.rocketmq.common.message.MessageConst;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@AllArgsConstructor
@Slf4j
public class StreamProducerRunner implements ApplicationRunner {
    private static final String CONSUMER_EVENT_OUT_0 = "consumerEvent-out-0";
    private static final String MESSAGE_TAG = "test";
    private final StreamBridge streamBridge;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("run");


        List<Message<MessageInfo>> messageInfoList = new ArrayList<>();

        int index = 1;
        while (true) {

            MessageInfo message = MessageInfo.builder().build();
            String body = UUID.randomUUID().toString();
            message.setBody(body);
            message.setIndex(index);

            // 创建 Spring Message 对象
            Message<MessageInfo> info = MessageBuilder.withPayload(message)
                    .setHeader(MessageConst.PROPERTY_TAGS, MESSAGE_TAG) //  <1> 设置 Tag
                    .build();

            streamBridge.send(CONSUMER_EVENT_OUT_0, info);

            index++;
            ThreadUtils.sleep(Duration.ofMillis(2000));
        }

    }
}
