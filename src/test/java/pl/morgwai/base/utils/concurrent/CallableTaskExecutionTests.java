// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.*;

import org.junit.Test;

import static java.util.concurrent.TimeUnit.*;

import static org.junit.Assert.*;
import static pl.morgwai.base.utils.concurrent.CallableTaskExecution.callAsync;



public class CallableTaskExecutionTests {



	Throwable asyncError;



	@Test
	public void testCallAsyncPropagatesTaskResults() throws Exception {
		final var result = "result";
		final var execution = callAsync(() -> result);
		assertSame("result of execution should be the same as returned by the task",
			result, execution.get(50L, MILLISECONDS));
	}



	@Test
	public void testCallAsyncPropagatesTaskExceptions()
		throws InterruptedException, TimeoutException {
		final var thrown = new Exception("thrown");

		final var execution = callAsync(
			() -> { throw thrown; },
			Executors.newSingleThreadExecutor()
		).whenComplete(
			(result, caught) -> {
				if (result == null) asyncError = caught;
			}
		);
		try {
			execution.get(50L, MILLISECONDS);
			fail("execution of a throwing task should throw an ExecutionException");
		} catch (ExecutionException e) {
			assertSame("cause of the ExecutionException should be the same as thrown by the task",
					thrown, e.getCause());
		}
		assertSame("caught exception should be the same as thrown by the task",
				thrown, asyncError);
	}



	@Test
	public void testCallAsyncWrapsTasksWithCallableTaskExecution() throws Exception {
		final var executor = Executors.newSingleThreadExecutor();
		final var blockingTaskStarted = new CountDownLatch(1);
		final var taskBlockingLatch = new CountDownLatch(1);
		callAsync(  // make executor's thread busy
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
		callAsync(testTask, executor);
		assertTrue("blocking task should start",
				blockingTaskStarted.await(20L, MILLISECONDS));

		final var unexecutedTasks = executor.shutdownNow();
		assertEquals("there should be 1 unexecuted task after shutdownNow()",
				1, unexecutedTasks.size());
		assertTrue("unexecutedTask should be a CallableTaskExecution instance",
				unexecutedTasks.get(0) instanceof CallableTaskExecution);
		@SuppressWarnings("unchecked")
		final var unexecutedTask = (CallableTaskExecution<String>) unexecutedTasks.get(0);
		assertSame("unexecutedTask should be wrapping testTask",
				testTask, unexecutedTask.getTask());
		assertTrue("CallableTaskExecution should contain toString() of the original task",
				unexecutedTask.toString().contains(testTask.toString()));
	}



	@Test
	public void testCallAsyncPropagatesExecutorExceptions()
		throws InterruptedException {
		final var executor = new ThreadPoolExecutor(1, 1, 0L, DAYS, new LinkedBlockingQueue<>(1));
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
				blockingTaskStarted.await(20L, MILLISECONDS));

		try {
			callAsync(() -> "", executor);
			fail("a RejectedExecutionException thrown by the executor should be propagated");
		} catch (RejectedExecutionException expected) {
		} finally {
			taskBlockingLatch.countDown();
		}
	}
}
