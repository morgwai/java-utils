// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;
import java.util.function.*;



/**
 * Various concurrent utility functions.
 */
public interface ConcurrentUtils {



	/**
	 * Convenient version of {@link CompletableFuture#supplyAsync(Supplier, Executor)} that takes a
	 * {@link Callable} instead of a {@link Supplier}. If {@link Callable#call() task.call()} throws
	 * an exception, it will be pipelined to
	 * {@link CompletableFuture#handle(BiFunction) handle(...)} /
	 * {@link CompletableFuture#whenComplete(BiConsumer) whenComplete(...)} /
	 * {@link CompletableFuture#exceptionally(Function) exceptionally(...)} chained calls.
	 * <p>
	 * In case {@code executor} rejects {@code task}, its {@link RejectedExecutionHandler} will
	 * receive {@code task} wrapped with a {@link RunnableCallable}.</p>
	 */
	static <T> CompletableFuture<T> completableFutureSupplyAsync(
		Callable<T> task,
		Executor executor
	) {
		final var result = new CompletableFuture<T>();
		executor.execute(new RunnableCallable<>(task) {
			@Override public void run() {
				try {
					result.complete(wrappedTask.call());
				} catch (Exception e) {
					result.completeExceptionally(e);
				}
			}
		});
		return result;
	}



	/**
	 * Wrapper for {@link Callable} tasks passed to
	 * {@link #completableFutureSupplyAsync(Callable, Executor)}.
	 */
	abstract class RunnableCallable<T> implements Runnable {

		public Callable<T> getWrappedTask() { return wrappedTask; }
		public final Callable<T> wrappedTask;

		public RunnableCallable(Callable<T> taskToWrap) {
			this.wrappedTask = taskToWrap;
		}

		@Override public String toString() {
			return wrappedTask.toString();
		}
	}
}
