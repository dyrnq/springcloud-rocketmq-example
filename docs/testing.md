# Testing

Every test in this repository talks to a **real broker running in the Vagrant VM** —
there is no embedded broker, no Testcontainers, no mocked clients. This is a deliberate
choice: most of the features exercised here (delay, FIFO, transaction, ACL, controller
mode) depend on broker-side behaviour that no in-memory fake can replicate.

Before running tests, follow [local-development.md](local-development.md) to bring up
the broker. Then:

```bash
# Run the entire test suite
mvn -T 1C test

# Run only one module's tests
mvn -pl sbs -am test

# Run a single test class
mvn -pl client test -Dtest=DelayLevelTest

# Skip slow V5 gRPC tests (see "Known flaky tests" below)
mvn -T 1C test -Dtest='!SimpleConsumeTest,!AsyncSimpleConsumeTest'
```

## Test matrix per module

Test counts are the current snapshot — numbers shift as new tests are added.

| Module               | Tests                                                                                                                                  | Count |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ----- |
| `client`             | `AdminToolTest`, `DelayLevelTest`, `PushConsumeTest`, `SyncSendTest`                                                                   | 4     |
| `client-v5`          | `AsyncSendTest`, `AsyncSimpleConsumeTest`, `DelayTimestampTest`, `FifoMessageTest`, `PushConsumeTest`, `SimpleConsumeTest`, `SyncSendTest`, `TransactionalSendTest` | 8 |
| `sbs`                | `ContextLoadsTest`, `DelayLevelTest`, `DelayTimestampTest`, `PushConsumeTest`, `SyncSendTest`                                          | 5     |
| `sbs-v5`             | `AsyncDelayTimestampTest`, `AsyncSendTest`, `ContextLoadsTest`, `DelayTimestampTest`, `FifoMessageTest`, `PushConsumeTest`, `SyncSendTest`, `TransactionalSendTest` | 8 |
| `spring-cloud-v2021` | `AsyncSendTest`, `ContextLoadsTest`, `PushConsumeTest`                                                                                 | 3     |
| `spring-cloud-v2023` | `AsyncSendTest`, `ContextLoadsTest`, `PushConsumeTest`                                                                                 | 3     |
| `spring-cloud-v2025` | `AsyncSendTest`, `ContextLoadsTest`, `PushConsumeTest`                                                                                 | 3     |
| `spring-cloud-v2025-1` | `AsyncSendTest`, `ContextLoadsTest`, `PushConsumeTest`                                                                               | 3     |

Total: **37 tests** across 8 modules.

## Test dimensions covered

Each module exercises the same five production dimensions, where the underlying client
supports them:

| Dimension       | What it verifies                                                                  | V4 API                                | V5 API                                                |
| --------------- | --------------------------------------------------------------------------------- | ------------------------------------- | ----------------------------------------------------- |
| Sync send       | `producer.send(message)` returns success                                          | `MessageQueueSelector`, `SendResult`  | `RocketMQClientTemplate.syncSend`                     |
| Async send      | `producer.send(message, callback)` invokes the callback with success             | `SendCallback`                        | `RocketMQClientTemplate.asyncSend`                    |
| Push consume    | Round-trip via a real `DefaultMQPushConsumer` on the test topic                   | `DefaultMQPushConsumer`               | n/a (V5 has no push consumer; uses simple consumer)   |
| Delay (level)   | Message with `setDelayTimeLevel(N)` is delivered ≥ N seconds after send           | `Message.setDelayTimeLevel(2)`         | n/a (V5 only supports absolute timestamp)             |
| Delay (time)    | Message with `setDeliverTimeMs(now+Δ)` is delivered at approximately `now+Δ`     | `Message.setDeliverTimeMs(...)`        | `syncSendDelayMessage(topic, body, Duration)`         |
| FIFO            | Messages with the same `MessageGroup` arrive in send order, single consumer       | (V4 FIFO is topic-level, not group)   | `MessageBuilder.setMessageGroup(...)`                 |
| Transaction     | Local transaction state transitions COMMIT / ROLLBACK are observable              | `TransactionMQProducer`               | `RocketMQClientTemplate.sendMessageInTransaction`     |
| Simple consume  | `SimpleConsumer.receive()` returns the expected message body                      | n/a                                   | `SimpleConsumer.builder()...build()`                  |

## Key test patterns

### 1. Raw V4 push consumer round-trip (used by `PushConsumeTest` in every module)

Spring Cloud Stream binder tests can't easily intercept the binder's internal channel
adapter (we tried `@SpyBean`, `@Primary @TestConfiguration`, bean-definition override —
all failed in different ways). The cleanest solution is a raw V4 `DefaultMQPushConsumer`
on the same `demo` topic:

```java
String bodyMarker = "v2023-push-marker-" + UUID.randomUUID();

DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(
    "push-test-" + UUID.randomUUID(),  // unique group per run
    new AclClientRPCHook(new SessionCredentials("ak", "sk")),
    new AllocateMessageQueueAveragely(),
    true, null);
consumer.subscribe(Topics.TOPIC_DEMO, "*");
consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
    for (MessageExt m : msgs) {
        if (new String(m.getBody(), StandardCharsets.UTF_8).equals(bodyMarker)) {
            received.incrementAndGet();
        }
    }
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
});
consumer.start();

// Trigger via the production StreamBridge / Template
streamBridge.send(Topics.TOPIC_DEMO, bodyMarker);

ReceiptAwaiter.waitingFor(received, 1, Duration.ofSeconds(30)).poll("body marker");
```

The production `consumerEvent()` bean is still exercised by the binder (visible in
logs as `It Received message: ...`), but the test directly verifies the broker round-trip.

### 2. Body-marker filter for delay tests (used by `DelayLevelTest`, `DelayTimestampTest`)

**Problem:** Old delay messages with expired timers get delivered immediately when a
consumer subscribes — they pollute the `firstDeliveryAt` timestamp and make the test
report "delivered in 800 ms" when the level was supposed to be 5 s.

**Fix:** Filter on a fresh body marker before recording delivery time. Each test gets a
unique body, so only its own message can set the counter.

```java
String bodyMarker = "delay-marker-" + UUID.randomUUID();
AtomicLong firstDeliveryAt = new AtomicLong(0);

consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
    for (MessageExt m : msgs) {
        if (new String(m.getBody(), StandardCharsets.UTF_8).equals(bodyMarker)) {
            firstDeliveryAt.compareAndSet(0, System.currentTimeMillis());
        }
    }
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
});
```

Also use a unique consumer group per test (`Groups.uniquePush()`) and a fresh consumer in
`@BeforeEach` so leftover offsets don't bleed between runs.

### 3. V4 ACL auth in raw-client tests

The broker requires `AclClientRPCHook` for delay messages. Same as `ClientCreater` in
`client/src/main/java`:

```java
RPCHook rpcHook = new AclClientRPCHook(new SessionCredentials("ak", "sk"));
DefaultMQProducer producer = new DefaultMQProducer(
    Groups.uniqueProducer(), rpcHook);
```

### 4. V5 `SimpleConsumer` long-poll (used by `SimpleConsumeTest`, `AsyncSimpleConsumeTest`)

V5's `SimpleConsumer` uses gRPC long-polling internally; `receive()` blocks until a
message arrives or the timeout expires. Pattern:

```java
SimpleConsumer consumer = SimpleConsumer.builder()
    .setConsumerGroup(Groups.uniqueSimple())
    .setEndpoints(Addresses.PROXY)
    .setSubscriptionExpressions(Collections.singletonMap(Topics.TOPIC_DEMO,
        FilterExpression.SUB_ALL))
    .build();

// Wait ~2 s for the gRPC long-poll subscription to fully establish
LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));

MessageView message = consumer.receive(Duration.ofSeconds(10));
assertEquals(bodyMarker, StandardCharsets.UTF_8.decode(message.getBody()).toString());
consumer.ack(message);
```

V5 `MessageView.getBody()` returns a `ByteBuffer` (not `byte[]` like V4's `Message.getBody()`);
`MessageBuilder.setBody()` takes `byte[]`.

### 5. Spring Cloud Stream `StreamBridge.send` (used by `AsyncSendTest`)

```java
@SpringBootTest
class AsyncSendTest {
    @Autowired StreamBridge streamBridge;
    @Test void sendAndForget() {
        streamBridge.send(Topics.TOPIC_DEMO, MessageFactory.v4(Topics.TOPIC_DEMO));
    }
}
```

`ContextLoadsTest` in every spring-cloud-v* and sbs module is a bare `@SpringBootTest`
sanity check — verifies that auto-config + binder wiring resolves without throwing.

## Known flaky tests

These tests are environmental, not deterministic — they depend on broker load, gRPC
timing, and the global V5 SimpleConsumer subscription state. They are pre-existing and
not caused by formatting or other code-style changes.

| Test                                       | Module      | Symptom                                                  | Workaround                                                            |
| ------------------------------------------ | ----------- | -------------------------------------------------------- | --------------------------------------------------------------------- |
| `SimpleConsumeTest`                        | `client-v5` | `UnfinishedVerification` / `TimeoutException`            | Re-run alone: `mvn -pl client-v5 test -Dtest=SimpleConsumeTest`       |
| `AsyncSimpleConsumeTest`                   | `client-v5` | Same as above                                            | Same; the two tests interfere via gRPC long-poll subscription caching |

Other tests are stable on a single-broker controller-mode topology.

## CI

The CI pipeline (`mvn -B package --file pom.xml -Dmaven.test.skip=true`) **does not run
tests** — only build and format check. So a test regression won't be caught by CI. Run
tests locally before pushing. See [ci.md](ci.md).