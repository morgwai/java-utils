// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;

import org.junit.*;
import pl.morgwai.base.util.concurrent.ConcurrentUtils.RunnableCallable;

import static org.junit.Assert.*;
import static pl.morgwai.base.util.concurrent.ConcurrentUtils.completableFutureSupplyAsync;



public class TaskTracingThreadPoolExecutorTest {



	protected TaskTracingExecutor testSubject;



	@Before
	public void setup() {
		testSubject = new TaskTracingThreadPoolExecutor(
				1, 1, 0L, TimeUnit.DAYS, new LinkedBlockingQueue<>(1));
	}

	@After
	public void shutdownNowIfNeeded() {
		if ( !testSubject.isTerminated()) testSubject.shutdownNow();
	}



	@Test
	public void testExecuteCallable() throws InterruptedException {
		final var thrown = new Exception("thrown");
		final Throwable[] caughtHolder = new Throwable[1];
		final var completionLatch = new CountDownLatch(1);

		final var execution = completableFutureSupplyAsync(() -> { throw thrown; }, testSubject);
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
	public void testStuckCallable() throws InterruptedException {
		final var latchAwaitingTaskStartedLatch = new CountDownLatch(1);
		final var completionLatch = new CountDownLatch(1);
		final Callable<String> latchAwaitingTask = new Callable<>() {
			@Override public String call() throws Exception {
				latchAwaitingTaskStartedLatch.countDown();
				completionLatch.await();
				return "";
			}
			@Override public String toString() {
				return "latchAwaitingTask";
			}
		};
		final Callable<Integer> instantTask = new Callable<>() {
			@Override public Integer call()  {
				return 0;
			}
			@Override public String toString() {
				return "instantTask";
			}
		};

		final var latchAwaitingTaskExecution =
				completableFutureSupplyAsync(latchAwaitingTask, testSubject);
		final var instantTaskExecution = completableFutureSupplyAsync(instantTask, testSubject);
		assertTrue("latchAwaitingTask should be started",
				latchAwaitingTaskStartedLatch.await(20L, TimeUnit.MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse("latchAwaitingTaskExecution should not complete",
				latchAwaitingTaskExecution.isDone());
		assertFalse("instantTask should not be executed", instantTaskExecution.isDone());

		testSubject.shutdownNow();
		assertTrue("aftermath data should be present after the forced shutdown",
				testSubject.getForcedShutdownAftermath().isPresent());
		final var aftermath = testSubject.getForcedShutdownAftermath().get();
		assertEquals("1 task should be running in the aftermath", 1, aftermath.runningTasks.size());
		assertEquals("1 task should be unexecuted in the aftermath",
				1, aftermath.unexecutedTasks.size());
		final var runningTask = unwrapIfScheduled(aftermath.runningTasks.get(0));
		final var unexecutedTask = unwrapIfScheduled(aftermath.unexecutedTasks.get(0));
		assertTrue("runningTask should be a RunnableCallable instance",
				runningTask instanceof RunnableCallable);
		assertTrue("unexecutedTask should be a RunnableCallable instance",
				unexecutedTask instanceof RunnableCallable);
		assertSame("runningTask should be latchAwaitingTask",
				latchAwaitingTask, ((RunnableCallable<?>) runningTask).getWrappedTask());
		assertSame("unexecutedTask should be instantTask",
				instantTask, ((RunnableCallable<?>) unexecutedTask).getWrappedTask());
		try {
			latchAwaitingTaskExecution.get(20L, TimeUnit.MILLISECONDS);
			fail("latchAwaitingTaskExecution should complete exceptionally");
		} catch (TimeoutException e) {
			fail("latchAwaitingTaskExecution should complete after the forced shutdown");
		} catch (ExecutionException e) {
			assertTrue(
				"latchAwaitingTask should throw an InterruptedException after forced shutdown",
				e.getCause() instanceof InterruptedException
			);
		}
		assertTrue("executor should terminate after the forced shutdown",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
	}



	@Test
	public void testStuckUninterruptibleCallable()
			throws InterruptedException, ExecutionException, TimeoutException {
		final var result = "result";
		final var latchAwaitingTaskStartedLatch = new CountDownLatch(1);
		final var completionLatch = new CountDownLatch(1);
		final Callable<String> latchAwaitingTask = new Callable<>() {
			@Override public String call() {
				latchAwaitingTaskStartedLatch.countDown();
				boolean completionLatchSwitched = false;
				while ( !completionLatchSwitched) {
					try {
						completionLatch.await();
						completionLatchSwitched = true;
					} catch (InterruptedException expected) {}
				}
				return result;
			}
			@Override public String toString() {
				return "latchAwaitingTask";
			}
		};
		final Callable<Integer> instantTask = new Callable<>() {
			@Override public Integer call()  {
				return 0;
			}
			@Override public String toString() {
				return "instantTask";
			}
		};

		final var latchAwaitingTaskExecution =
				completableFutureSupplyAsync(latchAwaitingTask, testSubject);
		final var instantTaskExecution = completableFutureSupplyAsync(instantTask, testSubject);
		assertTrue("latchAwaitingTask should be started",
				latchAwaitingTaskStartedLatch.await(20L, TimeUnit.MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse("latchAwaitingTaskExecution should not complete",
				latchAwaitingTaskExecution.isDone());
		assertFalse("instantTask should not be executed", instantTaskExecution.isDone());

		testSubject.shutdownNow();
		assertTrue("aftermath data should be present after the forced shutdown",
				testSubject.getForcedShutdownAftermath().isPresent());
		final var aftermath = testSubject.getForcedShutdownAftermath().get();
		assertEquals("1 task should be running in the aftermath", 1, aftermath.runningTasks.size());
		assertEquals("1 task should be unexecuted in the aftermath",
				1, aftermath.unexecutedTasks.size());
		final var runningTask = unwrapIfScheduled(aftermath.runningTasks.get(0));
		final var unexecutedTask = unwrapIfScheduled(aftermath.unexecutedTasks.get(0));
		assertTrue("runningTask should be a RunnableCallable instance",
				runningTask instanceof RunnableCallable);
		assertTrue("unexecutedTask should be a RunnableCallable instance",
				unexecutedTask instanceof RunnableCallable);
		assertSame("runningTask should be latchAwaitingTask",
				latchAwaitingTask, ((RunnableCallable<?>) runningTask).getWrappedTask());
		assertSame("unexecutedTask should be instantTask",
				instantTask, ((RunnableCallable<?>) unexecutedTask).getWrappedTask());
		assertFalse("executor should not terminate even after the forced shutdown",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse("latchAwaitingTaskExecution should not complete even after the forced shutdown",
				latchAwaitingTaskExecution.isDone());

		testSubject.shutdownNow();
		final var aftermath2 = testSubject.getForcedShutdownAftermath().get();
		assertEquals("1 task should be running in the aftermath2",
				1, aftermath2.runningTasks.size());
		assertTrue("there should be no unexecuted tasks in the aftermath2",
				aftermath2.unexecutedTasks.isEmpty());
		final var runningTask2 = unwrapIfScheduled(aftermath2.runningTasks.get(0));
		assertTrue("runningTask2 should be a RunnableCallable instance",
				runningTask2 instanceof RunnableCallable);
		assertSame("runningTask2 should be latchAwaitingTask",
				latchAwaitingTask, ((RunnableCallable<?>) runningTask2).getWrappedTask());
		assertFalse("executor should not terminate even after the 2nd forced shutdown",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse(
			"latchAwaitingTaskExecution should not complete even after the 2nd forced shutdown",
			latchAwaitingTaskExecution.isDone()
		);

		completionLatch.countDown();
		assertSame("result of latchAwaitingTaskExecution should be the same as returned",
				result, latchAwaitingTaskExecution.get(20L, TimeUnit.MILLISECONDS));
		assertTrue("executor should terminate after completionLatch is switched",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
	}



	/** For {@link ScheduledTaskTracingThreadPoolExecutorTest} */
	protected Object unwrapIfScheduled(Runnable task) {
		return task;
	}



	// TODO: execution rejection tests
}
