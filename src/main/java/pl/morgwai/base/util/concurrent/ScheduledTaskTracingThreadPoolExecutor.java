// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;



/**
 * blah
 */
public class ScheduledTaskTracingThreadPoolExecutor extends ScheduledThreadPoolExecutor
		implements TaskTracingExecutor {



	final TaskTracingExecutorDecorator wrapper;



	public ScheduledTaskTracingThreadPoolExecutor(int corePoolSize) {
		super(corePoolSize);
		wrapper = new TaskTracingExecutorDecorator(new SuperClassWrapper());
	}



	@Override
	protected void beforeExecute(Thread worker, Runnable task) {
		wrapper.beforeExecute(worker, task);
	}

	@Override
	protected void afterExecute(Runnable task, Throwable error) {
		wrapper.afterExecute();
	}



	@Override
	public List<Runnable> shutdownNow() {
		return wrapper.shutdownNow();
	}

	@Override
	public Optional<ForcedShutdownAftermath> getForcedShutdownAftermath() {
		return wrapper.getForcedShutdownAftermath();
	}

	class SuperClassWrapper extends AbstractExecutorService implements ExecutorService {

		@Override public List<Runnable> shutdownNow() {
			return ScheduledTaskTracingThreadPoolExecutor.super.shutdownNow();
		}

		// all remaining methods throw UnsupportedOperationException
		@Override public void execute(Runnable task) { throw new UnsupportedOperationException(); }
		@Override public void shutdown() { throw new UnsupportedOperationException(); }
		@Override public boolean isShutdown() { throw new UnsupportedOperationException(); }
		@Override public boolean isTerminated() { throw new UnsupportedOperationException(); }
		@Override public boolean awaitTermination(long timeout, TimeUnit unit) {
			throw new UnsupportedOperationException();
		}
	}



	@Override
	protected <V> RunnableScheduledFuture<V> decorateTask(
			Runnable task, RunnableScheduledFuture<V> scheduledItem) {
		return new ScheduledExecution<>(task, scheduledItem);
	}

	/** todo: blah */
	public static class ScheduledExecution<V> implements RunnableScheduledFuture<V> {

		public Runnable getTask() { return task; }
		final Runnable task;

		final RunnableScheduledFuture<V> wrappedScheduledItem;

		public ScheduledExecution(Runnable task, RunnableScheduledFuture<V> itemToWrap) {
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
		wrapper = new TaskTracingExecutorDecorator(new SuperClassWrapper());
	}

	public ScheduledTaskTracingThreadPoolExecutor(
			int corePoolSize, RejectedExecutionHandler handler) {
		super(corePoolSize, handler);
		wrapper = new TaskTracingExecutorDecorator(new SuperClassWrapper());
	}

	public ScheduledTaskTracingThreadPoolExecutor(
			int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
		super(corePoolSize, threadFactory, handler);
		wrapper = new TaskTracingExecutorDecorator(new SuperClassWrapper());
	}
}
