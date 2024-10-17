package com.dyrnq.rocketmq.delay;

import com.dyrnq.rocketmq.ClientCreater;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

/**
 * 固定延迟级别的定时消息，建议用户使用更自由精确的TimerMessageProducer
 * 延迟消息通过设置延迟等级来实现
 * 等级与时间对应关系：
 * 1s、 5s、 10s、 30s、 1m、 2m、 3m、 4m、 5m、 6m、 7m、 8m、 9m、 10m、 20m、 30m、 1h、 2h；
 * 1    2    3     4     5    6   7    8   9    10   11   12  13   14    15    16   17   18
 */
public class DelayMessageProducer {

    /**
     * topic名称
     */
    private static final String TOPIC_NAME = "delayTopic";

    /**
     * 生产者组名称
     */
    private static final String GROUP_NAME = "group2";

    public static void main(String[] args) throws Exception {
        // 创建消息生产者
        DefaultMQProducer producer = ClientCreater.createProducer(GROUP_NAME);

        int totalMessagesToSend = 5;
        int delayLevel = 19;
        for (int i = 0; i < totalMessagesToSend; i++) {
            Message message = new Message(TOPIC_NAME, ("Hello scheduled message " + i).getBytes());
            message.setTags("DELAY=" + delayLevel);
            // 设置消息延迟等级
            message.setDelayTimeLevel(delayLevel);
            // 发送消息
            SendResult sendResult = producer.send(message);
            System.out.println("sendResult = " + sendResult);
        }

        producer.shutdown();
    }
}
