# Modules

Nine Maven modules, organised by RocketMQ client API (V4 Remoting vs V5 gRPC) and
integration layer (raw client vs Spring Boot starter vs Spring Cloud Stream binder).

```
springcloud-rocketmq-example/                      ← root pom, packaging=pom, aggregator only
├── test-support/                                  ← shared test constants; test scope only
├── spring-cloud-v2025-1/                          ← Spring Boot 4.1.0 + Spring Cloud Stream binder
├── spring-cloud-v2025/                            ← Spring Boot 3.5.9 + Spring Cloud Stream binder
├── spring-cloud-v2023/                            ← Spring Boot 3.3.13 + Spring Cloud Stream binder
├── spring-cloud-v2021/                            ← Spring Boot 2.7.18 + Spring Cloud Stream binder
├── sbs/                                           ← Spring Boot 3.5.9 + rocketmq-spring-boot-starter (V4)
├── sbs-v5/                                        ← Spring Boot 3.5.9 + rocketmq-v5-client-spring-boot-starter
├── client-v5/                                     ← rocketmq-client-java 5.2.1 (raw V5 gRPC)
└── client/                                        ← rocketmq-client 5.3.4 (raw V4 Remoting)
```

## What each module exercises

| Module               | Spring Boot | Spring Cloud | Spring Cloud Alibaba | RocketMQ client          | Notes                                                                                                  |
| -------------------- | ----------- | ------------ | -------------------- | ------------------------ | ------------------------------------------------------------------------------------------------------ |
| `spring-cloud-v2025-1` | 4.1.0       | 2025.1.2     | 2025.1.0.0           | (transitive via alibaba) | Latest generation. Spring Boot 4.x removed `SpyBean`; tests use `MockitoSpyBean` from spring-test 6.2.5. |
| `spring-cloud-v2025`   | 3.5.9       | 2025.0.1     | 2025.0.0.0           | (transitive via alibaba) | Same code shape as 2025-1, but on Spring Boot 3.5.x. Still uses the legacy `SpyBean`.                  |
| `spring-cloud-v2023`   | 3.3.13      | 2023.0.6     | 2023.0.3.4           | (transitive via alibaba) | Mid-generation baseline.                                                                               |
| `spring-cloud-v2021`   | 2.7.18      | 2021.0.9     | 2021.0.6.0           | (transitive via alibaba) | Spring Boot 2.7.x — last generation on javax/jakarta boundary. Test-binder dependency is **commented out** (incompatible with 2.7.x). |
| `sbs`                | 3.5.9       | —            | —                    | rocketmq-spring-boot-starter 2.3.6 (V4) | Starter (no Spring Cloud Stream).                                                                       |
| `sbs-v5`             | 3.5.9       | —            | —                    | rocketmq-v5-client-spring-boot-starter 2.3.6 | V5 starter.                                                                                             |
| `client-v5`          | —           | —            | —                    | rocketmq-client-java 5.2.1 (gRPC)         | Raw V5 client, no Spring. JUnit 5 standalone.                                                          |
| `client`             | —           | —            | —                    | rocketmq-client 5.3.4 (Remoting)          | Raw V4 client, no Spring. JUnit 5 standalone. Includes admin / ACL examples.                          |

`spring-cloud-v*` and `sbs*` modules inherit dependency management from
`spring-boot-starter-parent`; `client` and `client-v5` do not (no Spring Boot) and
declare `junit-jupiter` 5.10.3 directly.

## The `test-support` module

Packaging: `jar`. Scope: `compile` (consumers add it as `test` scope only). Contents:

| Class                                   | Purpose                                                                    |
| --------------------------------------- | -------------------------------------------------------------------------- |
| [`Addresses`](../test-support/src/main/java/com/dyrnq/rocketmq/testsupport/Addresses.java) | Broker endpoints — `NAMESERVER`, `PROXY`, `BROKER_ADDR_1`, `BROKER_ADDR_2`. |
| [`Topics`](../test-support/src/main/java/com/dyrnq/rocketmq/testsupport/Topics.java)       | Topic name constants. `TOPIC_NORMAL`, `TOPIC_DELAY`, `TOPIC_FIFO`, `TOPIC_TRANSACTION`, etc. |
| [`Groups`](../test-support/src/main/java/com/dyrnq/rocketmq/testsupport/Groups.java)       | Producer / consumer group names. `uniquePush()` / `uniqueSimple()` return per-test fresh names. |
| [`MessageFactory`](../test-support/src/main/java/com/dyrnq/rocketmq/testsupport/MessageFactory.java) | Build V4 `Message` instances with unique bodies. |
| [`ReceiptAwaiter`](../test-support/src/main/java/com/dyrnq/rocketmq/testsupport/ReceiptAwaiter.java) | Poll an `AtomicInteger` until it reaches a target, with a deadline. Lightweight re-implementation of Awaitility. |

See [test-support.md](test-support.md) for the full reference.

## Module → test-support mapping

Every module declares:

```xml
<dependency>
    <groupId>com.dyrnq.rocketmq</groupId>
    <artifactId>test-support</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

so all tests share the same broker endpoint constants and topic names. Change one
constant in `Addresses`, and the entire test suite retargets.

## Adding a new module

1. Copy the layout of the closest sibling (use `client/` for a raw-client module,
   `sbs-v5/` for a Spring Boot starter module, `spring-cloud-v2025/` for a Spring
   Cloud Stream binder module).
2. Add `<module>your-module</module>` to the root `pom.xml` `<modules>` list.
3. Add the `test-support` dependency above to the new module's `pom.xml`.
4. Add `<plugin>com.diffplug.spotless:spotless-maven-plugin</plugin>` to the new
   module's `<build><plugins>`. **The Spotless plugin is declared in the root pom
   but is NOT inherited by child modules** — see
   [formatting.md § Why every module redeclares Spotless](formatting.md#why-every-module-redeclares-spotless).
5. Run `mvn spotless:apply` once on the new module to format your files.