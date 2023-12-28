// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static org.junit.Assert.*;
import static pl.morgwai.base.utils.concurrent.ConcurrentUtils.waitForMonitorCondition;



public class ConcurrentUtilsTests {



	Throwable asyncError;



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
				threadStarted.await(100L, MILLISECONDS));

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
				threadStarted.await(100L, MILLISECONDS));

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
