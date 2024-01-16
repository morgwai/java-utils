// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator;

import static java.util.concurrent.TimeUnit.DAYS;



/** A {@link TaskTrackingExecutor} based on a {@link ThreadPoolExecutor}. */
public class TaskTrackingThreadPoolExecutor extends ThreadPoolExecutor
		implements TaskTrackingExecutor, TaskTrackingExecutorDecorator.HookableExecutor {



	final TaskTrackingExecutorDecorator taskTrackingDecorator;



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
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, corePoolSize);
		setThreadFactory(taskTrackingDecorator.decorateThreadFactory(getThreadFactory()));
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



	// only constructor variations below



	/**
	 * Calls {@link #TaskTrackingThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
	 * this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>())}.
	 */
	public TaskTrackingThreadPoolExecutor(int poolSize) {
		this(poolSize, poolSize, 0L, DAYS, new LinkedBlockingQueue<>());
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
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, corePoolSize);
		setThreadFactory(taskTrackingDecorator.decorateThreadFactory(getThreadFactory()));
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
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, corePoolSize);
		setThreadFactory(taskTrackingDecorator.decorateThreadFactory(getThreadFactory()));
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
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
		taskTrackingDecorator = new TaskTrackingExecutorDecorator(this, corePoolSize);
		setThreadFactory(taskTrackingDecorator.decorateThreadFactory(getThreadFactory()));
	}
}
