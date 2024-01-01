// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.*;
import org.junit.experimental.categories.Category;
import pl.morgwai.base.utils.SlowTests;

import static java.util.concurrent.TimeUnit.*;

import static org.junit.Assert.*;
import static pl.morgwai.base.utils.concurrent.CallableTaskExecution.callAsync;



public abstract class TaskTrackingExecutorTests {



	protected TaskTrackingExecutor testSubject;

	protected CountDownLatch taskBlockingLatch;

	protected Executor expectedRejectingExecutor;
	Runnable rejectedTask;
	Executor rejectingExecutor;
	protected final RejectedExecutionHandler rejectionHandler = (task, executor) -> {
		rejectedTask = task;
		rejectingExecutor = executor;
		throw new RejectedExecutionException("rejected " + task);
	};

	protected double expectedNoopTaskPerformanceFactor = 1.2d;
	protected double expected1msTaskPerformanceFactor = 1.015d;



	/** For {@link ScheduledTaskTrackingThreadPoolExecutorTests} */
	protected Object unwrapIfScheduled(Runnable task) {
		return task;
	}



	@Before
	public void setup() {
		taskBlockingLatch = new CountDownLatch(1);
		testSubject = createTestSubjectAndFinishSetup(1, 1);
	}

	protected abstract TaskTrackingExecutor createTestSubjectAndFinishSetup(
			int threadPoolSize, int queueSize);



	@Test
	public void testExecuteCallable() throws Exception {
		final var result = "result";
		final var execution = callAsync(() -> result, testSubject);
		assertSame("obtained result should be the same as returned",
				result, execution.get(50L, MILLISECONDS));
	}



	@Test
	public void testStuckCallable() throws InterruptedException {
		final var blockingTaskStarted = new CountDownLatch(1);
		final var blockingTask = new Callable<>() {
			@Override public String call() throws Exception {
				blockingTaskStarted.countDown();
				taskBlockingLatch.await();
				return "";
			}
			@Override public String toString() {
				return "blockingTask";
			}
		};
		final var queuedTask = new Callable<>() {
			@Override public Integer call()  {
				return 0;
			}
			@Override public String toString() {
				return "queuedTask";
			}
		};

		final var blockingTaskExecution = callAsync(blockingTask, testSubject);
		final var instantTaskExecution = callAsync(queuedTask, testSubject);
		assertTrue("blockingTask should start",
				blockingTaskStarted.await(20L, MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, MILLISECONDS));
		assertFalse("blockingTaskExecution should not complete",
				blockingTaskExecution.isDone());
		assertFalse("queuedTask should not be executed",
				instantTaskExecution.isDone());

		final var aftermath = testSubject.tryForceTerminate();
		assertEquals("1 task should be running in the aftermath",
				1, aftermath.runningTasks.size());
		assertEquals("1 task should be unexecuted in the aftermath",
				1, aftermath.unexecutedTasks.size());
		final var runningTask = unwrapIfScheduled(aftermath.runningTasks.get(0));
		final var unexecutedTask = unwrapIfScheduled(aftermath.unexecutedTasks.get(0));
		assertTrue("runningTask should be a CallableTaskExecution instance",
				runningTask instanceof CallableTaskExecution);
		assertTrue("unexecutedTask should be a CallableTaskExecution instance",
				unexecutedTask instanceof CallableTaskExecution);
		assertSame("runningTask should be blockingTask",
				blockingTask, ((CallableTaskExecution<?>) runningTask).getTask());
		assertSame("unexecutedTask should be queuedTask",
				queuedTask, ((CallableTaskExecution<?>) unexecutedTask).getTask());
		try {
			blockingTaskExecution.get(20L, MILLISECONDS);
			fail("blockingTaskExecution should complete exceptionally");
		} catch (TimeoutException e) {
			fail("blockingTaskExecution should complete after the forced shutdown");
		} catch (ExecutionException e) {
			assertTrue("blockingTask should throw an InterruptedException after forced shutdown",
					e.getCause() instanceof InterruptedException);
		}
		assertTrue("executor should terminate after the forced shutdown",
				testSubject.awaitTermination(20L, MILLISECONDS));
	}



	@Test
	public void testStuckUninterruptibleCallable()
			throws InterruptedException, ExecutionException, TimeoutException {
		final var result = "result";
		final var blockingTaskStarted = new CountDownLatch(1);
		final var blockingTask = new Callable<>() {
			@Override public String call() {
				blockingTaskStarted.countDown();
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
				return "blockingTask";
			}
		};
		final var queuedTask = new Callable<>() {
			@Override public Integer call()  {
				return 0;
			}
			@Override public String toString() {
				return "queuedTask";
			}
		};

		final var blockingTaskExecution = callAsync(blockingTask, testSubject);
		final var instantTaskExecution = callAsync(queuedTask, testSubject);
		assertTrue("blockingTask should start",
				blockingTaskStarted.await(20L, MILLISECONDS));

		testSubject.shutdown();
		assertFalse("executor should not terminate",
				testSubject.awaitTermination(20L, MILLISECONDS));
		assertFalse("blockingTaskExecution should not complete",
				blockingTaskExecution.isDone());
		assertFalse("queuedTask should not be executed", instantTaskExecution.isDone());

		final var aftermath = testSubject.tryForceTerminate();
		assertEquals("1 task should be running in the aftermath",
				1, aftermath.runningTasks.size());
		assertEquals("1 task should be unexecuted in the aftermath",
				1, aftermath.unexecutedTasks.size());
		final var runningTask = unwrapIfScheduled(aftermath.runningTasks.get(0));
		final var unexecutedTask = unwrapIfScheduled(aftermath.unexecutedTasks.get(0));
		assertTrue("runningTask should be a CallableTaskExecution instance",
				runningTask instanceof CallableTaskExecution);
		assertTrue("unexecutedTask should be a CallableTaskExecution instance",
				unexecutedTask instanceof CallableTaskExecution);
		assertSame("runningTask should be blockingTask",
				blockingTask, ((CallableTaskExecution<?>) runningTask).getTask());
		assertSame("unexecutedTask should be queuedTask",
				queuedTask, ((CallableTaskExecution<?>) unexecutedTask).getTask());
		assertFalse("executor should not terminate even after the forced shutdown",
				testSubject.awaitTermination(20L, MILLISECONDS));
		assertFalse("blockingTaskExecution should not complete even after the forced shutdown",
				blockingTaskExecution.isDone());

		final var aftermath2 = testSubject.tryForceTerminate();
		assertEquals("1 task should be running in the aftermath2",
				1, aftermath2.runningTasks.size());
		assertTrue("there should be no unexecuted tasks in the aftermath2",
				aftermath2.unexecutedTasks.isEmpty());
		final var runningTask2 = unwrapIfScheduled(aftermath2.runningTasks.get(0));
		assertTrue("runningTask2 should be a CallableTaskExecution instance",
				runningTask2 instanceof CallableTaskExecution);
		assertSame("runningTask2 should be blockingTask",
				blockingTask, ((CallableTaskExecution<?>) runningTask2).getTask());
		assertFalse("executor should not terminate even after the 2nd forced shutdown",
				testSubject.awaitTermination(20L, MILLISECONDS));
		assertFalse("blockingTaskExecution should not complete even after the 2nd forced shutdown",
				blockingTaskExecution.isDone()
		);

		taskBlockingLatch.countDown();
		assertSame("result of blockingTaskExecution should be the same as returned",
				result, blockingTaskExecution.get(20L, MILLISECONDS));
		assertTrue("executor should terminate after taskBlockingLatch is switched",
				testSubject.awaitTermination(20L, MILLISECONDS));
	}



	@Test
	public void testExecutionRejection() {
		testSubject.execute(  // make executor's thread busy
			() -> {
				try {
					taskBlockingLatch.await();
				} catch (InterruptedException ignored) {}
			}
		);
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
	public void testShutdownNowUnwrapsTasks() throws InterruptedException {
		final var blockingTaskStarted = new CountDownLatch(1);
		testSubject.execute(  // make executor's thread busy
			() -> {
				blockingTaskStarted.countDown();
				try {
					taskBlockingLatch.await();
				} catch (InterruptedException ignored) {}
			}
		);
		final Runnable queuedTask = () -> {};
		testSubject.execute(queuedTask);
		assertTrue("blocking task should start",
				blockingTaskStarted.await(20L, MILLISECONDS));

		final var unexecutedTasks = testSubject.shutdownNow();
		assertEquals("there should be 1 unexecuted task after shutdownNow()",
				1, unexecutedTasks.size());
		assertSame("unexecuted task should be queuedTask",
				queuedTask, unwrapIfScheduled(unexecutedTasks.get(0)));
	}



	@Test
	public void testIdleWorkersDoNotAddNullsToRunningTasks() throws InterruptedException {
		testSubject.execute(() -> {});
		testSubject.shutdown();
		assertTrue("executor should terminate",
				testSubject.awaitTermination(50L, MILLISECONDS));

		final var aftermath = testSubject.tryForceTerminate();
		assertTrue("there should be no running tasks",
				aftermath.getRunningTasks().isEmpty());
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



	/**
	 * Change the below value if you need logging:<br/>
	 * {@code INFO} will log performance measurements from
	 * {@link #testPerformance(int, long, double)}.
	 */
	static Level LOG_LEVEL = Level.WARNING;
	static final Logger log = Logger.getLogger(TaskTrackingExecutorTests.class.getName());

	@BeforeClass
	public static void setupLogging() {
		try {
			LOG_LEVEL = Level.parse(System.getProperty(
					TaskTrackingExecutorTests.class.getPackageName() + ".level"));
		} catch (Exception ignored) {}
		log.setLevel(LOG_LEVEL);
		for (final var handler: Logger.getLogger("").getHandlers()) handler.setLevel(LOG_LEVEL);
	}
}
