// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.Set;
import java.util.concurrent.*;

import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator
		.TaskHolder;

import static java.util.concurrent.TimeUnit.MILLISECONDS;



public class TaskTrackingThreadPoolExecutorTests extends TaskTrackingHookableExecutorTests {



	TaskTrackingThreadPoolExecutor executor;



	@Override
	protected TaskTrackingExecutor createTestSubjectAndFinishSetup(
		int threadPoolSize,
		int queueSize,
		ThreadFactory threadFactory
	) {
		executor = new TaskTrackingThreadPoolExecutor(
			threadPoolSize, threadPoolSize,
			0L, MILLISECONDS,
			new LinkedBlockingQueue<>(queueSize),
			threadFactory,
			rejectionHandler
		);
		expectedRejectingExecutor = executor;
		return executor;
	}



	@Override
	protected ThreadFactory getThreadFactory() {
		return executor.getThreadFactory();
	}



	@Override
	protected void setThreadFactory(ThreadFactory threadFactory) {
		executor.setThreadFactory(threadFactory);
	}



	@Override
	protected Set<TaskHolder> getRunningTaskHolders() {
		return executor.taskTrackingDecorator.runningTasks;
	}



	@Override
	protected void setMaxPoolSize(int maxPoolSize) {
		executor.setMaximumPoolSize(maxPoolSize);
	}
}
