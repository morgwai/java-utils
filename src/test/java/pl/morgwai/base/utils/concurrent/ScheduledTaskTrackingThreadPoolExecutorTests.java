// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.Set;
import java.util.concurrent.*;
import org.junit.Test;

import pl.morgwai.base.utils.concurrent.ScheduledTaskTrackingThreadPoolExecutor.ScheduledExecution;
import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator
		.TaskHolder;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.*;



public class ScheduledTaskTrackingThreadPoolExecutorTests extends TaskTrackingHookableExecutorTests
{



	ScheduledTaskTrackingThreadPoolExecutor scheduler;  // same as testSubject



	@Override
	protected TaskTrackingExecutor createTestSubjectAndFinishSetup(
		int threadPoolSize,
		int queueSize,
		ThreadFactory threadFactory,
		RejectedExecutionHandler rejectionHandler
	) {
		scheduler = new ScheduledTaskTrackingThreadPoolExecutor(threadPoolSize, threadFactory);
		return scheduler;
	}



	@Override
	protected ThreadFactory getThreadFactory() {
		return scheduler.getThreadFactory();
	}



	@Override
	protected void setThreadFactory(ThreadFactory threadFactory) {
		scheduler.setThreadFactory(threadFactory);
	}



	@Override
	protected Set<TaskHolder> getRunningTaskHolders() {
		return scheduler.taskTrackingDecorator.runningTasks;
	}



	@Override
	protected Object unwrapIfScheduled(Runnable wrappedScheduledTask) {
		assertTrue("wrappedScheduledTask should be a ScheduledExecution",
				wrappedScheduledTask instanceof ScheduledExecution);
		return ((ScheduledExecution<?>) wrappedScheduledTask).getTask();
	}



	@Override
	public void testExecutionRejection() {
		// ScheduledExecutor's queue grows until out of memory
	}

	@Override
	protected Executor getExpectedRejectingExecutor() {
		throw new UnsupportedOperationException();
	}



	@Override
	public void testDyingWorkersDoNotLeakTaskHolders() {
		// task exceptions don't kill ScheduledExecutor workers
	}

	@Override
	public void testLaidOffWorkersDoNotLeakTaskHolders() {
		// ScheduledExecutor always uses fixed size pool
	}

	@Override
	protected void setMaxPoolSize(int maxPoolSize) {
		throw new UnsupportedOperationException();
	}



	@Override
	protected ExecutorService createStandardExecutor(int threadPoolSize, int numberOfTasks) {
		return new ScheduledThreadPoolExecutor(threadPoolSize);
	}

	@Override
	public void test10MNoopTasksPerformance() throws InterruptedException {
		expectedNoopTaskPerformanceFactor = 2.3d;
		super.test10MNoopTasksPerformance();
	}

	@Override
	public void test10k1msTasksPerformance() throws InterruptedException {
		expected1msTaskPerformanceFactor = 1.03d;
		super.test10k1msTasksPerformance();
	}



	@Test
	public void testStuckTaskScheduledWithFixedDelay()
			throws InterruptedException, ExecutionException, TimeoutException {
		final var numberOfUnblockedRuns = 2;
		final var taskEnteredTheBlockingCycle = new CountDownLatch(1);
		final var scheduledTask = new Runnable() {

			int count = 0;

			@Override public void run() {
				count++;
				if (count <= numberOfUnblockedRuns) return;
				taskEnteredTheBlockingCycle.countDown();
				try {
					taskBlockingLatch.await();
				} catch (InterruptedException expected) {}
			}

			@Override public String toString() {
				return "scheduledTask";
			}
		};
		final var delayMillis = 100L;

		final var scheduledExecution =
				scheduler.scheduleWithFixedDelay(scheduledTask, 0L, delayMillis, MILLISECONDS);
		assertTrue("scheduledTask should run " + numberOfUnblockedRuns + " times without blocking",
				taskEnteredTheBlockingCycle.await(
						(delayMillis * numberOfUnblockedRuns) + 20L, MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, MILLISECONDS));
		assertFalse("scheduledExecution should not complete",
				scheduledExecution.isDone());

		final var runningTasks = testSubject.getRunningTasks();
		testSubject.shutdownNow();
		assertEquals("1 task should be running in the aftermath",
				1, runningTasks.size());
		final var runningTask = unwrapIfScheduled(runningTasks.get(0));
		assertSame("runningTask should be wrapping scheduledTask",
				scheduledTask, runningTask);
		try {
			scheduledExecution.get(20L, MILLISECONDS);
			fail("CancellationException expected");
		} catch (CancellationException expected) {}
		assertTrue("scheduledExecution should complete after the forced shutdown",
				scheduledExecution.isDone());
		assertTrue("executor should terminate after the forced shutdown",
				testSubject.awaitTermination(20L, MILLISECONDS));
	}



	@Test
	public void testStuckScheduledCallable()
			throws InterruptedException, ExecutionException, TimeoutException {
		final var taskStarted = new CountDownLatch(1);
		final var result = "result";
		final var scheduledTask = new Callable<String>() {

			@Override public String call() {
				taskStarted.countDown();
				try {
					taskBlockingLatch.await();
				} catch (InterruptedException expected) {}
				return result;
			}

			@Override public String toString() {
				return "scheduledTask";
			}
		};
		final var delayMillis = 10L;

		final var scheduledExecution = scheduler.schedule(scheduledTask, delayMillis, MILLISECONDS);
		assertTrue("scheduledTask should start",
				taskStarted.await(delayMillis + 20L, MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, MILLISECONDS));
		assertFalse("scheduledExecution should not complete",
				scheduledExecution.isDone());

		final var runningTasks = testSubject.getRunningTasks();
		testSubject.shutdownNow();
		assertEquals("1 task should be running in the aftermath",
				1, runningTasks.size());
		final var runningTask = unwrapIfScheduled(runningTasks.get(0));
		assertSame("runningTask should be wrapping scheduledTask",
				scheduledTask, runningTask);
		assertSame("scheduledExecution should return the same result as scheduledTask",
				result, scheduledExecution.get(20L, MILLISECONDS));
		assertTrue("executor should terminate after the forced shutdown",
				testSubject.awaitTermination(20L, MILLISECONDS));
	}
}
