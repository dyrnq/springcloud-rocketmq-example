package com.dyrnq.rocketmq.testsupport;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Helper that polls an {@link AtomicInteger} (typically a counter incremented by a
 * consumer callback) until it reaches a target value, or throws
 * {@link AssertionError} on timeout.
 *
 * <p>Lightweight re-implementation of awaitility's {@code await().until(callable)}
 * that avoids pulling in the awaitility dependency on modules that don't otherwise
 * use it.</p>
 */
public final class ReceiptAwaiter {

    private final AtomicInteger counter;
    private final int target;
    private final Duration timeout;

    private ReceiptAwaiter(AtomicInteger counter, int target, Duration timeout) {
        this.counter = counter;
        this.target = target;
        this.timeout = timeout;
    }

    public static ReceiptAwaiter waitingFor(AtomicInteger counter, int target, Duration timeout) {
        return new ReceiptAwaiter(counter, target, timeout);
    }

    public void poll(String description) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (counter.get() < target) {
            if (System.nanoTime() > deadlineNanos) {
                throw new AssertionError(
                    "Timed out after " + timeout.toMillis() + "ms waiting for "
                        + target + " message(s) ("
                        + description + "); got " + counter.get());
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
        }
    }
}
