package com.dyrnq.rocketmq.feature;

import com.dyrnq.rocketmq.sbs.RocketmqApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the Spring Boot context boots cleanly for the {@code sbs} module
 * (with the demo {@code ProducerRunner} / {@code PushConsumerRunner} runners
 * disabled via {@code !test} profile).
 */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class ContextLoadsTest {

    @Test
    void contextLoads() {
        // If the context fails to start, this test errors out automatically.
    }
}
