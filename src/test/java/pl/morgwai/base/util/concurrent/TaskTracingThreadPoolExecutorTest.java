// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;

import org.junit.*;
import pl.morgwai.base.util.concurrent.ConcurrentUtils.RunnableCallable;

import static org.junit.Assert.*;
import static pl.morgwai.base.util.concurrent.ConcurrentUtils.completableFutureSupplyAsync;



public class TaskTracingThreadPoolExecutorTest {



	protected TaskTracingExecutor testSubject;

	protected Executor expectedRejectingExecutor;
	Runnable rejectedTask;
	Executor rejectingExecutor;
	protected final RejectedExecutionHandler rejectionHandler = (task, executor) -> {
		rejectedTask = task;
		rejectingExecutor = executor;
		throw new RejectedExecutionException("rejected " + task);
	};



	@Before
	public void setup() {
		testSubject = new TaskTracingThreadPoolExecutor(
				1, 1, 0L, TimeUnit.DAYS, new LinkedBlockingQueue<>(1), rejectionHandler);
		expectedRejectingExecutor = testSubject;
	}

	@After
	public void shutdownNowIfNeeded() {
		if ( !testSubject.isTerminated()) testSubject.shutdownNow();
	}



	/** For {@link ScheduledTaskTracingThreadPoolExecutorTest} */
	protected Object unwrapIfScheduled(Runnable task) {
		return task;
	}



	@Test
	public void testExecuteCallable()
			throws InterruptedException, TimeoutException, ExecutionException {
		final var result = "result";

		final var execution = completableFutureSupplyAsync(() -> result, testSubject);
		assertSame("obtained result should be the same as returned",
				result, execution.get(50L, TimeUnit.MILLISECONDS));
	}



	@Test
	public void testStuckCallable() throws InterruptedException {
		final var latchAwaitingTaskStarted = new CountDownLatch(1);
		final var taskBlockingLatch = new CountDownLatch(1);
		final var latchAwaitingTask = new Callable<>() {
			@Override public String call() throws Exception {
				latchAwaitingTaskStarted.countDown();
				taskBlockingLatch.await();
				return "";
			}
			@Override public String toString() {
				return "latchAwaitingTask";
			}
		};
		final var instantTask = new Callable<>() {
			@Override public Integer call()  {
				return 0;
			}
			@Override public String toString() {
				return "instantTask";
			}
		};

		final var latchAwaitingTaskExecution =
				completableFutureSupplyAsync(latchAwaitingTask, testSubject);
		final var instantTaskExecution = completableFutureSupplyAsync(instantTask, testSubject);
		assertTrue("latchAwaitingTask should start",
				latchAwaitingTaskStarted.await(20L, TimeUnit.MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse("latchAwaitingTaskExecution should not complete",
				latchAwaitingTaskExecution.isDone());
		assertFalse("instantTask should not be executed", instantTaskExecution.isDone());

		testSubject.shutdownNow();
		assertTrue("aftermath data should be present after the forced shutdown",
				testSubject.getForcedShutdownAftermath().isPresent());
		final var aftermath = testSubject.getForcedShutdownAftermath().get();
		assertEquals("1 task should be running in the aftermath", 1, aftermath.runningTasks.size());
		assertEquals("1 task should be unexecuted in the aftermath",
				1, aftermath.unexecutedTasks.size());
		final var runningTask = unwrapIfScheduled(aftermath.runningTasks.get(0));
		final var unexecutedTask = unwrapIfScheduled(aftermath.unexecutedTasks.get(0));
		assertTrue("runningTask should be a RunnableCallable instance",
				runningTask instanceof RunnableCallable);
		assertTrue("unexecutedTask should be a RunnableCallable instance",
				unexecutedTask instanceof RunnableCallable);
		assertSame("runningTask should be latchAwaitingTask",
				latchAwaitingTask, ((RunnableCallable<?>) runningTask).getWrappedTask());
		assertSame("unexecutedTask should be instantTask",
				instantTask, ((RunnableCallable<?>) unexecutedTask).getWrappedTask());
		try {
			latchAwaitingTaskExecution.get(20L, TimeUnit.MILLISECONDS);
			fail("latchAwaitingTaskExecution should complete exceptionally");
		} catch (TimeoutException e) {
			fail("latchAwaitingTaskExecution should complete after the forced shutdown");
		} catch (ExecutionException e) {
			assertTrue(
				"latchAwaitingTask should throw an InterruptedException after forced shutdown",
				e.getCause() instanceof InterruptedException
			);
		}
		assertTrue("executor should terminate after the forced shutdown",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
	}



	@Test
	public void testStuckUninterruptibleCallable()
			throws InterruptedException, ExecutionException, TimeoutException {
		final var result = "result";
		final var latchAwaitingTaskStarted = new CountDownLatch(1);
		final var taskBlockingLatch = new CountDownLatch(1);
		final var latchAwaitingTask = new Callable<>() {
			@Override public String call() {
				latchAwaitingTaskStarted.countDown();
				boolean blockingLatchSwitched = false;
				while ( !blockingLatchSwitched) {
					try {
						taskBlockingLatch.await();
						blockingLatchSwitched = true;
					} catch (InterruptedException expected) {}
				}
				return result;
			}
			@Override public String toString() {
				return "latchAwaitingTask";
			}
		};
		final var instantTask = new Callable<>() {
			@Override public Integer call()  {
				return 0;
			}
			@Override public String toString() {
				return "instantTask";
			}
		};

		final var latchAwaitingTaskExecution =
				completableFutureSupplyAsync(latchAwaitingTask, testSubject);
		final var instantTaskExecution = completableFutureSupplyAsync(instantTask, testSubject);
		assertTrue("latchAwaitingTask should start",
				latchAwaitingTaskStarted.await(20L, TimeUnit.MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse("latchAwaitingTaskExecution should not complete",
				latchAwaitingTaskExecution.isDone());
		assertFalse("instantTask should not be executed", instantTaskExecution.isDone());

		testSubject.shutdownNow();
		assertTrue("aftermath data should be present after the forced shutdown",
				testSubject.getForcedShutdownAftermath().isPresent());
		final var aftermath = testSubject.getForcedShutdownAftermath().get();
		assertEquals("1 task should be running in the aftermath", 1, aftermath.runningTasks.size());
		assertEquals("1 task should be unexecuted in the aftermath",
				1, aftermath.unexecutedTasks.size());
		final var runningTask = unwrapIfScheduled(aftermath.runningTasks.get(0));
		final var unexecutedTask = unwrapIfScheduled(aftermath.unexecutedTasks.get(0));
		assertTrue("runningTask should be a RunnableCallable instance",
				runningTask instanceof RunnableCallable);
		assertTrue("unexecutedTask should be a RunnableCallable instance",
				unexecutedTask instanceof RunnableCallable);
		assertSame("runningTask should be latchAwaitingTask",
				latchAwaitingTask, ((RunnableCallable<?>) runningTask).getWrappedTask());
		assertSame("unexecutedTask should be instantTask",
				instantTask, ((RunnableCallable<?>) unexecutedTask).getWrappedTask());
		assertFalse("executor should not terminate even after the forced shutdown",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse("latchAwaitingTaskExecution should not complete even after the forced shutdown",
				latchAwaitingTaskExecution.isDone());

		testSubject.shutdownNow();
		final var aftermath2 = testSubject.getForcedShutdownAftermath().get();
		assertEquals("1 task should be running in the aftermath2",
				1, aftermath2.runningTasks.size());
		assertTrue("there should be no unexecuted tasks in the aftermath2",
				aftermath2.unexecutedTasks.isEmpty());
		final var runningTask2 = unwrapIfScheduled(aftermath2.runningTasks.get(0));
		assertTrue("runningTask2 should be a RunnableCallable instance",
				runningTask2 instanceof RunnableCallable);
		assertSame("runningTask2 should be latchAwaitingTask",
				latchAwaitingTask, ((RunnableCallable<?>) runningTask2).getWrappedTask());
		assertFalse("executor should not terminate even after the 2nd forced shutdown",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
		assertFalse(
			"latchAwaitingTaskExecution should not complete even after the 2nd forced shutdown",
			latchAwaitingTaskExecution.isDone()
		);

		taskBlockingLatch.countDown();
		assertSame("result of latchAwaitingTaskExecution should be the same as returned",
				result, latchAwaitingTaskExecution.get(20L, TimeUnit.MILLISECONDS));
		assertTrue("executor should terminate after taskBlockingLatch is switched",
				testSubject.awaitTermination(20L, TimeUnit.MILLISECONDS));
	}



	@Test
	public void testExecutionRejection() {
		final var taskBlockingLatch = new CountDownLatch(1);
		testSubject.execute(  // make executor's thread busy
			() -> {
				try {
					taskBlockingLatch.await();
				} catch (InterruptedException ignored) {}
			}
		);
		testSubject.execute(() -> {});  // fill executor's queue
		final Runnable overloadingTask = () -> {};

		try {
			testSubject.execute(overloadingTask);
			fail("overloaded executor should throw a RejectedExecutionException");
		} catch (RejectedExecutionException expected) {}
		assertSame("rejectingExecutor should be expectedRejectingExecutor",
				expectedRejectingExecutor, rejectingExecutor);
		assertSame("rejectedTask should be overloadingTask", overloadingTask, rejectedTask);
		taskBlockingLatch.countDown();
	}
}
