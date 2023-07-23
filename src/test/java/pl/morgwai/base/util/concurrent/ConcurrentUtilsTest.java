// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;

import org.junit.Test;
import pl.morgwai.base.util.concurrent.ConcurrentUtils.RunnableCallable;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static pl.morgwai.base.util.concurrent.ConcurrentUtils.*;



public class ConcurrentUtilsTest {



	Throwable asyncError;



	@Test
	public void testCompletableFutureSupplyAsyncThrowing()
			throws InterruptedException, TimeoutException {
		final var thrown = new Exception("thrown");

		final var execution = completableFutureSupplyAsync(
			() -> { throw thrown; },
			Executors.newSingleThreadExecutor()
		).whenComplete(
			(result, caught) -> {
				if (result == null) asyncError = caught;
			}
		);
		try {
			execution.get(50L, TimeUnit.MILLISECONDS);
			fail("execution of a throwing task should throw ExecutionException");
		} catch (ExecutionException e) {
			assertSame("caught exception should be the same as thrown by the Callable task",
					thrown, e.getCause());
		}
		assertSame("caught exception should be the same as thrown by the Callable task",
				thrown, asyncError);
	}



	@Test
	public void testCompletableFutureSupplyAsync() throws Exception {
		final var result = "result";
		final var testTask = new Callable<String>() {
			@Override public String call() {
				return result;
			}
			@Override public String toString() {
				return "testTask";
			}
		};
		final var executor = new TaskCapturingExecutor();

		final var execution = completableFutureSupplyAsync(testTask, executor);
		assertSame("result of execution should be the same as returned by testTask",
				result, execution.get(50L, TimeUnit.MILLISECONDS));
		assertTrue("capturedTask should be a RunnableCallable instance",
				executor.capturedTask instanceof RunnableCallable);
		@SuppressWarnings("unchecked")
		final var capturedTask = (RunnableCallable<String>) executor.capturedTask;
		assertSame("capturedTask should be wrapping testTask",
				testTask, capturedTask.getWrappedTask());
		assertSame("RunnableCallable should delegate toString to the original task",
				testTask.toString(), executor.capturedTask.toString());
	}

	static class TaskCapturingExecutor extends ThreadPoolExecutor {

		Runnable capturedTask;

		public TaskCapturingExecutor() {
			super(1, 1, 0L, TimeUnit.DAYS, new LinkedBlockingQueue<>(1));
		}

		@Override protected void beforeExecute(Thread worker, Runnable task) {
			capturedTask = task;
		}
	}




	@Test
	public void testAwaitableOfMonitorConditionWithInterrupt() throws Throwable {
		final var monitor = new Object();
		final var threadStarted = new CountDownLatch(1);
		final var awaitingThread = new Thread(() -> {
			threadStarted.countDown();
			try {
				synchronized (monitor) {
					waitForMonitorCondition(monitor, () -> false, 0L);
				}
				fail("InterruptedException expected");
			} catch (InterruptedException expected) {
			} catch (Throwable e) {
				asyncError = e;
			}
		});
		awaitingThread.start();
		assertTrue("awaitingThread should start", threadStarted.await(100L, TimeUnit.MILLISECONDS));

		awaitingThread.interrupt();
		awaitingThread.join(20L);
		assertFalse("awaitingThread should exit after an interrupt", awaitingThread.isAlive());

		if (asyncError != null) throw asyncError;
	}



	@Test
	public void testAwaitableOfMonitorConditionWithTimeout() throws Throwable {
		final var monitor = new Object();
		final var awaitingThread = new Thread(() -> {
			try {
				synchronized (monitor) {
					assertFalse("awaiting should fail",
							waitForMonitorCondition(monitor, () -> false, 20L));
				}
			} catch (Throwable e) {
				asyncError = e;
			}
		});

		awaitingThread.start();
		awaitingThread.join(100L);
		assertFalse("awaitingThread should exit after the timeout", awaitingThread.isAlive());

		if (asyncError != null) throw asyncError;
	}



	@Test
	public void testAwaitableOfMonitorCondition() throws Throwable {
		final var monitor = new Object();
		final boolean[] conditionHolder = {false};
		final var threadStarted = new CountDownLatch(1);
		final var awaitingThread = new Thread(() -> {
			threadStarted.countDown();
			try {
				synchronized (monitor) {
					assertTrue("awaiting should succeed",
							waitForMonitorCondition(monitor, () -> conditionHolder[0], 1000L));
				}
			} catch (Throwable e) {
				asyncError = e;
			}
		});
		awaitingThread.start();
		assertTrue("awaitingThread should start", threadStarted.await(100L, TimeUnit.MILLISECONDS));

		synchronized (monitor) {
			monitor.notify();
		}
		awaitingThread.join(50L);
		assertTrue("notifying awaitingThread without switching condition should have no effect",
				awaitingThread.isAlive());

		synchronized (monitor) {
			conditionHolder[0] = true;
			monitor.notify();
		}
		awaitingThread.join(50L);
		assertFalse("after switching condition awaitingThread should exit",
				awaitingThread.isAlive());

		if (asyncError != null) throw asyncError;
	}
}
