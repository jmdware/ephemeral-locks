package org.jmdware.ephemeral.locks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import org.jmdware.ephemeral.locks.EphemeralLocks.Handle;
import org.jmdware.ephemeral.locks.junit.Threads;
import org.junit.Rule;
import org.junit.Test;

public class EphemeralLocksMutualExclusionTest {
    @Rule
    public final Threads threads = new Threads();

    private final EphemeralLocks ephemeralLocks = new EphemeralLocks();

    private final Map<Object, AtomicInteger> keyToBlocked = new HashMap<>();

    private final Map<Object, List<CountDownLatch>> keyToLatches = new HashMap<>();

    private final Map<Object, Integer> keyToLastReleased = new HashMap<>();

    private final Set<Object> acquiredKeys = ConcurrentHashMap.newKeySet();

    @Test(timeout = 5_000)
    public void test() throws Exception {
        whenTryLock(new String("key-1"));

        thenRunning("key-1", 1);
        thenLocksAcquired("key-1");
        thenSizeIs(1);

        whenTryLock(new String("key-1"));

        thenRunning("key-1", 2);
        thenLocksAcquired("key-1");
        thenSizeIs(1);

        whenTryLock(new String("key-1"));

        thenRunning("key-1", 3);
        thenSizeIs(1);

        // new key
        whenTryLock(new String("key-2"));

        thenRunning("key-1", 3);
        thenRunning("key-2", 1);
        thenLocksAcquired("key-1", "key-2");
        thenSizeIs(2);

        // new key
        whenTryLock(new String("key-3"));

        thenRunning("key-1", 3);
        thenRunning("key-2", 1);
        thenRunning("key-3", 1);
        thenLocksAcquired("key-1", "key-2", "key-3");
        thenSizeIs(3);

        whenTryLock(new String("key-2"));

        thenRunning("key-1", 3);
        thenRunning("key-2", 2);
        thenRunning("key-3", 1);
        thenLocksAcquired("key-1", "key-2", "key-3");
        thenSizeIs(3);

        whenLockReleased("key-1");

        thenRunning("key-1", 2);
        thenRunning("key-2", 2);
        thenRunning("key-3", 1);
        thenLocksAcquired("key-1", "key-2", "key-3");
        thenSizeIs(3);

        whenLockReleased("key-2");

        thenRunning("key-1", 2);
        thenRunning("key-2", 1);
        thenRunning("key-3", 1);
        thenLocksAcquired("key-1", "key-2", "key-3");
        thenSizeIs(3);

        whenLockReleased("key-2");

        thenRunning("key-1", 2);
        thenRunning("key-2", 0);
        thenRunning("key-3", 1);
        thenLocksAcquired("key-1", "key-3");
        thenSizeIs(2);

        whenLockReleased("key-1");

        thenRunning("key-1", 1);
        thenRunning("key-2", 0);
        thenRunning("key-3", 1);
        thenLocksAcquired("key-1", "key-3");
        thenSizeIs(2);

        whenLockReleased("key-1");

        thenRunning("key-1", 0);
        thenRunning("key-2", 0);
        thenRunning("key-3", 1);
        thenLocksAcquired("key-3");
        thenSizeIs(1);

        // new key
        whenTryLock(new Integer(4));

        thenRunning("key-1", 0);
        thenRunning("key-2", 0);
        thenRunning("key-3", 1);
        thenRunning(      4, 1);
        thenLocksAcquired("key-3", 4);
        thenSizeIs(2);

        whenLockReleased(4);

        thenRunning("key-1", 0);
        thenRunning("key-2", 0);
        thenRunning("key-3", 1);
        thenRunning(      4, 0);
        thenLocksAcquired("key-3");
        thenSizeIs(1);

        whenLockReleased("key-3");

        thenRunning("key-1", 0);
        thenRunning("key-2", 0);
        thenRunning("key-3", 0);
        thenRunning(      4, 0);
        thenLocksAcquired();
        thenSizeIs(0);
    }

    private void whenLockReleased(Object key) {
        int next = keyToLastReleased.compute(key, (k, last) -> last == null ? 0 : last + 1);

        keyToLatches.get(key).get(next).countDown();
    }

    private void thenRunning(Object key, int expected) {
        loopUntilTrue(() -> keyToBlocked.get(key) != null && keyToBlocked.get(key).get() == expected);
    }

    private void thenLocksAcquired(Object... keys) {
        Set<Object> expected = new HashSet<>(Arrays.asList(keys));

        loopUntilTrue(() -> acquiredKeys.equals(expected));
    }

    private void thenSizeIs(int expected) {
        loopUntilTrue(() -> ephemeralLocks.size() == expected);
    }

    private void whenTryLock(Object k) {
        List<CountDownLatch> latches = keyToLatches.computeIfAbsent(k, (ignored) -> new CopyOnWriteArrayList<>());
        AtomicInteger blocked = keyToBlocked.computeIfAbsent(k, (ignored) -> new AtomicInteger());

        int index = latches.size();

        latches.add(new CountDownLatch(1));

        threads.submit(() -> {
            blocked.incrementAndGet();

            try (Handle ignored = ephemeralLocks.lock(k)) {
                acquiredKeys.add(k);
                latches.get(index).await();
                acquiredKeys.remove(k);
            } finally {
                blocked.decrementAndGet();
            }

            return null;
        });
    }

    private void loopUntilTrue(BooleanSupplier test) {
        while (!Thread.currentThread().isInterrupted() && !test.getAsBoolean()) {
            LockSupport.parkNanos(10_000_000);
        }

        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("interrupted");
        }
    }
}
