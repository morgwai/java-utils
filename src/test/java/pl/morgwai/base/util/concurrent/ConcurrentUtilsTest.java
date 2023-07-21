// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;

import org.junit.Test;
import pl.morgwai.base.util.concurrent.ConcurrentUtils.RunnableCallable;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;



public class ConcurrentUtilsTest {



	@Test
	public void testCompletableFutureSupplyAsyncThrowing() throws InterruptedException {
		final var thrown = new Exception("thrown");
		final Throwable[] caughtHolder = new Throwable[1];
		final var completionLatch = new CountDownLatch(1);

		final var execution = ConcurrentUtils.completableFutureSupplyAsync(
			() -> { throw thrown; },
			Executors.newSingleThreadExecutor()
		);
		execution.whenComplete(
			(result, caught) -> {
				if (result == null) caughtHolder[0] = caught;
				completionLatch.countDown();
			}
		);

		assertTrue("the Callable task should complete",
				completionLatch.await(50L, TimeUnit.MILLISECONDS));
		assertTrue("execution should be marked as done", execution.isDone());
		assertSame("caught exception should be the same as thrown by the Callable task",
				thrown, caughtHolder[0]);
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
		final var executor = new IntrospectiveExecutor();

		final var execution = ConcurrentUtils.completableFutureSupplyAsync(testTask, executor);
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

	static class IntrospectiveExecutor extends ThreadPoolExecutor {

		Runnable capturedTask;

		public IntrospectiveExecutor() {
			super(1, 1, 0L, TimeUnit.DAYS, new LinkedBlockingQueue<>(1));
		}

		@Override
		protected void beforeExecute(Thread worker, Runnable task) {
			capturedTask = task;
		}
	}
}
