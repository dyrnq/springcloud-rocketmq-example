package com.dyrnq.sca.rocketmq;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageConst;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
public class StreamProducerRunner implements ApplicationRunner {
    private static final String CONSUMER_EVENT_OUT_0 = "consumerEvent-out-0";
    private static final String MESSAGE_TAG = "test";
    private final StreamBridge streamBridge;

    public static String genFixedString(int charCount) {
        byte[] byteArray = new byte[charCount];

        for (int i = 0; i < byteArray.length; i++) {
            byteArray[i] = 'A';
        }

        String str = new String(byteArray);
        return str;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        int index = 1;
        long begin = System.currentTimeMillis();
        long end = 0L;
        double avg = 0;
//        log.info("streamBridge.isAsync():{}", streamBridge.isAsync());
//        streamBridge.setAsync(true);
        while (true) {

//            MessageInfo message = MessageInfo.builder().build();
//            String body = UUID.randomUUID().toString();
//            body = genFixedString(5000);
//            message.setBody(body);
//            message.setIndex(index);
//
//            // 创建 Spring Message 对象
//            Message<MessageInfo> info = MessageBuilder.withPayload(message)
//                    .setHeader(MessageConst.PROPERTY_TAGS, MESSAGE_TAG) //  <1> 设置 Tag
//                    .build();


            String myStr = """
                    {"body":"%s","index":%d}
                    """;

            String outStr = String.format(myStr, "", index).trim();
            int increase = 1;
            int wantBodySize = 5096;

            if (index % 7 == 0) {
                wantBodySize = 5097;
            }
            while (outStr.getBytes().length < wantBodySize) {
                outStr = String.format(myStr, genFixedString(increase), index).trim();
                increase++;
            }
//            log.warn("bytes length:{} ", outStr.getBytes().length);

            Message<String> info = MessageBuilder.withPayload(outStr)
                    .setHeader(MessageConst.PROPERTY_TAGS, MESSAGE_TAG)
                    .build();
            streamBridge.send(CONSUMER_EVENT_OUT_0, info);

            index++;
            if (index % 1000 == 0) {
                end = System.currentTimeMillis();
                avg = (double) Math.round((end - begin) * 100.0 / index) / 100.0;
                log.info("index {}, use time {}, avg {} ", index, (end - begin) + "", avg);
            }
            //ThreadUtils.sleep(Duration.ofMillis(2000));

            if (index > 10 * 10) {
                break;
            }
        }
        end = System.currentTimeMillis();
        avg = (double) Math.round((end - begin) * 100.0 / index) / 100.0;
        log.info("over index {}, use time {}, avg {} ", index, (end - begin) + "", avg);
    }
}
