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



	public ScheduledTaskTracingThreadPoolExecutor(
		int corePoolSize/*,
		ThreadFactory threadFactory,
		RejectedExecutionHandler handler*/
	) {
		super(
			corePoolSize/*,
			threadFactory,
			handler*/
		);
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



	@Override
	protected <V> RunnableScheduledFuture<V> decorateTask(
		Runnable task,
		RunnableScheduledFuture<V> scheduledFuture
	) {
		return new DecomposableRunnableScheduledFuture<>(task, scheduledFuture);
	}



	public static class DecomposableRunnableScheduledFuture<V> implements RunnableScheduledFuture<V>
	{
		public Runnable getWrappedScheduledTask() { return wrappedScheduledTask; }
		final Runnable wrappedScheduledTask;

		final RunnableScheduledFuture<V> wrappedScheduledFuture;

		public DecomposableRunnableScheduledFuture(
			Runnable wrappedScheduledTask,
			RunnableScheduledFuture<V> wrappedScheduledFuture
		) {
			this.wrappedScheduledTask = wrappedScheduledTask;
			this.wrappedScheduledFuture = wrappedScheduledFuture;
		}

		@Override public boolean isPeriodic() {
			return wrappedScheduledFuture.isPeriodic();
		}

		@Override public void run() {
			wrappedScheduledFuture.run();
		}

		@Override public boolean cancel(boolean mayInterruptIfRunning) {
			return wrappedScheduledFuture.cancel(mayInterruptIfRunning);
		}

		@Override public boolean isCancelled() {
			return wrappedScheduledFuture.isCancelled();
		}

		@Override public boolean isDone() {
			return wrappedScheduledFuture.isDone();
		}

		@Override public V get() throws InterruptedException, ExecutionException {
			return wrappedScheduledFuture.get();
		}

		@Override public V get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException {
			return wrappedScheduledFuture.get(timeout, unit);
		}

		@Override public long getDelay(TimeUnit unit) {
			return wrappedScheduledFuture.getDelay(unit);
		}

		@Override public int compareTo(Delayed o) {
			return wrappedScheduledFuture.compareTo(o);
		}
	}



	class SuperClassWrapper extends AbstractExecutorService implements ExecutorService {

		@Override public List<Runnable> shutdownNow() {
			return ScheduledTaskTracingThreadPoolExecutor.super.shutdownNow();
		}

		@Override public void execute(Runnable task) { throw new UnsupportedOperationException(); }
		@Override public void shutdown() { throw new UnsupportedOperationException(); }
		@Override public boolean isShutdown() { throw new UnsupportedOperationException(); }
		@Override public boolean isTerminated() { throw new UnsupportedOperationException(); }
		@Override public boolean awaitTermination(long timeout, TimeUnit unit) {
			throw new UnsupportedOperationException();
		}
	}
}
