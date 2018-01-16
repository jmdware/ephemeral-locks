package org.jmdware.ephemeral.locks.junit;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.rules.ExternalResource;

public class Threads extends ExternalResource {

    private ExecutorService threads;

    @Override
    protected void before() {
        threads = Executors.newCachedThreadPool();
    }

    @Override
    protected void after() {
        threads.shutdownNow();
    }

    public <T> Future<T> submit(Callable<T> task) {
        return threads.submit(task);
    }

    public <T> Future<T> submit(Runnable task, T result) {
        return threads.submit(task, result);
    }

    public Future<?> submit(Runnable task) {
        return threads.submit(task);
    }

    public void execute(Runnable command) {
        threads.execute(command);
    }
}
