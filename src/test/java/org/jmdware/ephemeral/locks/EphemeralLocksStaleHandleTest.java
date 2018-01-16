package org.jmdware.ephemeral.locks;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.jmdware.ephemeral.locks.EphemeralLocks.Handle;
import org.jmdware.ephemeral.locks.junit.Threads;
import org.junit.Rule;
import org.junit.Test;

public class EphemeralLocksStaleHandleTest {

    private static final String KEY = "key";

    @Rule
    public final Threads threads = new Threads();

    /**
     * When an async lock attempt succeeds.
     */
    private final BlockingQueue<Long> acquiredAts = new LinkedBlockingQueue<>();

    /**
     * Signal for an async lock owner to release the lock.
     */
    private final Semaphore unlockPermits = new Semaphore(0);

    private final EphemeralLocks locks = new EphemeralLocks();

    private Handle handle;

    private long releasedAt;

    @Test(timeout = 3_000L)
    public void test() throws Exception {
        givenLockHeld();
        givenAsyncLockAttempt();
        givenAsyncLockAttempt();

        // first lock still held
        thenLockHeld();

        whenHandleMadeStale();

        thenAsyncLockAcquired();
        thenLockHeld();

        whenAsyncUnlocked();

        thenAsyncLockAcquired();

        // Let the second async lock attempt return
        whenAsyncUnlocked();
    }

    private void givenLockHeld() {
        handle = locks.lock(KEY);
    }

    private void givenAsyncLockAttempt() {
        threads.submit(() -> {
            try (Handle ignored = locks.lock(KEY)) {
                acquiredAts.offer(System.nanoTime());

                // Operations on stale handles should have no effect!
                // Invoke in the same thread as the most recent lock acquisition to satisfy
                // ReentractLock's thread ownership checking, which is nice but not strict
                // enough for us.
                handle.close();

                unlockPermits.acquireUninterruptibly();
            }
        });
    }

    private void whenHandleMadeStale() {
        releasedAt = System.nanoTime();
        handle.close(); // this handle is now "stale"
    }

    private void thenLockHeld() throws InterruptedException {
        assertThat(pollAcquiredAts(), nullValue());
    }

    private void thenAsyncLockAcquired() throws InterruptedException {
        assertThat(pollAcquiredAts(), greaterThanOrEqualTo(releasedAt));
    }

    private Long pollAcquiredAts() throws InterruptedException {
        return acquiredAts.poll(500, TimeUnit.MILLISECONDS);
    }

    private void whenAsyncUnlocked() {
        releasedAt = System.nanoTime();
        unlockPermits.release();
    }
}
