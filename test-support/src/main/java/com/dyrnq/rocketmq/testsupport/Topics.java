package com.dyrnq.rocketmq.testsupport;

/**
 * Centralised topic names for RocketMQ POC tests.
 *
 * <p>The on-broker topics pre-created by {@code scripts/run-containers-controller.sh} are
 * {@code delayTopic}, {@code fifoTopic}, {@code normalTopic}, {@code transTopic},
 * {@code demo-topic}, {@code topic_a}, {@code topic_b}. The remaining constants are
 * used by tests for producer-only or auto-create flows.</p>
 */
public final class Topics {

    /** Spring Cloud Stream binder destination used by spring-cloud-v* modules. */
    public static final String TOPIC_DEMO = "demo";

    /** Starter (sbs) and demo topic used by sbs and client modules. */
    public static final String TOPIC_DEMO_TOPIC = "demo-topic";

    /** Normal message topic. Pre-created with message.type=NORMAL. */
    public static final String TOPIC_NORMAL = "normalTopic";

    /** Delay message topic. Pre-created with message.type=DELAY. */
    public static final String TOPIC_DELAY = "delayTopic";

    /** FIFO message topic. Pre-created with message.type=FIFO. */
    public static final String TOPIC_FIFO = "fifoTopic";

    /** Transaction message topic. Pre-created with message.type=TRANSACTION. */
    public static final String TOPIC_TRANSACTION = "transTopic";

    /** Generic single-letter topic used by client/SimpleSyncProducer and others. */
    public static final String TOPIC_A = "topic_a";

    /** Second generic topic, on broker 2. */
    public static final String TOPIC_B = "topic_b";

    /** Placeholder name used by V5 example code; tests use TOPIC_NORMAL instead. */
    public static final String YOUR_TOPIC = TOPIC_NORMAL;

    /** Placeholder for V5 transaction examples; tests use TOPIC_TRANSACTION. */
    public static final String YOUR_TRANSACTION_TOPIC = TOPIC_TRANSACTION;

    private Topics() {}
}
