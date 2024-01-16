// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator;
import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator
		.TaskHolder;

import static java.util.concurrent.TimeUnit.DAYS;



public class TaskTrackingExecutorDecoratorTests extends TaskTrackingExecutorTests {



	final List<BiConsumer<Runnable, Throwable>> afterExecuteHooks = new LinkedList<>();
	ThreadPoolExecutor backingExecutor;



	@Override
	protected TaskTrackingExecutor createTestSubjectAndFinishSetup(
		int threadPoolSize,
		int queueSize,
		ThreadFactory threadFactory
	) {
		backingExecutor = new ThreadPoolExecutor(
			threadPoolSize, threadPoolSize,
			0L, DAYS,
			new LinkedBlockingQueue<>(queueSize),
			threadFactory,
			rejectionHandler
		) {
			@Override protected void afterExecute(Runnable task, Throwable error) {
				afterExecuteHooks.forEach((hook) -> hook.accept(task, error));
			}
		};
		expectedRejectingExecutor = backingExecutor;
		return new TaskTrackingExecutorDecorator(backingExecutor);
	}



	@Override
	protected void addAfterExecuteHook(BiConsumer<Runnable, Throwable> hook) {
		afterExecuteHooks.add(hook);
	}



	@Override
	protected Set<TaskHolder> getRunningTaskHolders() {
		return ((TaskTrackingExecutorDecorator) testSubject).runningTasks;
	}
}
