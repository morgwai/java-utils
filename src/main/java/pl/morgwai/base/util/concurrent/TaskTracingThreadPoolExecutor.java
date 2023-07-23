// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.*;
import java.util.concurrent.*;



/**
 * blah...
 */
public class TaskTracingThreadPoolExecutor extends ThreadPoolExecutor implements TaskTracingExecutor
{



	final TaskTracingExecutorDecorator wrapper;



	public TaskTracingThreadPoolExecutor(
		int corePoolSize,
		int maximumPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue/*,
		ThreadFactory threadFactory,
		RejectedExecutionHandler handler*/
	) {
		super(
			corePoolSize,
			maximumPoolSize,
			keepAliveTime,
			unit,
			workQueue/*,
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



	class SuperClassWrapper extends AbstractExecutorService implements ExecutorService {

		@Override public List<Runnable> shutdownNow() {
			return TaskTracingThreadPoolExecutor.super.shutdownNow();
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
