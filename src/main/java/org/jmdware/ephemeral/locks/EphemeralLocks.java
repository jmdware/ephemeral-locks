package org.jmdware.ephemeral.locks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>
 * Balances parallelism with thread-safety by assigning different locks to different keys. This
 * allows threads operating on different keys to simultaneously execute the critical section while
 * threads operating on the same key execute the critical section serially. Memory usage grows with
 * the number of active keys. Underlying lock instances are discarded when no longer needed.
 * </p>
 *
 * <p>
 * Keys must implement {@link #equals(Object)} and {@link #hashCode()} correctly.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * private final EphemeralLocks locks = new EphemeralLocks();
 *
 * ...
 *
 * // The id for the thread-sensitive resource. E.g. directory name, document
 * // id, user id, etc.
 * String id = ...;
 *
 * // try-with-resources recommended to ensure the unlock occurs.
 * try (Handle ignored = locks.lock(id)) {
 *     // critical section - do sensitive operations here
 *     ...
 * }
 *
 * // try-finally works, too.
 * Handle lockHandle = locks.lock(id);
 *
 * try {
 *     // critical section - do sensitive operations here
 *     ...
 * } finally {
 *     lockHandle.close();
 * }
 * </pre>
 */
public class EphemeralLocks {

    private final ConcurrentHashMap<Object, Context> keyToContext = new ConcurrentHashMap<>();

    /**
     * Acquires the lock for the given key and returns a handle to the lock, which <b>must</b> be
     * closed to release the underlying lock. Blocks uninterruptibly until the lock is acquired.
     *
     * @param key
     *         identifies the resource requiring a lock
     *
     * @return handle for releasing the underlying lock
     *
     * @see Lock#lock()
     */
    public Handle lock(Object key) {
        Context ctx = keyToContext.compute(key, (k, v) -> {
            if (v == null) {
                v = new Context();
            }

            v.count.incrementAndGet();

            return v;
        });

        ctx.lock.lock();

        AtomicBoolean closed = new AtomicBoolean();

        return () -> keyToContext.compute(key, (k, v) -> {
            if (closed.compareAndSet(false, true)) {
                v.lock.unlock();

                return v.count.decrementAndGet() == 0 ? null : v;
            }

            // Stale so don't change anything.
            return v;
        });
    }

    /**
     * Returns the number of active locks.
     *
     * @return active locks count
     */
    public int size() {
        return keyToContext.size();
    }

    private static class Context {

        private final Lock lock = new ReentrantLock();

        private final AtomicInteger count = new AtomicInteger();
    }

    /**
     * Handle to close the underlying lock for a given key.
     */
    public interface Handle extends AutoCloseable {
        @Override
        void close();
    }
}
