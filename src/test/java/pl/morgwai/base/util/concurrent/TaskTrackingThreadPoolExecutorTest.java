// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;



public class TaskTrackingThreadPoolExecutorTest extends TaskTrackingExecutorTest {



	@Override
	protected TaskTrackingExecutor createTestSubjectAndFinishSetup(
			int threadPoolSize, int queueSize) {
		final var executor = new TaskTrackingThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L,
				TimeUnit.DAYS, new LinkedBlockingQueue<>(queueSize), rejectionHandler);
		expectedRejectingExecutor = executor;
		expectedNoopTaskPerformanceFactor = 1.15d;
		return executor;
	}
}