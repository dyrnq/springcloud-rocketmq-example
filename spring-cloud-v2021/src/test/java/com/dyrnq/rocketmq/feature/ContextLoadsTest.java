package com.dyrnq.rocketmq.feature;

import com.dyrnq.sca.rocketmq.RocketmqApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Verifies the Spring Boot context boots cleanly for {@code spring-cloud-v2021}. */
@SpringBootTest(classes = RocketmqApplication.class)
@ActiveProfiles("test")
class ContextLoadsTest {

  @Test
  void contextLoads() {
    // If the context fails to start, this test errors out automatically.
  }
}
