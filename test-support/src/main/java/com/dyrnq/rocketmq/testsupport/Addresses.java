package com.dyrnq.rocketmq.testsupport;

/**
 * Network addresses for the live Vagrant VM ("server", 192.168.88.123) that hosts the
 * controller-mode RocketMQ topology used by every test in this project.
 *
 * <p>These constants are the single source of truth for the broker endpoint. To switch
 * the entire test suite to a different VM, change these values (and re-run
 * {@code scripts/run-containers-controller.sh} on the new host).</p>
 */
public final class Addresses {

    /** NameSrv reachable on the host machine (port 9876 is the default). */
    public static final String NAMESERVER = "192.168.88.123:9876";

    /** gRPC proxy reachable on the host machine (port 8181 is the controller-mode default). */
    public static final String PROXY = "192.168.88.123:8181";

    /** Master broker of group q1 (brokerId 0). Used as a target for admin commands. */
    public static final String BROKER_ADDR_1 = "192.168.88.123:11911";

    /** Master broker of group q3 (brokerId 0). Used as a target for admin commands. */
    public static final String BROKER_ADDR_2 = "192.168.88.123:13911";

    private Addresses() {}
}
