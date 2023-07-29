// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;



/**
 * A {@link TaskTracingExecutor} based on a {@link ThreadPoolExecutor}.
 */
public class TaskTracingThreadPoolExecutor extends ThreadPoolExecutor implements TaskTracingExecutor
{



	final TaskTracingExecutorDecorator taskTracingDecorator;



	public TaskTracingThreadPoolExecutor(
		int corePoolSize,
		int maximumPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue
	) {
		super(
			corePoolSize,
			maximumPoolSize,
			keepAliveTime,
			unit,
			workQueue
		);
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



	public TaskTracingThreadPoolExecutor(
		int corePoolSize,
		int maximumPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		ThreadFactory threadFactory
	) {
		super(
			corePoolSize,
			maximumPoolSize,
			keepAliveTime,
			unit,
			workQueue,
			threadFactory
		);
		taskTracingDecorator = new TaskTracingExecutorDecorator(this, false, corePoolSize);
	}

	public TaskTracingThreadPoolExecutor(
		int corePoolSize,
		int maximumPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		RejectedExecutionHandler handler
	) {
		super(
			corePoolSize,
			maximumPoolSize,
			keepAliveTime,
			unit,
			workQueue,
			handler
		);
		taskTracingDecorator = new TaskTracingExecutorDecorator(this, false, corePoolSize);
	}

	public TaskTracingThreadPoolExecutor(
		int corePoolSize,
		int maximumPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		ThreadFactory threadFactory,
		RejectedExecutionHandler handler
	) {
		super(
			corePoolSize,
			maximumPoolSize,
			keepAliveTime,
			unit,
			workQueue,
			threadFactory,
			handler
		);
		taskTracingDecorator = new TaskTracingExecutorDecorator(this, false, corePoolSize);
	}
}
