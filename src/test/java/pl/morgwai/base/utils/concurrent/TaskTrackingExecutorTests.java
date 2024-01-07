// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.*;

import org.junit.*;
import org.junit.experimental.categories.Category;
import pl.morgwai.base.utils.SlowTests;

import static java.util.concurrent.TimeUnit.*;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toUnmodifiableList;

import static org.junit.Assert.*;
import static pl.morgwai.base.jul.JulConfigurator.*;
import static pl.morgwai.base.utils.concurrent.CallableTaskExecution.callAsync;



public abstract class TaskTrackingExecutorTests {



	protected static final int THREADPOOL_SIZE = 10;
	protected TaskTrackingExecutor testSubject;

	protected CountDownLatch taskBlockingLatch = new CountDownLatch(1);

	protected Executor expectedRejectingExecutor;
	Executor rejectingExecutor;
	Runnable rejectedTask;
	protected final RejectedExecutionHandler rejectionHandler = (task, executor) -> {
		rejectedTask = task;
		rejectingExecutor = executor;
		throw new RejectedExecutionException("rejected " + task);
	};

	protected double expectedNoopTaskPerformanceFactor = 1.2d;
	protected double expected1msTaskPerformanceFactor = 1.015d;



	@Before
	public void setup() {
		testSubject = createTestSubjectAndFinishSetup(THREADPOOL_SIZE, 1);
	}

	protected abstract TaskTrackingExecutor createTestSubjectAndFinishSetup(
			int threadPoolSize, int queueSize);



	/** For {@link ScheduledTaskTrackingThreadPoolExecutorTests} */
	protected Object unwrapIfScheduled(Runnable task) {
		return task;
	}



	class BlockingTask implements Callable<String> {

		final String taskId;
		final CountDownLatch taskStartedLatch;

		BlockingTask(String taskId, CountDownLatch taskStartedLatch) {
			this.taskId = taskId;
			this.taskStartedLatch = taskStartedLatch;
		}

		@Override public String call() throws InterruptedException {
			taskStartedLatch.countDown();
			taskBlockingLatch.await();
			return taskId;
		}

		@Override public String toString() {
			return "BlockingTask { taskId = \"" + taskId + "\" }";
		}
	}



	CallableTaskExecution<String>[] createAndDispatchBlockingTasks(
		int numberOfTasks,
		CountDownLatch tasksStartedLatch
	) {
		@SuppressWarnings("unchecked")
		final CallableTaskExecution<String>[] tasks = new CallableTaskExecution[numberOfTasks];
		for (int taskNumber = 0; taskNumber < numberOfTasks; taskNumber++) {
			tasks[taskNumber] = CallableTaskExecution.callAsync(
				new BlockingTask(String.valueOf(taskNumber), tasksStartedLatch),
				testSubject
			);
		}
		return tasks;
	}



	@Test
	public void testGetRunningTasks() throws InterruptedException {
		final int NUMBER_OF_TASKS = THREADPOOL_SIZE / 2;
		final var allTasksStarted = new CountDownLatch(NUMBER_OF_TASKS);
		final var tasks = createAndDispatchBlockingTasks(NUMBER_OF_TASKS, allTasksStarted);
		assertTrue("all tasks should start",
				allTasksStarted.await(100L, MILLISECONDS));
		final var runningTasks = testSubject.getRunningTasks()
			.stream()
			.map(this::unwrapIfScheduled)
			.collect(toUnmodifiableList());
		taskBlockingLatch.countDown();
		testSubject.shutdown();
		assertEquals("there should be " + NUMBER_OF_TASKS + " tasks on the list of running tasks",
				NUMBER_OF_TASKS, runningTasks.size());
		for (var taskNumber = 0; taskNumber < NUMBER_OF_TASKS; taskNumber++) {
			assertTrue("task-" + taskNumber + " should be on the list of running tasks",
					runningTasks.contains(tasks[taskNumber]));
		}
		assertTrue("testSubject should terminate cleanly after unblocking tasks",
				testSubject.awaitTermination(50L, MILLISECONDS));
	}



	@Test
	public void testExecute() throws Exception {
		final var result = "result";
		final var execution = callAsync(() -> result, testSubject);
		assertSame("obtained result should be the same as returned",
				result, execution.get(50L, MILLISECONDS));
	}



	@Test
	public void testStuckCallable() throws InterruptedException {
		final var blockingTaskStarted = new CountDownLatch(1);
		final var blockingTaskExecution = new CallableTaskExecution<>(
				new BlockingTask("blockingTask", blockingTaskStarted));
		testSubject.execute(blockingTaskExecution);
		assertTrue("blockingTaskExecution should start",
				blockingTaskStarted.await(50L, MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(50L, MILLISECONDS));
		assertFalse("blockingTaskExecution should not complete",
				blockingTaskExecution.isDone());

		final var runningTasks = testSubject.getRunningTasks();
		assertEquals("1 task should still be running after shutdown",
				1, runningTasks.size());
		final var runningTask = unwrapIfScheduled(runningTasks.get(0));
		assertSame("runningTask should be blockingTaskExecution",
				blockingTaskExecution, runningTask);

		assertTrue("there should be no unexecuted tasks after shutdownNow()",
				testSubject.shutdownNow().isEmpty());
		try {
			blockingTaskExecution.get(20L, MILLISECONDS);
			fail("blockingTaskExecution should complete exceptionally after shutdownNow()");
		} catch (TimeoutException e) {
			fail("blockingTaskExecution should complete after shutdownNow()");
		} catch (ExecutionException e) {
			assertTrue(
				"blockingTaskExecution should throw an InterruptedException after shutdownNow()",
				e.getCause() instanceof InterruptedException
			);
		}
		assertTrue("executor should terminate after shutdownNow()",
				testSubject.awaitTermination(20L, MILLISECONDS));
	}



	@Test
	public void testStuckUninterruptibleCallable()
			throws InterruptedException, ExecutionException, TimeoutException {
		final var result = "result";
		final var uninterruptibleTaskStarted = new CountDownLatch(1);
		final var uninterruptibleTask = new Callable<>() {
			@Override public String call() {
				uninterruptibleTaskStarted.countDown();
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
				return "uninterruptibleTask";
			}
		};

		final var uninterruptibleTaskExecution = callAsync(uninterruptibleTask, testSubject);
		assertTrue("uninterruptibleTask should start",
				uninterruptibleTaskStarted.await(50L, MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(50L, MILLISECONDS));
		assertFalse("uninterruptibleTaskExecution should not complete",
				uninterruptibleTaskExecution.isDone());

		final var runningTasks = testSubject.getRunningTasks();
		assertEquals("1 task should still be running after shutdown",
				1, runningTasks.size());
		final var runningTask = unwrapIfScheduled(runningTasks.get(0));
		assertTrue("runningTask2 should be a CallableTaskExecution instance",
				runningTask instanceof CallableTaskExecution);
		assertSame("runningTask2 should be uninterruptibleTask",
				uninterruptibleTask, ((CallableTaskExecution<?>) runningTask).getTask());

		assertTrue("there should be no unexecuted tasks after shutdownNow()",
				testSubject.shutdownNow().isEmpty());
		assertFalse("executor should not terminate even after shutdownNow()",
				testSubject.awaitTermination(50L, MILLISECONDS));
		assertFalse("uninterruptibleTaskExecution should not complete even after shutdownNow()",
				uninterruptibleTaskExecution.isDone());

		final var runningTasks2 = testSubject.getRunningTasks();
		assertEquals("1 task should still be running even after shutdownNow()",
				1, runningTasks2.size());
		final var runningTask2 = unwrapIfScheduled(runningTasks2.get(0));
		assertTrue("runningTask2 should be a CallableTaskExecution instance",
				runningTask2 instanceof CallableTaskExecution);
		assertSame("runningTask2 should be uninterruptibleTask",
				uninterruptibleTask, ((CallableTaskExecution<?>) runningTask2).getTask());

		assertTrue("there should be no unexecuted tasks after the 2nd shutdownNow()",
				testSubject.shutdownNow().isEmpty());
		assertFalse("executor should not terminate even after the 2nd shutdownNow()",
				testSubject.awaitTermination(50L, MILLISECONDS));
		assertFalse(
			"uninterruptibleTaskExecution should not complete even after the 2nd shutdownNow()",
			uninterruptibleTaskExecution.isDone()
		);

		taskBlockingLatch.countDown();
		assertSame("result of uninterruptibleTaskExecution should be the same as returned",
				result, uninterruptibleTaskExecution.get(20L, MILLISECONDS));
		assertTrue("executor should terminate after taskBlockingLatch is switched",
				testSubject.awaitTermination(20L, MILLISECONDS));
	}



	@Test
	public void testExecutionRejection() throws InterruptedException {
		final var allTasksStarted = new CountDownLatch(THREADPOOL_SIZE);
		createAndDispatchBlockingTasks(THREADPOOL_SIZE, allTasksStarted);
		assertTrue("all tasks should start",
				allTasksStarted.await(100L, MILLISECONDS));
		testSubject.execute(() -> {}); // fill executor's queue
		final Runnable overloadingTask = () -> {};

		try {
			testSubject.execute(overloadingTask);
			fail("overloaded executor should throw a RejectedExecutionException");
		} catch (RejectedExecutionException expected) {}
		taskBlockingLatch.countDown();
		assertSame("rejectingExecutor should be expectedRejectingExecutor",
				expectedRejectingExecutor, rejectingExecutor);
		assertSame("rejectedTask should be overloadingTask",
				overloadingTask, rejectedTask);
	}



	@Test
	public void testShutdownNowPreservesQueuedTasks() throws InterruptedException {
		final var allTasksStarted = new CountDownLatch(THREADPOOL_SIZE);
		createAndDispatchBlockingTasks(THREADPOOL_SIZE, allTasksStarted);
		assertTrue("all tasks should start",
				allTasksStarted.await(100L, MILLISECONDS));
		final Runnable queuedTask = () -> {};
		testSubject.execute(queuedTask);

		final var unexecutedTasks = testSubject.shutdownNow();
		assertEquals("there should be 1 unexecuted task after shutdownNow()",
				1, unexecutedTasks.size());
		assertSame("unexecuted task should be queuedTask",
				queuedTask, unwrapIfScheduled(unexecutedTasks.get(0)));
	}



	@Test
	public void testIdleWorkersDoNotAddNullsToRunningTasks() throws InterruptedException {
		for (int i = 0; i < THREADPOOL_SIZE / 2; i++) testSubject.execute(() -> {});

		final var runningTasks = testSubject.getRunningTasks();
		assertTrue("there should be no running tasks",
				runningTasks.isEmpty());

		testSubject.shutdown();
		assertTrue("executor should terminate",
				testSubject.awaitTermination(20L, MILLISECONDS));
	}



	@Test
	@Category({SlowTests.class})
	public void test10MNoopTasksPerformance() throws InterruptedException {
		testPerformance(10_000_000, 0L, expectedNoopTaskPerformanceFactor);
	}

	@Test
	public void test1k1msTasksPerformance() throws InterruptedException {
		testPerformance(1_000, 1L, 1.1d);  // 1.1d is a statistical inaccuracy observed even
				// between ThreadPoolExecutor invocations for such a small number of tasks as 1k.
	}

	@Test
	@Category({SlowTests.class})
	public void test10k1msTasksPerformance() throws InterruptedException {
		testPerformance(10_000, 1L, expected1msTaskPerformanceFactor);
	}

	public void testPerformance(
		int numberOfTasks,
		long taskDurationMillis,
		double expectedPerformanceFactor
	) throws InterruptedException {
		final var threadPoolSize = Runtime.getRuntime().availableProcessors();
		final var threadPoolExecutor = new ThreadPoolExecutor(
				threadPoolSize, threadPoolSize, 0L, DAYS, new LinkedBlockingQueue<>(numberOfTasks));
		testSubject.shutdown();
		testSubject = createTestSubjectAndFinishSetup(threadPoolSize, numberOfTasks);
		final Runnable warmupTask = () -> {
			try {
				taskBlockingLatch.await();
			} catch (InterruptedException ignored) {}
		};
		for (int i = 0; i < threadPoolSize; i++) {
			testSubject.execute(warmupTask);
			threadPoolExecutor.execute(warmupTask);
		}
		taskBlockingLatch.countDown();

		final var threadPoolExecutorDuration =
				measurePerformance(threadPoolExecutor, numberOfTasks, taskDurationMillis);
		final var testSubjectDuration =
				measurePerformance(testSubject, numberOfTasks, taskDurationMillis);
		final var performanceFactor = ((double) testSubjectDuration) / threadPoolExecutorDuration;
		if (log.isLoggable(Level.INFO)) log.info(String.format(
			"%dk of %dms-tasks on %s resulted with %.3fx performanceFactor",
			numberOfTasks / 1000,
			taskDurationMillis,
			testSubject.getClass().getSimpleName(),
			performanceFactor
		));
		assertTrue(
			String.format(
				"task tracing should not be more than %.3f times slower (was %.3fx)",
				expectedPerformanceFactor,
				performanceFactor
			),
			expectedPerformanceFactor > performanceFactor
		);
	}

	long measurePerformance(
		ExecutorService executor,
		int numberOfTasks,
		long taskDurationMillis
	) throws InterruptedException {
		final Runnable testTask = () -> {
			try {
				Thread.sleep(taskDurationMillis);
			} catch (InterruptedException ignored) {}
		};
		testTask.run();  // warmup
		final var startMillis = System.currentTimeMillis();
		for (int i = 0; i < numberOfTasks; i++) executor.execute(testTask);
		executor.shutdown();
		assertTrue("executor should terminate in a reasonable time",
				executor.awaitTermination(100L, SECONDS));
		final var durationMillis = System.currentTimeMillis() - startMillis;
		if (log.isLoggable(Level.INFO)) log.info(String.format(
			"%dk of %dms-tasks on %s took %dms",
			(numberOfTasks / 1000),
			taskDurationMillis,
			executor.getClass().getSimpleName(),
			durationMillis
		));
		return durationMillis;
	}



	@After
	public void tryTerminate() {
		testSubject.shutdown();
		taskBlockingLatch.countDown();
		try {
			testSubject.awaitTermination(50L, MILLISECONDS);
		} catch (InterruptedException ignored) {
		} finally {
			if ( !testSubject.isTerminated()) testSubject.shutdownNow();
		}
	}



	static final Logger log = Logger.getLogger(TaskTrackingExecutorTests.class.getName());



	/**
	 * {@code INFO} will log performance measurements from
	 * {@link #testPerformance(int, long, double)}.
	 */
	@BeforeClass
	public static void setupLogging() {
		addOrReplaceLoggingConfigProperties(Map.of(
			LEVEL_SUFFIX, WARNING.toString(),
			ConsoleHandler.class.getName() + LEVEL_SUFFIX, FINEST.toString()
		));
		overrideLogLevelsWithSystemProperties("pl.morgwai");
	}
}
