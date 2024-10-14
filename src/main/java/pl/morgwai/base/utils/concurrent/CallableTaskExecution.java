// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.*;
import java.util.function.*;



/**
 * Adapter for a {@link Callable} that allows to pass it to {@link Executor#execute(Runnable)} and
 * access  its result using {@link java.util.concurrent.CompletableFuture} API.
 */
public class CallableTaskExecution<T> extends CompletableFuture<T> implements RunnableFuture<T> {



	public Callable<T> getTask() { return task; }
	public final Callable<T> task;



	public CallableTaskExecution(Callable<T> task) {
		this.task = task;
	}



	/**
	 * Calls {@link #task} and {@link #complete(Object) completes} this {@code CompletableFuture}
	 * with the result.
	 * If {@link #task} throws, {@link CompletableFuture#completeExceptionally(Throwable)} is called
	 * instead.
	 */
	@Override
	public void run() {
		try {
			complete(task.call());
		} catch (Throwable e) {
			completeExceptionally(e);
		}
	}



	@Override
	public String toString() {
		return "CallableTaskExecution { task = " + task + " }";
	}



	/**
	 * Similar to {@link CompletableFuture#supplyAsync(Supplier, Executor)}, but takes a
	 * {@link Callable} argument.
	 * If {@link Callable#call() task.call()} throws, the {@link Throwable} will be passed to
	 * {@link #completeExceptionally(Throwable)} directly (without wrapping with a
	 * {@link CompletionException} unlike
	 * {@link CompletableFuture#supplyAsync(Supplier, Executor) supplyAsync(...)} does with
	 * {@link RuntimeException}s).
	 * <p>
	 * Internally {@code task} is wrapped with a {@link CallableTaskExecution}, so in case
	 * {@code executor}
	 * {@link RejectedExecutionHandler#rejectedExecution(Runnable, ThreadPoolExecutor) rejects}
	 * {@code task} or {@link ExecutorService#shutdownNow() executor.shutdownNow()} is called,
	 * {@link CallableTaskExecution#getTask()} may be used to obtain the original.</p>
	 */
	public static <R> CallableTaskExecution<R> callAsync(Callable<R> task, Executor executor) {
		final var taskExecution = new CallableTaskExecution<>(task);
		executor.execute(taskExecution);
		return taskExecution;
	}



	/**
	 * Similar as {@link #callAsync(Callable, Executor)}, but uses
	 * {@link CompletableFuture#defaultExecutor()}.
	 */
	public static <R> CallableTaskExecution<R> callAsync(Callable<R> task) {
		final var taskExecution = new CallableTaskExecution<>(task);
		taskExecution.defaultExecutor().execute(taskExecution);
		return taskExecution;
	}
}
