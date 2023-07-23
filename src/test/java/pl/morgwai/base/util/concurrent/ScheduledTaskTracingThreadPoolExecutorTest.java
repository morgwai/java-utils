// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;

import org.junit.Test;
import pl.morgwai.base.util.concurrent.ScheduledTaskTracingThreadPoolExecutor
		.DecomposableRunnableScheduledFuture;

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
		assertTrue("wrappedScheduledTask should be a DecomposableRunnableScheduledFuture",
				wrappedScheduledTask instanceof DecomposableRunnableScheduledFuture);
		return ((DecomposableRunnableScheduledFuture<?>) wrappedScheduledTask)
				.getWrappedScheduledTask();
	}



	@Test
	public void testStuckScheduledTask()
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

		final var scheduledFuture = scheduler.scheduleWithFixedDelay(
				scheduledTask, 0L, delayMillis, TimeUnit.MILLISECONDS);
		assertTrue("scheduledTask should run " + numberOfUnblockedRuns + " times without blocking",
				taskRunsTheBlockingCycleLatch.await(
						(delayMillis * numberOfUnblockedRuns) + 20L, TimeUnit.MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse("scheduledFuture should not complete",
				scheduledFuture.isDone());

		testSubject.shutdownNow();
		assertTrue("aftermath data should be present after the forced shutdown",
				testSubject.getForcedShutdownAftermath().isPresent());
		final var aftermath = testSubject.getForcedShutdownAftermath().get();
		assertEquals("1 task should be running in the aftermath", 1, aftermath.runningTasks.size());
		final var runningTask = unwrapIfScheduled(aftermath.runningTasks.get(0));
		assertSame("runningTask should be wrapping scheduledTask",
				scheduledTask, runningTask);
		try {
			scheduledFuture.get(20L, TimeUnit.MILLISECONDS);
			fail("CancellationException expected");
		} catch (CancellationException expected) {}
		assertTrue("scheduledFuture should complete after the forced shutdown",
				scheduledFuture.isDone());
		assertTrue("executor should terminate after the forced shutdown",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
	}
}
