// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.Comparator;
import java.util.LinkedList;

import com.google.common.collect.Comparators;
import org.junit.Test;
import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator
		.HookableExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;



public abstract class TaskTrackingHookableExecutorTests extends TaskTrackingExecutorTests {



	@Test
	public void testAddingHooks() throws InterruptedException {
		final var hookExecutions = new LinkedList<Integer>();
		final var hookableExecutor = (HookableExecutor) testSubject;
		hookableExecutor.addAfterExecuteHook((task, error) -> hookExecutions.add(1));
		hookableExecutor.addBeforeExecuteHook((thread, task) -> hookExecutions.add(-1));
		hookableExecutor.addBeforeExecuteHook((thread, task) -> hookExecutions.add(-2));
		hookableExecutor.addAfterExecuteHook((task, error) -> hookExecutions.add(2));
		hookableExecutor.addAfterExecuteHook((task, error) -> hookExecutions.add(3));
		hookableExecutor.addBeforeExecuteHook((thread, task) -> hookExecutions.add(-3));
		hookableExecutor.addAfterExecuteHook((task, error) -> hookExecutions.add(4));
		hookableExecutor.execute(() -> hookExecutions.add(0));
		hookableExecutor.shutdown();
		assertTrue("hookableExecutor should terminate",
				hookableExecutor.awaitTermination(20L, MILLISECONDS));
		assertEquals("all added hooks should be executed",
				8, hookExecutions.size());
		assertTrue("hooks should be executed in the correct order",
				Comparators.isInStrictOrder(hookExecutions, Comparator.comparingInt((i) -> i)));
	}
}
