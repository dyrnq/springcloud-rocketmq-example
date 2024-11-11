package com.dyrnq.rocketmq.simple;

import com.dyrnq.rocketmq.ClientCreater;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;

public class SimpleSyncProducer {

    /**
     * topic名称
     */
    private static final String TOPIC_NAME = "topic_a";

    /**
     * 生产者组名称
     */
    private static final String GROUP_NAME = "group";


    public static void main(String[] args) throws Exception {
        // 创建消息生产者
        DefaultMQProducer producer = ClientCreater.createProducer(GROUP_NAME);
        // 创建消息实例，设置topic和消息内容
        Message msg = new Message(TOPIC_NAME, "yourMessageTagA", "Hello RocketMQ.".getBytes(StandardCharsets.UTF_8));
        // 发送消息
        SendResult sendResult = producer.send(msg, 3000);
        System.out.println(sendResult + ":" + new String(msg.getBody()));
        producer.shutdown();
    }
}
