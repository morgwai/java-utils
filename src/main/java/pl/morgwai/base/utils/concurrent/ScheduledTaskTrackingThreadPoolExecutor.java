// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator;



/**
 * A {@link ScheduledThreadPoolExecutor} that is also a {@link TaskTrackingExecutor}.
 * Decorates scheduled items with {@link ScheduledExecution}, so that the original tasks can be
 * obtained from the result of {@link #getRunningTasks()}.
 * <p>
 * <b>NOTE:</b> due to the design of
 * {@link ScheduledThreadPoolExecutor#decorateTask(Runnable, RunnableScheduledFuture) task
 * decorating in ScheduledThreadPoolExecutor} this class is <i>extremely</i> slow in case of
 * a large number of very tiny tasks. See
 * <a href="https://github.com/AdoptOpenJDK/openjdk-jdk11/blob/master/src/java.base/share/classes/
java/util/concurrent/ScheduledThreadPoolExecutor.java#L903-L915">
 * the comment with an explanation in the source</a>.</p>
 */
public class ScheduledTaskTrackingThreadPoolExecutor extends ScheduledThreadPoolExecutor
		implements TaskTrackingExecutor, TaskTrackingExecutorDecorator.HookableExecutor {



	final TaskTrackingExecutorDecorator taskTrackingDecorator;



	public ScheduledTaskTrackingThreadPoolExecutor(int corePoolSize) {
		super(corePoolSize);
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, corePoolSize);
	}



	@Override
	public List<Runnable> getRunningTasks() {
		return taskTrackingDecorator.getRunningTasks();
	}



	final Deque<BiConsumer<Thread, Runnable>> beforeExecuteHooks = new LinkedList<>();

	/** Adds {@code hook} to be executed in {@link #beforeExecute(Thread, Runnable)}. */
	public void addBeforeExecuteHook(BiConsumer<Thread, Runnable> hook) {
		beforeExecuteHooks.addFirst(hook);
	}

	/**
	 * Executes all {@link #addBeforeExecuteHook(BiConsumer) added hooks} in the reverse order they
	 * were added.
	 */
	@Override
	protected final void beforeExecute(Thread worker, Runnable task) {
		beforeExecuteHooks.forEach((hook) -> hook.accept(worker, task));
	}



	final List<BiConsumer<Runnable, Throwable>> afterExecuteHooks = new LinkedList<>();

	/** Adds hook to be executed in {@link #afterExecute(Runnable, Throwable)}. */
	public void addAfterExecuteHook(BiConsumer<Runnable, Throwable> hook) {
		afterExecuteHooks.add(hook);
	}

	/**
	 * Executes all {@link #addAfterExecuteHook(BiConsumer)} added hooks} in the order they were
	 * added.
	 */
	@Override
	protected final void afterExecute(Runnable task, Throwable error) {
		afterExecuteHooks.forEach((hook) -> hook.accept(task, error));
	}



	/** Decorates {@code task} using {@link ScheduledExecution}. */
	@Override
	protected <V> ScheduledExecution<V> decorateTask(
		Runnable task,
		RunnableScheduledFuture<V> scheduledExecution
	) {
		return new ScheduledExecution<>(task, scheduledExecution);
	}



	/** Decorates {@code task} using {@link ScheduledExecution}. */
	@Override
	protected <V> ScheduledExecution<V> decorateTask(
		Callable<V> task,
		RunnableScheduledFuture<V> scheduledExecution
	) {
		return new ScheduledExecution<>(task, scheduledExecution);
	}



	/**
	 * Decorates a {@link RunnableScheduledFuture} to allow to obtain the original scheduled task.
	 */
	public static class ScheduledExecution<V> implements RunnableScheduledFuture<V> {

		/** The original scheduled task. Either a {@link Runnable} or a {@link Callable}. */
		public Object getTask() { return task; }
		final Object task;

		final RunnableScheduledFuture<V> wrappedScheduledItem;

		public ScheduledExecution(Object task, RunnableScheduledFuture<V> scheduledItemToWrap) {
			this.task = task;
			this.wrappedScheduledItem = scheduledItemToWrap;
		}

		// only dumb delegations to wrappedScheduledItem below

		@Override public boolean isPeriodic() {
			return wrappedScheduledItem.isPeriodic();
		}

		@Override public void run() {
			wrappedScheduledItem.run();
		}

		@Override public boolean cancel(boolean mayInterruptIfRunning) {
			return wrappedScheduledItem.cancel(mayInterruptIfRunning);
		}

		@Override public boolean isCancelled() {
			return wrappedScheduledItem.isCancelled();
		}

		@Override public boolean isDone() {
			return wrappedScheduledItem.isDone();
		}

		@Override public V get() throws InterruptedException, ExecutionException {
			return wrappedScheduledItem.get();
		}

		@Override public V get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException {
			return wrappedScheduledItem.get(timeout, unit);
		}

		@Override public long getDelay(TimeUnit unit) {
			return wrappedScheduledItem.getDelay(unit);
		}

		@Override public int compareTo(Delayed o) {
			return wrappedScheduledItem.compareTo(o);
		}
	}



	public ScheduledTaskTrackingThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
		super(corePoolSize, threadFactory);
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, corePoolSize);
	}



	public ScheduledTaskTrackingThreadPoolExecutor(
		int corePoolSize,
		RejectedExecutionHandler handler
	) {
		super(corePoolSize, handler);
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, corePoolSize);
	}



	public ScheduledTaskTrackingThreadPoolExecutor(
		int corePoolSize,
		ThreadFactory threadFactory,
		RejectedExecutionHandler handler
	) {
		super(corePoolSize, threadFactory, handler);
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, corePoolSize);
	}
}
