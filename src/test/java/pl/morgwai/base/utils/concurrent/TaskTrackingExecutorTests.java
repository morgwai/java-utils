// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.*;

import org.junit.*;
import org.junit.experimental.categories.Category;
import pl.morgwai.base.utils.SlowTests;
import pl.morgwai.base.utils.concurrent.TaskTrackingExecutor.TaskTrackingExecutorDecorator
		.TaskHolder;

import static java.util.concurrent.TimeUnit.*;
import static java.util.logging.Level.*;
import static java.util.stream.Collectors.toUnmodifiableList;

import static org.junit.Assert.*;
import static pl.morgwai.base.jul.JulConfigurator.*;
import static pl.morgwai.base.utils.concurrent.CallableTaskExecution.callAsync;



public abstract class TaskTrackingExecutorTests {



	protected static final int THREADPOOL_SIZE = 10;
	protected TaskTrackingExecutor testSubject;



	@Before
	public void setup() {
		testSubject = createTestSubjectAndFinishSetup(
			THREADPOOL_SIZE,
			1,
			(task) -> new Thread(workerDecorator.apply(task)),
			(rejectedTask, executor) -> rejectionHandler.accept(rejectedTask, executor)
		);
	}

	protected abstract TaskTrackingExecutor createTestSubjectAndFinishSetup(
		int threadPoolSize,
		int queueSize,
		ThreadFactory threadFactory,
		RejectedExecutionHandler rejectionHandler
	);

	Function<Runnable, Runnable> workerDecorator = Function.identity();
	BiConsumer<Runnable, Executor> rejectionHandler = (rejectedTask, executor) -> {
		throw new RejectedExecutionException("executor " + executor + " rejected " + rejectedTask);
	};

	protected abstract void addAfterExecuteHook(BiConsumer<Runnable, Throwable> hook);
	protected abstract ThreadFactory getThreadFactory();
	protected abstract void setThreadFactory(ThreadFactory threadFactory);
	protected abstract Executor getExpectedRejectingExecutor();
	protected abstract Set<TaskHolder> getRunningTaskHolders();
	protected abstract void setMaxPoolSize(int maxPoolSize);

	/** For {@link ScheduledTaskTrackingThreadPoolExecutorTests}. */
	protected Object unwrapIfScheduled(Runnable task) {
		return task;
	}



	protected CountDownLatch taskBlockingLatch = new CountDownLatch(1);

	static class BlockingTask implements Callable<String> {

		final String taskId;
		final CountDownLatch taskStartedLatch;
		final CountDownLatch taskBlockingLatch;

		BlockingTask(
			String taskId,
			CountDownLatch taskStartedLatch,
			CountDownLatch taskBlockingLatch
		) {
			this.taskId = taskId;
			this.taskStartedLatch = taskStartedLatch;
			this.taskBlockingLatch = taskBlockingLatch;
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

	BlockingTask newBlockingTask(String taskId, CountDownLatch taskStartedLatch) {
		return new BlockingTask(
			taskId,
			taskStartedLatch,
			TaskTrackingExecutorTests.this.taskBlockingLatch
		);
	}

	CallableTaskExecution<String>[] createAndDispatchBlockingTasks(
		int numberOfTasks,
		CountDownLatch tasksStartedLatch
	) {
		return createAndDispatchBlockingTasks(
			numberOfTasks,
			tasksStartedLatch,
			taskBlockingLatch,
			testSubject
		);
	}

	static CallableTaskExecution<String>[] createAndDispatchBlockingTasks(
		int numberOfTasks,
		CountDownLatch tasksStartedLatch,
		CountDownLatch taskBlockingLatch,
		Executor executor
	) {
		@SuppressWarnings("unchecked")
		final CallableTaskExecution<String>[] tasks = new CallableTaskExecution[numberOfTasks];
		for (int taskNumber = 0; taskNumber < numberOfTasks; taskNumber++) {
			tasks[taskNumber] = CallableTaskExecution.callAsync(
				new BlockingTask(String.valueOf(taskNumber), tasksStartedLatch, taskBlockingLatch),
				executor
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
				newBlockingTask("blockingTask", blockingTaskStarted));
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
		rejectionHandler = (rejectedTask, rejectingExecutor) -> {
			assertEquals("rejectingExecutor should be expectedRejectingExecutor",
					getExpectedRejectingExecutor().getClass(), rejectingExecutor.getClass());
			assertSame("rejectingExecutor should be expectedRejectingExecutor",
					getExpectedRejectingExecutor(), rejectingExecutor);
			assertEquals("rejectedTask should be overloadingTask",
					overloadingTask.getClass(), rejectedTask.getClass());
			assertSame("rejectedTask should be overloadingTask",
					overloadingTask, rejectedTask);
			throw new RejectedExecutionException("rejected " + rejectedTask);
		};

		try {
			testSubject.execute(overloadingTask);
			fail("overloaded executor should throw a RejectedExecutionException");
		} catch (RejectedExecutionException expected) {}
		taskBlockingLatch.countDown();
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
		final int NUMBER_OF_TASKS = THREADPOOL_SIZE / 2;
		final var allTasksStarted = new CountDownLatch(NUMBER_OF_TASKS);
		final var allWorkersIdle = new CountDownLatch(NUMBER_OF_TASKS);
		addAfterExecuteHook((task, error) -> allWorkersIdle.countDown());
		createAndDispatchBlockingTasks(NUMBER_OF_TASKS, allTasksStarted);
		assertTrue("all tasks should start",
				allTasksStarted.await(50L, MILLISECONDS));
		taskBlockingLatch.countDown();
		assertTrue("all workers should become idle",
				allWorkersIdle.await(50L, MILLISECONDS));

		assertTrue("there should be no running tasks",
				testSubject.getRunningTasks().isEmpty());
	}



	@Test
	public void testDyingWorkersDoNotLeakTaskHolders() throws InterruptedException {
		final var workerDied = new CountDownLatch(1);
		final var originalFactory = getThreadFactory();
		setThreadFactory((task) -> {
			final var worker = originalFactory.newThread(task);
			worker.setUncaughtExceptionHandler((t, e) -> {
				log.log(FINE, "uncaught exception in " + t, e);
				workerDied.countDown();
			});
			return worker;
		});
		final var allTasksStarted = new CountDownLatch(THREADPOOL_SIZE - 1);
		createAndDispatchBlockingTasks(THREADPOOL_SIZE - 1, allTasksStarted);
		assertTrue("all tasks should start",
				allTasksStarted.await(100L, MILLISECONDS));
		assertEquals("sanity check",
				THREADPOOL_SIZE - 1, getRunningTaskHolders().size());

		testSubject.execute(() -> {
			throw new AssertionError("killing worker");
		});
		assertTrue("worker should die",
				workerDied.await(100L, MILLISECONDS));
		assertEquals("sanity check",
				THREADPOOL_SIZE - 1, testSubject.getRunningTasks().size());
		assertEquals("dead worker should have removed its taskHolder right before dying",
				THREADPOOL_SIZE - 1, getRunningTaskHolders().size());
		taskBlockingLatch.countDown();
	}



	@Test
	public void testLaidOffWorkersDoNotLeakTaskHolders() throws InterruptedException {
		final var extraWorkersLaidOff = new CountDownLatch(THREADPOOL_SIZE);
		workerDecorator = (workerClosure) -> () -> {
			try {
				workerClosure.run();
			} finally {
				extraWorkersLaidOff.countDown();
			}
		};
		final int MAX_POOL_SIZE = THREADPOOL_SIZE * 2;
		setMaxPoolSize(MAX_POOL_SIZE);
		final var allTasksCompleted = new CountDownLatch(MAX_POOL_SIZE + 1);
		addAfterExecuteHook((task, error) -> allTasksCompleted.countDown());
		final var allWorkersStarted = new CountDownLatch(MAX_POOL_SIZE);
		createAndDispatchBlockingTasks(MAX_POOL_SIZE + 1, allWorkersStarted);
		assertTrue("all workers should start",
				allWorkersStarted.await(100L, MILLISECONDS));
		assertEquals("sanity check",
				MAX_POOL_SIZE, getRunningTaskHolders().size());

		taskBlockingLatch.countDown();
		assertTrue("all tasks should complete",
				allTasksCompleted.await(100L, MILLISECONDS));
		assertTrue("extra workers should be laid off",
				extraWorkersLaidOff.await(100L, MILLISECONDS));
		assertTrue("sanity check",
				testSubject.getRunningTasks().isEmpty());
		assertEquals("laid Off workers should remove their taskHolders",
				THREADPOOL_SIZE, getRunningTaskHolders().size());
	}



	protected double expected1msTaskPerformanceFactor = 1.015d;

	@Test
	@Category({SlowTests.class})
	public void test10MNoopTasksPerformance() throws InterruptedException {
		testPerformance(10_000_000, 0L, 1.2d);
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
		// create executors
		final var threadPoolSize = Runtime.getRuntime().availableProcessors();
		final var threadPoolExecutor = new ThreadPoolExecutor(
			threadPoolSize, threadPoolSize,
			0L, DAYS,
			new LinkedBlockingQueue<>(numberOfTasks)
		);
		testSubject.shutdown();
		testSubject = createTestSubjectAndFinishSetup(
			threadPoolSize,
			numberOfTasks,
			Executors.defaultThreadFactory(),
			(rejectedTask, executor) -> {
				throw new RejectedExecutionException("rejected " + rejectedTask);
			}
		);

		// warmup threadPoolExecutor
		final var threadPoolExecutorTaskBlockingLatch = new CountDownLatch(1);
		final var allThreadPoolExecutorWarmupTasksStarted = new CountDownLatch(threadPoolSize);
		createAndDispatchBlockingTasks(
			threadPoolSize,
			allThreadPoolExecutorWarmupTasksStarted,
			threadPoolExecutorTaskBlockingLatch,
			threadPoolExecutor
		);
		assertTrue("all warmup tasks should start",
				allThreadPoolExecutorWarmupTasksStarted.await(50L, MILLISECONDS));
		threadPoolExecutorTaskBlockingLatch.countDown();

		// warmup testSubject
		final var allWarmupTasksStarted = new CountDownLatch(threadPoolSize);
		createAndDispatchBlockingTasks(threadPoolSize, allWarmupTasksStarted);
		assertTrue("all warmup tasks should start",
				allWarmupTasksStarted.await(50L, MILLISECONDS));
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
	public void tryTerminate() throws InterruptedException {
		testSubject.shutdown();
		taskBlockingLatch.countDown();
		try {
			testSubject.awaitTermination(50L, MILLISECONDS);
		} finally {
			if ( !testSubject.isTerminated()) {
				testSubject.shutdownNow();
				fail("executor should terminate cleanly");
			}
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
