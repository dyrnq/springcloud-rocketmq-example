package com.dyrnq.rocketmq.simple;


import com.dyrnq.rocketmq.ClientCreater;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;

/**
 * Description: 消息消费者(push)
 */
public class SimplePushConsumer {


    /**
     * topic名称
     */
    private static final String TOPIC_NAME = "topic_a";

    /**
     * 消费者组名称
     */
    private static final String GROUP_NAME = "group";

    public static void main(String[] args) throws Exception {
        // 创建消息消费者
        DefaultMQPushConsumer pushConsumer = ClientCreater.createPushConsumer(GROUP_NAME);
        // 订阅topic
        pushConsumer.subscribe(TOPIC_NAME, "*");
        // 注册回调实现类来处理从broker拉取回来的消息
        pushConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            // 消息处理逻辑
            System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), msgs);
            // 标记该消息已经被成功消费
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        // 启动消费者实例
        System.out.printf("Consumer Started.%n");
        pushConsumer.start();
        System.in.read();
        pushConsumer.shutdown();
    }
}
