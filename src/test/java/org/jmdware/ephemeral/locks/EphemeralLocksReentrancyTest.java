package org.jmdware.ephemeral.locks;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import org.jmdware.ephemeral.locks.EphemeralLocks.Handle;
import org.junit.Test;

public class EphemeralLocksReentrancyTest {

    private static final String KEY = "key";

    private final EphemeralLocks locks = new EphemeralLocks();

    @Test(timeout = 1_000)
    public void test() {
        try (Handle ignored1 = locks.lock(KEY)) {
            try (Handle ignored2 = locks.lock(KEY)) {
                assertThat(locks.size(), equalTo(1));
            }
        }
    }
}
