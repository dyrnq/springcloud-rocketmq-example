package com.dyrnq.sca.rocketmq;

import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeType;

public class MyCustomMessageConverter  extends AbstractMessageConverter {

    public MyCustomMessageConverter() {
        super(new MimeType("text", "plain"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return (String.class.equals(clazz));
    }

    @Override
    protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object conversionHint) {
        Object payload = message.getPayload();
        return (payload instanceof String ? payload : new String((byte[]) payload));
    }
}
