package com.dyrnq.rocketmq.testsupport;

/**
 * Network addresses for the RocketMQ topology used by every test in this project.
 *
 * <p>Defaults target the live Vagrant VM ("server", 192.168.88.123) documented in the README, so
 * local development works unchanged. CI overrides them through environment variables (see {@code
 * scripts/ci/start-rocketmq.sh} and {@code .github/workflows/integration-test.yml}).
 */
public final class Addresses {

  private static final String DEFAULT_HOST = "192.168.88.123";

  /** NameSrv reachable on the host machine (port 9876 is the default). */
  public static final String NAMESERVER = envOr("ROCKETMQ_NAMESRV", DEFAULT_HOST + ":9876");

  /** gRPC proxy reachable on the host machine (port 8181 is the controller-mode default). */
  public static final String PROXY = envOr("ROCKETMQ_PROXY", DEFAULT_HOST + ":8181");

  /** Master broker of group q1 (brokerId 0). Used as a target for admin commands. */
  public static final String BROKER_ADDR_1 = envOr("ROCKETMQ_BROKER_1", DEFAULT_HOST + ":11911");

  /** Master broker of group q3 (brokerId 0). Used as a target for admin commands. */
  public static final String BROKER_ADDR_2 = envOr("ROCKETMQ_BROKER_2", DEFAULT_HOST + ":13911");

  private Addresses() {}

  private static String envOr(String key, String defaultValue) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
