// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;



/**
 * Closure {@link #await(long) awaiting} completion of a timed blocking operation, such as
 * {@link Thread#join(long)},
 * {@link ExecutorService#awaitTermination(long, TimeUnit) Executor.awaitTermination(...)} etc.
 * Useful for awaiting for multiple such operations within a joint timeout: see
 * {@link #awaitAll(long, TimeUnit, boolean, Iterator) awaitMultiple(...) function family}.
 */
@FunctionalInterface
public interface Awaitable {



	/**
	 * Awaits completion of a timed blocking operation.
	 * @return {@code true} if operation succeeds before {@code timeoutMillis} passes, {@code false}
	 *     otherwise.
	 */
	boolean await(long timeoutMillis) throws InterruptedException;



	/**
	 * Converts this {@code Awaitable} to {@link Awaitable.WithUnit}.
	 * <p>
	 * Timeout supplied to {@link Awaitable.WithUnit#await(long, TimeUnit)} is converted to millis
	 * using {@link TimeUnit#convert(long, TimeUnit)}, except when it is smaller than 1ms yet
	 * non-zero, in which case it will be rounded up to 1ms.</p>
	 */
	default Awaitable.WithUnit toAwaitableWithUnit() {
		return (timeout, unit) -> await(timeout == 0L ? 0L : Math.max(1L, unit.toMillis(timeout)));
	}



	/** A more precise and flexible {@link Awaitable}. */
	@FunctionalInterface
	interface WithUnit extends Awaitable {

		/** A version of {@link #await(long)} method with additional {@link TimeUnit} param. */
		boolean await(long timeout, TimeUnit unit) throws InterruptedException;

		/** Calls {@link #await(long, TimeUnit) await(timeout.toNanos(), TimeUnit.NANOSECONDS)}. */
		default boolean await(Duration timeout) throws InterruptedException {
			return await(timeout.toNanos(), NANOSECONDS);
		}

		/** Calls {@link #await(long, TimeUnit) await(timeoutMillis, TimeUnit.MILLISECONDS)}. */
		@Override
		default boolean await(long timeoutMillis) throws InterruptedException {
			return await(timeoutMillis, MILLISECONDS);
		}

		/** Returns {@code this}. */
		@Override
		default Awaitable.WithUnit toAwaitableWithUnit() {
			return this;
		}
	}



	/**
	 * Creates an {@link Awaitable.WithUnit} of {@link Thread#join(long, int) joining a thread}.
	 * If {@code 0} is passed as {@code timeout}, the operation will wait forever for {@code thread}
	 * to finish, similarly to the semantics of {@link Thread#join(long) join(0)} (note that non of
	 * the {@link #awaitAll(long, TimeUnit, boolean, Iterator)} methods will ever pass
	 * {@code 0} to any of its operations as a result of real time flow, except if {@code 0} was
	 * originally passed as joint {@code timeout}).
	 * @return {@code !}{@link Thread#isAlive()}.
	 */
	static Awaitable.WithUnit ofJoin(Thread thread) {
		return (timeout, unit) -> {
			final var timeoutMillis = unit.toMillis(timeout);
			if (timeout == 0L || unit.ordinal() >= MILLISECONDS.ordinal()) {
				thread.join(timeoutMillis);
			} else {
				thread.join(timeoutMillis, (int) (unit.toNanos(timeout) % 1_000_000L));
			}
			return !thread.isAlive();
		};
	}



	/**
	 * Creates an {@link Awaitable.WithUnit} of {@link ExecutorService#shutdown() shutdown} and
	 * {@link ExecutorService#awaitTermination(long, TimeUnit) termination} of {@code executor}.
	 * {@link ExecutorService#shutdown()} should usually be called before the resulting
	 * {@code Awaitable} is passed to {@link #awaitAll(long, TimeUnit, boolean, Iterator)} for
	 * the actual shutdown sequence to be performed in parallel with other {@code Awaitable}
	 * operations.
	 */
	static Awaitable.WithUnit ofTermination(ExecutorService executor) {
		return (timeout, unit) -> {
			executor.shutdown();
			return executor.awaitTermination(timeout, unit);
		};
	}



	/**
	 * Creates an {@link Awaitable.WithUnit} of {@link ExecutorService#shutdown() shutdown} and
	 * {@link ExecutorService#awaitTermination(long, TimeUnit) termination} of {@code executor}, if
	 * it fails, calls {@link ExecutorService#shutdownNow() shutdownNow()}.
	 * {@link ExecutorService#shutdown()} should usually be called before the resulting
	 * {@code Awaitable} is passed to {@link #awaitAll(long, TimeUnit, boolean, Iterator)} for
	 * the actual shutdown sequence to be performed in parallel with other {@code Awaitable}
	 * operations.
	 * @return the result of
	 *     {@link ExecutorService#awaitTermination(long, TimeUnit) executor.awaitTermination(...)}.
	 */
	static Awaitable.WithUnit ofEnforcedTermination(ExecutorService executor) {
		return (timeout, unit) -> {
			try {
				executor.shutdown();
				return executor.awaitTermination(timeout, unit);
			} finally {
				if ( !executor.isTerminated()) executor.shutdownNow();
			}
		};
	}



	/**
	 * {@link #await(long) Awaits} up to {@code timeout} for all {@link Entry#operation operations}
	 * of {@code awaitableEntries} to complete.
	 * The operations should be initiated before passing them to this function, so that they can all
	 * run in the background: for example in case of
	 * {@link ExecutorService#awaitTermination(long, TimeUnit) awaiting termination} of a bunch of
	 * {@link ExecutorService Executors}, {@link ExecutorService#shutdown() shutdown()} should be
	 * called on each of them before passing them to this function.
	 * <p>
	 * If {@code timeout} passes before all operations are completed, the remaining ones will still
	 * be awaited for and given {@code 1} nanosecond timeout each. This allows any operations that
	 * have already been completed in the background, to report their successful status.</p>
	 * <p>
	 * If any of the operations throws an {@link InterruptedException}, then an
	 * {@link AwaitInterruptedException} will be eventually thrown by this method and all the
	 * corresponding {@link Entry#object objects} of such operations will be available via
	 * {@link AwaitInterruptedException#getInterrupted()}.<br/>
	 * If {@code continueOnInterrupt} is {@code false}, then an {@link AwaitInterruptedException} is
	 * thrown immediately and the remaining {@link Entry Entries} will be available via
	 * {@link AwaitInterruptedException#getUnexecuted()}.<br/>
	 * If {@code continueOnInterrupt} is {@code true}, then the remaining operations will still be
	 * awaited for and given {@code 1} nanosecond timeout each and also the current {@link Thread}
	 * will be marked as {@link Thread#interrupt() interrupted} before performing each one to avoid
	 * any further blocking and just collect statuses of those that have already been completed in
	 * the background before the interrupt.</p>
	 * <p>
	 * If {@code timeout} argument is {@code 0}, then all operations will receive {@code 0} timeout.
	 * Note that different methods may interpret it in different ways: <i>"return false if cannot
	 * complete operation immediately"</i> like
	 * {@link ExecutorService#awaitTermination(long, TimeUnit)} or <i>"block without a timeout until
	 * operation is completed"</i> like {@link Thread#join(long)}.</p>
	 * <p>
	 * Note: internally all time measurements are performed in nanoseconds, hence this function is
	 * not suitable for timeouts spanning longer than {@link Long#MAX_VALUE} of nanoseconds.</p>
	 * <p>
	 * Note: this is a "low-level" core version: there are several "frontend" functions defined in
	 * this class with more convenient API divided into 3 families:</p>
	 * <ul>
	 *   <li>a family that accepts varargs of {@link Entry awaitableEntries} that map objects to
	 *     {@link Awaitable Awaitable operations} to be performed. This family returns a
	 *     {@link List} of objects for which their respective {@link Awaitable operations} failed
	 *     (returned {@code false}).</li>
	 *   <li>a family that accepts {@link Stream} of {@link Entry awaitableEntries} that map objects
	 *     to {@link Awaitable Awaitable operations} to be performed. This family returns a
	 *     {@link List} of objects for which their respective {@link Awaitable operations} failed
	 *     (returned {@code false}).</li>
	 *   <li>a family that accepts varargs of {@link Awaitable Awaitable operations}. This family
	 *     returns {@code true} if all {@code operations} succeeded, {@code false} otherwise.</li>
	 * </ul>
	 * <p>
	 * Within each family there are variants that either accept {@code (long timeout, TimeUnit
	 * unit)} params or a single {@code long timeoutMillis} param and variants that either accept
	 * {@code boolean continueOnInterrupt} param or always pass {@code true}.</p>
	 * @return an empty {@link List} if all {@link Awaitable operations} completed successfully,
	 *     otherwise a {@link List} of object whose operations failed.
	 * @throws AwaitInterruptedException if any of the operations throws an
	 *     {@link InterruptedException}.
	 */
	static <T> List<T> awaitAll(
		long timeout,
		TimeUnit unit,
		boolean continueOnInterrupt,
		Iterator<Entry<T>> awaitableEntries
	) throws AwaitInterruptedException {
		var remainingNanos =  unit.toNanos(timeout);
		final var deadlineNanos = System.nanoTime() + remainingNanos;
		final var currentThread = Thread.currentThread();
		final var failedTasks = new LinkedList<T>();
		final var interruptedTasks = new LinkedList<T>();
		boolean interrupted = false;
		while (awaitableEntries.hasNext()) {
			final var awaitableEntry = awaitableEntries.next();
			if (interrupted) currentThread.interrupt();  // ensure no blocking by operation
			if (remainingNanos > 1L) {
				remainingNanos = deadlineNanos - System.nanoTime();
				if (remainingNanos < 1L) remainingNanos = 1L;
			}
			try {
				if (
					!awaitableEntry.operation.toAwaitableWithUnit()
							.await(remainingNanos, NANOSECONDS)
				) {
					failedTasks.add(awaitableEntry.object);
				}
			} catch (InterruptedException e) {
				interruptedTasks.add(awaitableEntry.object);
				if ( !continueOnInterrupt) {
					throw new AwaitInterruptedException(
							failedTasks, interruptedTasks, awaitableEntries);
				}
				remainingNanos = 1L;
				interrupted = true;
			}
		}
		if (interrupted) {
			final var ignored = Thread.interrupted();
			throw new AwaitInterruptedException(failedTasks, interruptedTasks, awaitableEntries);
		}
		return failedTasks;
	}



	/**
	 * Maps an {@link #object object} to an {@link #operation Awaitable operation} that
	 * {@link Awaitable#awaitAll(long, TimeUnit, boolean, Iterator)} function will
	 * {@link #await(long) await} for.
	 */
	class Entry<T> {

		public final T object;
		public T getObject() { return object; }

		public final Awaitable operation;
		public Awaitable getOperation() { return operation; }

		public Entry(T object, Awaitable operation) {
			this.object = object;
			this.operation = operation;
		}
	}

	static <T> Entry<T> newEntry(T object, Awaitable operation) {
		return new Entry<>(object, operation);
	}



	/** Saves a few chars when {@link Stream#map(Function) mapping streams} to {@link Entry}s. */
	static <T> Function<T, Entry<T>> entryMapper(Function<? super T, ? extends Awaitable> adapter) {
		return (t) -> newEntry(t, adapter.apply(t));
	}



	/**
	 * {@link InterruptedException} that contains results of {@link Awaitable Awaitable operations}
	 * passed to a
	 * {@link Awaitable#awaitAll(long, TimeUnit, boolean, Iterator) awaitMultipe(...)} call
	 * that was later interrupted.
	 */
	class AwaitInterruptedException extends InterruptedException {

		public final List<?> failed;
		public List<?> getFailed() { return failed; }

		public final List<?> interrupted;
		public List<?> getInterrupted() { return interrupted; }

		public final Iterator<Entry<?>> unexecuted;
		public Iterator<Entry<?>> getUnexecuted() { return unexecuted; }

		public <T> AwaitInterruptedException(
			List<T> failed,
			List<T> interrupted,
			Iterator<Entry<T>> unexecuted
		) {
			this.failed = failed;
			this.interrupted = interrupted;
			@SuppressWarnings("unchecked")
			final Iterator<Entry<?>> tmp = (Iterator<Entry<?>>) (Iterator<?>) unexecuted;
			this.unexecuted = tmp;
		}
	}



	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	@SafeVarargs
	static <T> List<T> awaitAll(
		long timeout,
		TimeUnit unit,
		boolean continueOnInterrupt,
		Entry<T>... awaitableEntries
	) throws AwaitInterruptedException {
		return awaitAll(
			timeout,
			unit,
			continueOnInterrupt,
			Arrays.asList(awaitableEntries).iterator()
		);
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	@SafeVarargs
	static <T> List<T> awaitAll(
		long timeoutMillis,
		boolean continueOnInterrupt,
		Entry<T>... awaitableEntries
	) throws AwaitInterruptedException {
		return awaitAll(
			timeoutMillis,
			MILLISECONDS,
			continueOnInterrupt,
			Arrays.asList(awaitableEntries).iterator()
		);
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	@SafeVarargs
	static <T> List<T> awaitAll(long timeout, TimeUnit unit, Entry<T>... awaitableEntries)
			throws AwaitInterruptedException {
		return awaitAll(timeout, unit, true, Arrays.asList(awaitableEntries).iterator());
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	@SafeVarargs
	static <T> List<T> awaitAll(long timeoutMillis, Entry<T>... awaitableEntries)
			throws AwaitInterruptedException {
		return awaitAll(
			timeoutMillis,
			MILLISECONDS,
			true,
			Arrays.asList(awaitableEntries).iterator()
		);
	}



	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static <T> List<T> awaitAll(
		long timeout,
		TimeUnit unit,
		boolean continueOnInterrupt,
		Stream<Entry<T>> awaitableEntries
	) throws AwaitInterruptedException {
		return awaitAll(timeout, unit, continueOnInterrupt, awaitableEntries.iterator());
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static <T> List<T> awaitAll(
		long timeoutMillis,
		boolean continueOnInterrupt,
		Stream<Entry<T>> awaitableEntries
	) throws AwaitInterruptedException {
		return awaitAll(
			timeoutMillis,
			MILLISECONDS,
			continueOnInterrupt,
			awaitableEntries.iterator()
		);
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static <T> List<T> awaitAll(long timeout, TimeUnit unit, Stream<Entry<T>> awaitableEntries)
			throws AwaitInterruptedException {
		return awaitAll(timeout, unit, true, awaitableEntries.iterator());
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static <T> List<T> awaitAll(long timeoutMillis, Stream<Entry<T>> awaitableEntries)
			throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, MILLISECONDS, true, awaitableEntries.iterator());
	}



	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static boolean awaitAll(
		long timeout,
		TimeUnit unit,
		boolean continueOnInterrupt,
		Awaitable... operations
	) throws AwaitInterruptedException {
		return (
			awaitAll(
				timeout,
				unit,
				continueOnInterrupt,
				Arrays.stream(operations)
					.map((operation) -> newEntry(operation, operation))
					.iterator()
			).isEmpty()
		);
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static boolean awaitAll(
		long timeoutMillis,
		boolean continueOnInterrupt,
		Awaitable... operations
	) throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, MILLISECONDS, continueOnInterrupt, operations);
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static boolean awaitAll(
		long timeout,
		TimeUnit unit,
		boolean continueOnInterrupt,
		Awaitable.WithUnit... operations
	) throws AwaitInterruptedException {
		return awaitAll(timeout, unit, continueOnInterrupt, (Awaitable[]) operations);
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static boolean awaitAll(
		long timeoutMillis,
		boolean continueOnInterrupt,
		Awaitable.WithUnit... operations
	) throws AwaitInterruptedException {
		return awaitAll(
			timeoutMillis,
			MILLISECONDS,
			continueOnInterrupt,
			(Awaitable[]) operations
		);
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static boolean awaitAll(long timeout, TimeUnit unit, Awaitable... operations)
			throws AwaitInterruptedException {
		return awaitAll(timeout, unit, true, operations);
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static boolean awaitAll(long timeoutMillis, Awaitable... operations)
			throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, MILLISECONDS, true, operations);
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static boolean awaitAll(long timeout, TimeUnit unit, Awaitable.WithUnit... operations)
			throws AwaitInterruptedException {
		return awaitAll(timeout, unit, true, (Awaitable[]) operations);
	}

	/** See {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	static boolean awaitAll(long timeoutMillis, Awaitable.WithUnit... operations)
			throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, MILLISECONDS, true, (Awaitable[]) operations);
	}



	/** @deprecated Use {@link #awaitAll(long, TimeUnit, boolean, Iterator)}. */
	@Deprecated(forRemoval = true)
	static <T> List<T> awaitMultiple(
		long timeout,
		TimeUnit unit,
		boolean continueOnInterrupt,
		Iterator<Entry<T>> awaitableEntries
	) throws AwaitInterruptedException {
		return awaitAll(timeout, unit, continueOnInterrupt, awaitableEntries);
	}

	/** @deprecated Use {@link #awaitAll(long, TimeUnit, boolean, Entry...)}. */
	@Deprecated(forRemoval = true)
	@SafeVarargs
	static <T> List<T> awaitMultiple(
		long timeout,
		TimeUnit unit,
		boolean continueOnInterrupt,
		Entry<T>... awaitableEntries
	) throws AwaitInterruptedException {
		return awaitAll(timeout, unit, continueOnInterrupt, awaitableEntries);
	}

	/** @deprecated Use {@link #awaitAll(long, boolean, Entry...)}. */
	@Deprecated(forRemoval = true)
	@SafeVarargs
	static <T> List<T> awaitMultiple(
		long timeoutMillis,
		boolean continueOnInterrupt,
		Entry<T>... awaitableEntries
	) throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, continueOnInterrupt, awaitableEntries);
	}

	/** @deprecated Use {@link #awaitAll(long, TimeUnit, Entry...)}. */
	@Deprecated(forRemoval = true)
	@SafeVarargs
	static <T> List<T> awaitMultiple(long timeout, TimeUnit unit, Entry<T>... awaitableEntries)
			throws AwaitInterruptedException {
		return awaitAll(timeout, unit, awaitableEntries);
	}

	/** @deprecated Use {@link #awaitAll(long, Entry...)}. */
	@Deprecated(forRemoval = true)
	@SafeVarargs
	static <T> List<T> awaitMultiple(long timeoutMillis, Entry<T>... awaitableEntries)
			throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, awaitableEntries);
	}

	/** @deprecated Use {@link #awaitAll(long, TimeUnit, boolean, Stream)}. */
	@Deprecated(forRemoval = true)
	static <T> List<T> awaitMultiple(
		long timeout,
		TimeUnit unit,
		boolean continueOnInterrupt,
		Stream<Entry<T>> awaitableEntries
	) throws AwaitInterruptedException {
		return awaitAll(timeout, unit, continueOnInterrupt, awaitableEntries);
	}

	/** @deprecated Use {@link #awaitAll(long, boolean, Stream)}. */
	@Deprecated(forRemoval = true)
	static <T> List<T> awaitMultiple(
		long timeoutMillis,
		boolean continueOnInterrupt,
		Stream<Entry<T>> awaitableEntries
	) throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, continueOnInterrupt, awaitableEntries);
	}

	/** @deprecated Use {@link #awaitAll(long, TimeUnit, Stream)}. */
	@Deprecated(forRemoval = true)
	static <T> List<T> awaitMultiple(long timeout, TimeUnit unit, Stream<Entry<T>> awaitableEntries)
			throws AwaitInterruptedException {
		return awaitAll(timeout, unit, awaitableEntries);
	}

	/** @deprecated Use {@link #awaitAll(long, Stream)}. */
	@Deprecated(forRemoval = true)
	static <T> List<T> awaitMultiple(long timeoutMillis, Stream<Entry<T>> awaitableEntries)
			throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, awaitableEntries);
	}

	/** @deprecated Use {@link #awaitAll(long, TimeUnit, boolean, Awaitable...)}. */
	@Deprecated(forRemoval = true)
	static boolean awaitMultiple(
		long timeout,
		TimeUnit unit,
		boolean continueOnInterrupt,
		Awaitable... operations
	) throws AwaitInterruptedException {
		return awaitAll(timeout, unit, continueOnInterrupt, operations);
	}

	/** @deprecated Use {@link #awaitAll(long, boolean, Awaitable...)}. */
	@Deprecated(forRemoval = true)
	static boolean awaitMultiple(
		long timeoutMillis,
		boolean continueOnInterrupt,
		Awaitable... operations
	) throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, continueOnInterrupt, operations);
	}

	/** @deprecated Use {@link #awaitAll(long, TimeUnit, boolean, Awaitable.WithUnit...)}. */
	@Deprecated(forRemoval = true)
	static boolean awaitMultiple(
		long timeout,
		TimeUnit unit,
		boolean continueOnInterrupt,
		Awaitable.WithUnit... operations
	) throws AwaitInterruptedException {
		return awaitAll(timeout, unit, continueOnInterrupt, operations);
	}

	/** @deprecated Use {@link #awaitAll(long, boolean, Awaitable.WithUnit...)}. */
	@Deprecated(forRemoval = true)
	static boolean awaitMultiple(
		long timeoutMillis,
		boolean continueOnInterrupt,
		Awaitable.WithUnit... operations
	) throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, continueOnInterrupt, operations);
	}

	/** @deprecated Use {@link #awaitAll(long, TimeUnit, Awaitable...)}. */
	@Deprecated(forRemoval = true)
	static boolean awaitMultiple(long timeout, TimeUnit unit, Awaitable... operations)
			throws AwaitInterruptedException {
		return awaitAll(timeout, unit, operations);
	}

	/** @deprecated Use {@link #awaitAll(long, Awaitable...)}. */
	@Deprecated(forRemoval = true)
	static boolean awaitMultiple(long timeoutMillis, Awaitable... operations)
			throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, operations);
	}

	/** @deprecated Use {@link #awaitAll(long, TimeUnit, Awaitable.WithUnit...)}. */
	@Deprecated(forRemoval = true)
	static boolean awaitMultiple(long timeout, TimeUnit unit, Awaitable.WithUnit... operations)
			throws AwaitInterruptedException {
		return awaitAll(timeout, unit, operations);
	}

	/** @deprecated Use {@link #awaitAll(long, Awaitable.WithUnit...)}. */
	@Deprecated(forRemoval = true)
	static boolean awaitMultiple(long timeoutMillis, Awaitable.WithUnit... operations)
			throws AwaitInterruptedException {
		return awaitAll(timeoutMillis, operations);
	}
}
