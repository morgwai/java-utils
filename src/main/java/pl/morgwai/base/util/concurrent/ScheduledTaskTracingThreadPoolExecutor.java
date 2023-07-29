// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;



/**
 * A {@link ScheduledThreadPoolExecutor} that is also a {@link TaskTracingExecutor}.
 * <p>
 * <b>NOTE:</b> due to the design of
 * {@link ScheduledThreadPoolExecutor#decorateTask(Runnable, RunnableScheduledFuture) task
 * decorating} in {@link ScheduledThreadPoolExecutor} this class is <i>extremely</i> slow in case of
 * a large number of very tiny tasks. See
 * <a href="https://github.com/AdoptOpenJDK/openjdk-jdk11/blob/master/src/java.base/share/classes/
 *java/util/concurrent/ScheduledThreadPoolExecutor.java#L903-L915">
 * the comment with an explanation in the source</a>.</p>
 */
public class ScheduledTaskTracingThreadPoolExecutor extends ScheduledThreadPoolExecutor
		implements TaskTracingExecutor {



	final TaskTracingExecutorDecorator taskTracingDecorator;



	public ScheduledTaskTracingThreadPoolExecutor(int corePoolSize) {
		super(corePoolSize);
		taskTracingDecorator = new TaskTracingExecutorDecorator(this, false, corePoolSize);
	}



	/** Subclasses must call {@code super}. */
	@Override
	protected void beforeExecute(Thread worker, Runnable task) {
		taskTracingDecorator.beforeExecute(task);
	}

	/** Subclasses must call {@code super}. */
	@Override
	protected void afterExecute(Runnable task, Throwable error) {
		taskTracingDecorator.afterExecute();
	}



	@Override
	public ForcedTerminateAftermath tryForceTerminate() {
		return taskTracingDecorator.tryForceTerminate();
	}



	/** Decorates {@code task} using {@link ScheduledExecution}. */
	@Override
	protected <V> ScheduledExecution<V> decorateTask(
			Runnable task, RunnableScheduledFuture<V> scheduledExecution) {
		return new ScheduledExecution<>(task, scheduledExecution);
	}

	/** Decorates {@code task} using {@link ScheduledExecution}. */
	@Override
	protected <V> ScheduledExecution<V> decorateTask(
			Callable<V> task, RunnableScheduledFuture<V> scheduledExecution) {
		return new ScheduledExecution<>(task, scheduledExecution);
	}

	/** Wraps a {@link RunnableScheduledFuture} to allow to obtain the original scheduled task. */
	public static class ScheduledExecution<V> implements RunnableScheduledFuture<V> {

		/** The original scheduled task. Either a {@link Runnable} or a {@link Callable}. */
		public Object getTask() { return task; }
		final Object task;

		final RunnableScheduledFuture<V> wrappedScheduledItem;

		public ScheduledExecution(Object task, RunnableScheduledFuture<V> itemToWrap) {
			this.task = task;
			this.wrappedScheduledItem = itemToWrap;
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



	public ScheduledTaskTracingThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
		super(corePoolSize, threadFactory);
		taskTracingDecorator = new TaskTracingExecutorDecorator(this, false, corePoolSize);
	}

	public ScheduledTaskTracingThreadPoolExecutor(
			int corePoolSize, RejectedExecutionHandler handler) {
		super(corePoolSize, handler);
		taskTracingDecorator = new TaskTracingExecutorDecorator(this, false, corePoolSize);
	}

	public ScheduledTaskTracingThreadPoolExecutor(
			int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
		super(corePoolSize, threadFactory, handler);
		taskTracingDecorator = new TaskTracingExecutorDecorator(this, false, corePoolSize);
	}
}
