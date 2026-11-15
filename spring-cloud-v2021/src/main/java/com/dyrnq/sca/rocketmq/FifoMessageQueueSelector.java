package com.dyrnq.sca.rocketmq;

import java.util.List;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

/**
 * Hash-by-keys message queue selector used by the FIFO output binding.
 *
 * <p>The binder passes the Spring {@link MessageHeaders} as the selector argument, but the message
 * converter already maps the {@code KEYS} header onto {@link Message#getKeys()}, which is the
 * primary key source here. Fallback reads the {@code KEYS} header directly.
 */
@Component("fifoMessageQueueSelector")
public class FifoMessageQueueSelector implements MessageQueueSelector {

  @Override
  public MessageQueue select(List<MessageQueue> queues, Message message, Object arg) {
    String key = message.getKeys();
    if (key == null && arg instanceof MessageHeaders headers) {
      Object headerValue =
          headers.get(org.apache.rocketmq.common.message.MessageConst.PROPERTY_KEYS);
      if (headerValue != null) {
        key = headerValue.toString();
      }
    }
    int index = key == null ? 0 : Math.floorMod(key.hashCode(), queues.size());
    return queues.get(index);
  }
}
