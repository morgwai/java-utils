// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.DAYS;



/** A {@link TaskTrackingExecutor} based on a {@link ThreadPoolExecutor}. */
public class TaskTrackingThreadPoolExecutor extends ThreadPoolExecutor
		implements TaskTrackingExecutor {



	final TaskTrackingExecutorDecorator taskTrackingDecorator;



	/**
	 * Calls {@link #TaskTrackingThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
	 * this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>())}.
	 */
	public TaskTrackingThreadPoolExecutor(int poolSize) {
		this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>());
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



	/**
	 * Calls {@link ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
	 * super} and sets up {@link TaskTrackingExecutor.TaskTrackingExecutorDecorator}.
	 */
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



	/**
	 * Calls {@link ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue,
	 * ThreadFactory) super} and sets up {@link TaskTrackingExecutor.TaskTrackingExecutorDecorator}.
	 */
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



	/**
	 * Calls {@link ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue,
	 * RejectedExecutionHandler) super} and sets up
	 * {@link TaskTrackingExecutor.TaskTrackingExecutorDecorator}.
	 */
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



	/**
	 * Calls {@link ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue,
	 * ThreadFactory, RejectedExecutionHandler) super} and sets up
	 * {@link TaskTrackingExecutor.TaskTrackingExecutorDecorator}.
	 */
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



	/**
	 * Calls {@link #TaskTrackingThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
	 * this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>(queueSize))}.
	 */
	public TaskTrackingThreadPoolExecutor(int poolSize, int queueSize) {
		this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>(queueSize));
	}



	/**
	 * Calls {@link #TaskTrackingThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
	 * this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>(queueSize), threadFactory)}.
	 */
	public TaskTrackingThreadPoolExecutor(int poolSize, int queueSize, ThreadFactory threadFactory)
	{
		this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>(queueSize), threadFactory);
	}



	/**
	 * Calls {@link #TaskTrackingThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
	 * this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>(queueSize), handler)}.
	 */
	public TaskTrackingThreadPoolExecutor(
		int poolSize,
		int queueSize,
		RejectedExecutionHandler handler
	) {
		this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>(queueSize), handler);
	}



	/**
	 * Calls {@link #TaskTrackingThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
	 * this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>(queueSize), threadFactory,
	 * handler)}.
	 */
	public TaskTrackingThreadPoolExecutor(
		int poolSize,
		int queueSize,
		ThreadFactory threadFactory,
		RejectedExecutionHandler handler
	) {
		this(
			poolSize,
			poolSize,
			0L, DAYS,
			new LinkedBlockingQueue<>(queueSize),
			threadFactory,
			handler
		);
	}
}
