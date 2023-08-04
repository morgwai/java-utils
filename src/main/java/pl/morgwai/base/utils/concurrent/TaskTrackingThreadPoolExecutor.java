// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.*;



/**
 * A {@link TaskTrackingExecutor} based on a {@link ThreadPoolExecutor}.
 */
public class TaskTrackingThreadPoolExecutor extends ThreadPoolExecutor
		implements TaskTrackingExecutor {



	final TaskTrackingExecutorDecorator taskTracingDecorator;



	public TaskTrackingThreadPoolExecutor(
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
		taskTracingDecorator = new TaskTrackingExecutorDecorator(this, false, corePoolSize);
	}



	/** Subclasses must call {@code super}. */
	@Override
	protected void beforeExecute(Thread worker, Runnable task) {
		taskTracingDecorator.storeTaskIntoHolderBeforeExecute(task);
	}

	/** Subclasses must call {@code super}. */
	@Override
	protected void afterExecute(Runnable task, Throwable error) {
		taskTracingDecorator.clearTaskHolderAfterExecute();
	}



	@Override
	public ForcedTerminationAftermath tryForceTerminate() {
		return taskTracingDecorator.tryForceTerminate();
	}



	public TaskTrackingThreadPoolExecutor(
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
		taskTracingDecorator = new TaskTrackingExecutorDecorator(this, false, corePoolSize);
	}

	public TaskTrackingThreadPoolExecutor(
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
		taskTracingDecorator = new TaskTrackingExecutorDecorator(this, false, corePoolSize);
	}

	public TaskTrackingThreadPoolExecutor(
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
		taskTracingDecorator = new TaskTrackingExecutorDecorator(this, false, corePoolSize);
	}
}
