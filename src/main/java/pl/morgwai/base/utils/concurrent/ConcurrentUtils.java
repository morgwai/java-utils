// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.*;
import java.util.function.*;



/**
 * Various concurrent utility functions.
 */
public interface ConcurrentUtils {



	/**
	 * Similar to {@link Object#wait(long, int)}, but <b>not</b> affected by <i>spurious wakeup</i>
	 * nor by {@link Object#notifyAll() notifications} not related to {@code condition}.
	 * The calling thread must already own {@code monitor}'s lock, similarly when calling
	 * {@link Object#wait(long, int)}.
	 * <p>
	 * Internally this method performs {@link Object#wait(long, int) waiting} and {@code timeout}
	 * adjusting in a loop until either {@code condition} becomes {@code true} or an
	 * {@link InterruptedException} is thrown or {@code timeout} is exceeded (unless {@code 0} was
	 * passed).</p>
	 * <p>
	 * If {@code 0} was passed as {@code timeout}, the loop will only exit if
	 * {@code condition} becomes {@code true} or an {@link InterruptedException} is thrown,
	 * similarly to {@link Object#wait(long, int) wait(0L, 0)}.</p>
	 * @return {@code true} if {@code condition} became {@code true}, {@code false} if
	 *     {@code timeout} was exceeded.
	 */
	static boolean waitForMonitorCondition(
		Object monitor,
		BooleanSupplier condition,
		long timeout,
		TimeUnit unit
	) throws InterruptedException {
		var remainingNanos = unit.toNanos(timeout);
		final var deadlineNanos = System.nanoTime() + remainingNanos;
		while ( !condition.getAsBoolean()) {
			if (timeout == 0L) {
				monitor.wait();
			} else {
				if (remainingNanos <= 0L) return false;
				monitor.wait(
					remainingNanos / 1_000_000L,
					(int) (remainingNanos % 1_000_000L)
				);
				remainingNanos = deadlineNanos - System.nanoTime();
			}		}
		return true;
	}



	/**
	 * Calls {@link #waitForMonitorCondition(Object, BooleanSupplier, long, TimeUnit)
	 * waitForMonitorCondition(monitor, condition, timeoutMillis, TimeUnit.MILLISECONDS)}.
	 */
	static boolean waitForMonitorCondition(
		Object monitor,
		BooleanSupplier condition,
		long timeoutMillis
	) throws InterruptedException {
		return waitForMonitorCondition(monitor, condition, timeoutMillis, TimeUnit.MILLISECONDS);
	}
}
