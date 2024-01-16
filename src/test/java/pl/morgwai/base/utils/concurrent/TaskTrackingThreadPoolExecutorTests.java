// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.Set;
import java.util.concurrent.*;

import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator
		.TaskHolder;

import static java.util.concurrent.TimeUnit.DAYS;



public class TaskTrackingThreadPoolExecutorTests extends TaskTrackingHookableExecutorTests {



	@Override
	protected TaskTrackingExecutor createTestSubjectAndFinishSetup(
		int threadPoolSize,
		int queueSize,
		ThreadFactory threadFactory
	) {
		final var executor = new TaskTrackingThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L,
				DAYS, new LinkedBlockingQueue<>(queueSize), threadFactory, rejectionHandler);
		expectedRejectingExecutor = executor;
		return executor;
	}



	@Override
	protected Set<TaskHolder> getRunningTaskHolders() {
		return ((TaskTrackingThreadPoolExecutor) testSubject).taskTrackingDecorator.runningTasks;
	}
}
