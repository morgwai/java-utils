// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.Set;
import java.util.concurrent.*;

import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator;
import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator
		.TaskHolder;

import static java.util.concurrent.TimeUnit.DAYS;



public class TaskTrackingExecutorDecoratorTests extends TaskTrackingExecutorTests {



	@Override
	protected TaskTrackingExecutor createTestSubjectAndFinishSetup(
		int threadPoolSize,
		int queueSize,
		ThreadFactory threadFactory
	) {
		final var backingExecutor = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L,
				DAYS, new LinkedBlockingQueue<>(queueSize), threadFactory, rejectionHandler);
		expectedRejectingExecutor = backingExecutor;
		return new TaskTrackingExecutorDecorator(backingExecutor);
	}



	@Override
	protected Set<TaskHolder> getRunningTaskHolders() {
		return ((TaskTrackingExecutorDecorator) testSubject).runningTasks;
	}
}
