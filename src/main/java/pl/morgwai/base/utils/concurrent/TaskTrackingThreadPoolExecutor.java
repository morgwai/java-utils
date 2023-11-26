// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.*;



/** A {@link TaskTrackingExecutor} based on a {@link ThreadPoolExecutor}. */
public class TaskTrackingThreadPoolExecutor extends ThreadPoolExecutor
		implements TaskTrackingExecutor {



	final TaskTrackingExecutorDecorator taskTrackingDecorator;



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
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, false, corePoolSize);
	}



	/** Subclasses must call {@code super}. */
	@Override
	protected void beforeExecute(Thread worker, Runnable task) {
		taskTrackingDecorator.storeTaskIntoHolderBeforeExecute(task);
	}



	/** Subclasses must call {@code super}. */
	@Override
	protected void afterExecute(Runnable task, Throwable error) {
		taskTrackingDecorator.clearTaskHolderAfterExecute();
	}



	@Override
	public ForcedTerminationAftermath tryForceTerminate() {
		return taskTrackingDecorator.tryForceTerminate();
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
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, false, corePoolSize);
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
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, false, corePoolSize);
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
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, false, corePoolSize);
	}
}
