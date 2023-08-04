// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.*;

import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator;



public class TaskTrackingExecutorDecoratorTest extends TaskTrackingExecutorTest {



	@Override
	protected TaskTrackingExecutor createTestSubjectAndFinishSetup(
			int threadPoolSize, int queueSize) {
		final var backingExecutor = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L,
				TimeUnit.DAYS, new LinkedBlockingQueue<>(queueSize), rejectionHandler);
		expectedRejectingExecutor = backingExecutor;
		expectedNoopTaskPerformanceFactor = 1.3d;
		return new TaskTrackingExecutorDecorator(backingExecutor);
	}
}
