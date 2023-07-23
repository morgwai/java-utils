// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;

import org.junit.Test;
import pl.morgwai.base.util.concurrent.ScheduledTaskTracingThreadPoolExecutor.ScheduledExecution;

import static org.junit.Assert.*;
import static org.junit.Assert.assertSame;



public class ScheduledTaskTracingThreadPoolExecutorTest extends TaskTracingThreadPoolExecutorTest {



	ScheduledTaskTracingThreadPoolExecutor scheduler;  // same as testSubject



	@Override
	public void setup() {
		scheduler = new ScheduledTaskTracingThreadPoolExecutor(1);
		testSubject = scheduler;
	}



	@Override
	protected Object unwrapIfScheduled(Runnable wrappedScheduledTask) {
		assertTrue("wrappedScheduledTask should be a ScheduledExecution",
				wrappedScheduledTask instanceof ScheduledExecution);
		return ((ScheduledExecution<?>) wrappedScheduledTask).getTask();
	}



	@Test
	public void testStuckTaskScheduledWithFixedDelay()
			throws InterruptedException, ExecutionException, TimeoutException {
		final var numberOfUnblockedRuns = 2;
		final var taskRunsTheBlockingCycleLatch = new CountDownLatch(1);
		final var completionLatch = new CountDownLatch(1);
		final var scheduledTask = new Runnable() {

			int count = 0;

			@Override public void run() {
				count++;
				if (count <= numberOfUnblockedRuns) return;
				taskRunsTheBlockingCycleLatch.countDown();
				try {
					completionLatch.await();
				} catch (InterruptedException expected) {}
			}

			@Override public String toString() {
				return "scheduledTask";
			}
		};
		final var delayMillis = 10L;

		final var scheduledExecution = scheduler.scheduleWithFixedDelay(
				scheduledTask, 0L, delayMillis, TimeUnit.MILLISECONDS);
		assertTrue("scheduledTask should run " + numberOfUnblockedRuns + " times without blocking",
				taskRunsTheBlockingCycleLatch.await(
						(delayMillis * numberOfUnblockedRuns) + 20L, TimeUnit.MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse("scheduledExecution should not complete",
				scheduledExecution.isDone());

		testSubject.shutdownNow();
		assertTrue("aftermath data should be present after the forced shutdown",
				testSubject.getForcedShutdownAftermath().isPresent());
		final var aftermath = testSubject.getForcedShutdownAftermath().get();
		assertEquals("1 task should be running in the aftermath", 1, aftermath.runningTasks.size());
		final var runningTask = unwrapIfScheduled(aftermath.runningTasks.get(0));
		assertSame("runningTask should be wrapping scheduledTask", scheduledTask, runningTask);
		try {
			scheduledExecution.get(20L, TimeUnit.MILLISECONDS);
			fail("CancellationException expected");
		} catch (CancellationException expected) {}
		assertTrue("scheduledExecution should complete after the forced shutdown",
				scheduledExecution.isDone());
		assertTrue("executor should terminate after the forced shutdown",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
	}



	@Test
	public void testStuckScheduledCallable()
			throws InterruptedException, ExecutionException, TimeoutException {
		final var taskStartedLatch = new CountDownLatch(1);
		final var completionLatch = new CountDownLatch(1);
		final var result = "result";
		final var scheduledTask = new Callable<String>() {

			@Override public String call() {
				taskStartedLatch.countDown();
				try {
					completionLatch.await();
				} catch (InterruptedException expected) {}
				return result;
			}

			@Override public String toString() {
				return "scheduledTask";
			}
		};
		final var delayMillis = 10L;

		final var scheduledExecution = scheduler.schedule(
				scheduledTask, delayMillis, TimeUnit.MILLISECONDS);
		assertTrue("scheduledTask should start",
				taskStartedLatch.await(delayMillis + 20L, TimeUnit.MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse("scheduledExecution should not complete",
				scheduledExecution.isDone());

		testSubject.shutdownNow();
		assertTrue("aftermath data should be present after the forced shutdown",
				testSubject.getForcedShutdownAftermath().isPresent());
		final var aftermath = testSubject.getForcedShutdownAftermath().get();
		assertEquals("1 task should be running in the aftermath", 1, aftermath.runningTasks.size());
		final var runningTask = unwrapIfScheduled(aftermath.runningTasks.get(0));
		assertSame("runningTask should be wrapping scheduledTask", scheduledTask, runningTask);
		assertSame("scheduledExecution should return the same result as scheduledTask",
				result, scheduledExecution.get(20L, TimeUnit.MILLISECONDS));
		assertTrue("executor should terminate after the forced shutdown",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
	}
}
