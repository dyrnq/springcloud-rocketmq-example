# `test-support` module

A shared `jar` module (no Spring, no Boot) that every test in the project depends on.
Provides constants for broker endpoints, topic names, group names, a message factory,
and a lightweight polling helper. All classes are public, package-private constructors
— they are utility classes, never instantiated.

```xml
<dependency>
    <groupId>com.dyrnq.rocketmq</groupId>
    <artifactId>test-support</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## `Addresses`

Network endpoints. Single source of truth for "where is the broker?".

```java
public static final String NAMESERVER = "192.168.88.123:9876";  // V4 NameSrv
public static final String PROXY      = "192.168.88.123:8181";  // V5 gRPC Proxy
public static final String BROKER_ADDR_1 = "192.168.88.123:11911";  // q1 master (group 1)
public static final String BROKER_ADDR_2 = "192.168.88.123:13911";  // q3 master (group 2)
```

To retarget the entire test suite to a different VM, change these four constants and
bring the broker up on the new host with the matching `scripts/run-containers-*.sh`.

## `Topics`

Centralised topic names. Pre-created by `scripts/run-containers-controller.sh`:

| Constant               | Value           | `message.type` | Notes                                                |
| ---------------------- | --------------- | -------------- | ---------------------------------------------------- |
| `TOPIC_DEMO`           | `demo`          | (default)      | Spring Cloud Stream binder destination.              |
| `TOPIC_DEMO_TOPIC`     | `demo-topic`    | (default)      | Used by `sbs` and `client` modules.                  |
| `TOPIC_NORMAL`         | `normalTopic`   | NORMAL         | V5 normal-message tests.                             |
| `TOPIC_DELAY`          | `delayTopic`    | DELAY          | V4 + V5 delay-message tests.                         |
| `TOPIC_FIFO`           | `fifoTopic`     | FIFO           | V5 FIFO tests.                                       |
| `TOPIC_TRANSACTION`    | `transTopic`    | TRANSACTION    | V5 transaction tests.                                |
| `TOPIC_A`              | `topic_a`       | (default)      | `client/SyncSendTest`.                               |
| `TOPIC_B`              | `topic_b`       | (default)      | `client/SyncSendTest` (broker 2).                    |

`autoCreateTopicEnable=true` is set on the broker, so any topic not listed here is
auto-created with default 4 read / 4 write queues on first produce.

## `Groups`

Consumer / producer / FIFO group names.

```java
public static final String PRODUCER_DEFAULT        = "test-producer";
public static final String CONSUMER_PUSH_DEMO      = "demo-group";
public static final String CONSUMER_PUSH_LEGACY    = "your_consumer_group";
public static final String CONSUMER_SIMPLE         = "yourConsumerGroup";
public static final String MESSAGE_GROUP_FIFO      = "group1";

public static String uniquePush();     // → "push-<8 hex chars>"
public static String uniqueSimple();   // → "simple-<8 hex chars>"
public static String uniqueProducer(); // → "producer-<8 hex chars>"
```

**Always use `uniquePush()` / `uniqueSimple()` in tests** — stable group names leave
offsets on the broker that bleed into the next run. The unique helpers ensure every
test starts from a clean offset position.

## `MessageFactory`

Build V4 `Message` instances with bodies guaranteed to be unique per call.

```java
public static Message v4(String topic);                          // tag = DEFAULT_TAG ("yourMessageTagA")
public static Message v4(String topic, String tag);              // custom tag
public static Message v4OfSize(String topic, int sizeBytes);     // padded body, first 24 bytes random
```

`v4OfSize` is for size-sensitive tests (e.g. message-too-large handling); the random
prefix prevents the broker from deduplicating identical-size messages.

## `ReceiptAwaiter`

Polls an `AtomicInteger` until it reaches a target value, or throws
`AssertionError` on timeout. Lightweight re-implementation of Awaitility's
`await().until(callable)`, avoiding the awaitility dependency on modules that don't
otherwise use it.

```java
AtomicInteger received = new AtomicInteger(0);

// In some consumer callback:
received.incrementAndGet();

// In the test body:
ReceiptAwaiter.waitingFor(received, 1, Duration.ofSeconds(30)).poll("body marker");
```

Polls every 100 ms. The `description` argument is included in the timeout message so
failure logs point at the right assertion.

## Conventions when using `test-support`

- **Don't redeclare broker endpoints.** If a test needs `192.168.88.123:9876`, import
  `Addresses.NAMESERVER`. The whole point of `test-support` is that a host swap
  touches one file.
- **Don't reuse stable group names in tests.** Always use `Groups.uniquePush()` etc.
- **Generate fresh bodies per test.** `MessageFactory.v4()` already does this; do not
  call `new Message(topic, tag, "fixed-body".getBytes())` directly — the broker may
  dedupe or the consumer may receive a leftover message from a previous run.
- **Use `ReceiptAwaiter` instead of `Thread.sleep`.** A test that sleeps 30 s and
  asserts is brittle; a test that polls and fails fast on timeout is informative.