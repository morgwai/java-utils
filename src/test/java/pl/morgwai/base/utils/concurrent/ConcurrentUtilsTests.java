// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.*;

import org.junit.Test;
import pl.morgwai.base.utils.concurrent.ConcurrentUtils.RunnableCallable;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static pl.morgwai.base.utils.concurrent.ConcurrentUtils.*;



public class ConcurrentUtilsTests {



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
	public void testCompletableFutureSupplyAsyncReturnsResultOfSuppliedCallable() throws Exception {
		final var result = "result";
		final var testTask = new Callable<String>() {
			@Override public String call() {
				return result;
			}
			@Override public String toString() {
				return "testTask";
			}
		};

		final var execution = completableFutureSupplyAsync(
				testTask, Executors.newSingleThreadExecutor());
		assertSame("result of execution should be the same as returned by testTask",
				result, execution.get(50L, TimeUnit.MILLISECONDS));
	}



	@Test
	public void testCompletableFutureSupplyAsyncWrapsTasksWithRunnableCallable() throws Exception {
		final var executor = Executors.newSingleThreadExecutor();
		final var blockingTaskStarted = new CountDownLatch(1);
		final var taskBlockingLatch = new CountDownLatch(1);
		completableFutureSupplyAsync(  // make executor's thread busy
			() -> {
				blockingTaskStarted.countDown();
				taskBlockingLatch.await();
				return "";
			},
			executor
		);
		final var testTask = new Callable<String>() {
			@Override public String call() {
				return "";
			}
			@Override public String toString() {
				return "testTask";
			}
		};
		completableFutureSupplyAsync(testTask, executor);
		assertTrue("blocking task should start",
				blockingTaskStarted.await(20L, TimeUnit.MILLISECONDS));

		final var unexecutedTasks = executor.shutdownNow();
		assertEquals("there should be 1 unexecuted task after shutdownNow()",
				1, unexecutedTasks.size());
		assertTrue("unexecutedTask should be a RunnableCallable instance",
				unexecutedTasks.get(0) instanceof RunnableCallable);
		@SuppressWarnings("unchecked")
		final var unexecutedTask = (RunnableCallable<String>) unexecutedTasks.get(0);
		assertSame("unexecutedTask should be wrapping testTask",
				testTask, unexecutedTask.getWrappedTask());
		assertSame("RunnableCallable should delegate toString to the original task",
				testTask.toString(), unexecutedTask.toString());
	}



	@Test
	public void testCompletableFutureSupplyAsyncPropagatesExecutorException()
			throws InterruptedException {
		final var executor = new ThreadPoolExecutor(
				1, 1, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1));
		final var blockingTaskStarted = new CountDownLatch(1);
		final var taskBlockingLatch = new CountDownLatch(1);
		executor.execute(() -> {  // make executor's thread busy
			blockingTaskStarted.countDown();
			try {
				taskBlockingLatch.await();
			} catch (InterruptedException ignored) {}
		});
		executor.execute(() -> {});  // fill the queue
		assertTrue("blocking task should start",
				blockingTaskStarted.await(20L, TimeUnit.MILLISECONDS));

		try {
			completableFutureSupplyAsync(() -> "", executor);
			fail("supplyAsync(...) should propagate an Exception thrown by the executor");
		} catch (RejectedExecutionException expected) {
		} finally {
			taskBlockingLatch.countDown();
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
		assertTrue("awaitingThread should start",
				threadStarted.await(100L, TimeUnit.MILLISECONDS));

		awaitingThread.interrupt();
		awaitingThread.join(20L);
		assertFalse("awaitingThread should exit after an interrupt",
				awaitingThread.isAlive());

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
		assertFalse("awaitingThread should exit after the timeout",
				awaitingThread.isAlive());

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
		assertTrue("awaitingThread should start",
				threadStarted.await(100L, TimeUnit.MILLISECONDS));

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
