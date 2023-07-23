// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;

import pl.morgwai.base.util.concurrent.TaskTracingExecutor.TaskTracingExecutorDecorator;



public class TaskTracingExecutorDecoratorTest extends TaskTracingThreadPoolExecutorTest {



	ThreadPoolExecutor backingExecutor;



	@Override
	public void setup() {
		backingExecutor = new ThreadPoolExecutor(
			1, 1, 0L, TimeUnit.DAYS, new LinkedBlockingQueue<>(1));
		testSubject = new TaskTracingExecutorDecorator(backingExecutor);
	}
}
