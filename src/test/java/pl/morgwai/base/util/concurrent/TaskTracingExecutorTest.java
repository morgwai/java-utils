// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.*;
import org.junit.experimental.categories.Category;
import pl.morgwai.base.util.SlowTests;
import pl.morgwai.base.util.concurrent.ConcurrentUtils.RunnableCallable;

import static org.junit.Assert.*;
import static pl.morgwai.base.util.concurrent.ConcurrentUtils.completableFutureSupplyAsync;



public abstract class TaskTracingExecutorTest {



	protected TaskTracingExecutor testSubject;

	protected Executor expectedRejectingExecutor;
	Runnable rejectedTask;
	Executor rejectingExecutor;
	protected final RejectedExecutionHandler rejectionHandler = (task, executor) -> {
		rejectedTask = task;
		rejectingExecutor = executor;
		throw new RejectedExecutionException("rejected " + task);
	};

	protected double expectedNoopTaskPerformanceFactor;
	protected double expected1msTaskPerformanceFactor = 1.015d;



	@Before
	public void setup() {
		testSubject = createTestSubjectAndFinishSetup(1, 1);
	}

	protected abstract TaskTracingExecutor createTestSubjectAndFinishSetup(
			int threadPoolSize, int queueSize);

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
		testSubject.execute(() -> {}); // fill executor's queue
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



	@Test
	public void test100kNoopTasksPerformance() throws InterruptedException {
		testPerformance(100_000, 0L, expectedNoopTaskPerformanceFactor);
	}

	@Test
	@Category({SlowTests.class})
	public void test10MNoopTasksPerformance() throws InterruptedException {
		testPerformance(10_000_000, 0L, expectedNoopTaskPerformanceFactor);
	}

	@Test
	public void test1k1msTasksPerformance() throws InterruptedException {
		testPerformance(1_000, 1L, 1.15d);  // 1.15d is a statistical inaccuracy exhibited even
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
		final var threadPoolExecutor = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L,
				TimeUnit.DAYS, new LinkedBlockingQueue<>(numberOfTasks));
		final var threadPoolExecutorDuration =
				measurePerformance(threadPoolExecutor, numberOfTasks, taskDurationMillis);
		testSubject = createTestSubjectAndFinishSetup(threadPoolSize, numberOfTasks);
		final var testSubjectDuration =
				measurePerformance(testSubject, numberOfTasks, taskDurationMillis);
		final var performanceFactor = ((double)testSubjectDuration) / threadPoolExecutorDuration;
		if (log.isLoggable(Level.INFO)) log.info(String.format(
			"%dk of %dms-tasks on %s resulted with %.3fx performanceFactor",
			numberOfTasks / 1000,
			taskDurationMillis,
			testSubject.getClass().getSimpleName(),
			performanceFactor
		));
		assertTrue(
			"task tracing should not be more than " + expectedPerformanceFactor + " times slower"
					+ " (was " + performanceFactor + "x)",
			expectedPerformanceFactor > performanceFactor
		);
	}

	long measurePerformance(
		ExecutorService executor,
		int numberOfTasks,
		long taskDurationMillis
	) throws InterruptedException {
		final var startMillis = System.currentTimeMillis();
		for (int i = 0; i < numberOfTasks; i++) executor.execute(() -> {
			try {
				Thread.sleep(taskDurationMillis);
			} catch (InterruptedException ignored) {}
		});
		executor.shutdown();
		assertTrue("executor should terminate in a reasonable time",
				executor.awaitTermination(100L, TimeUnit.SECONDS));
		final var durationMillis = System.currentTimeMillis() - startMillis;
		if (log.isLoggable(Level.INFO)) {
			log.info((numberOfTasks / 1000) + "k of " + taskDurationMillis + "ms-tasks on "
					+ executor.getClass().getSimpleName() + " took " + durationMillis + "ms");
		}
		return durationMillis;
	}



	/**
	 * Change the below value if you need logging:<br/>
	 * {@code INFO} will log performance measurements from
	 * {@link #testPerformance(int, long, double)}.
	 */
	static Level LOG_LEVEL = Level.WARNING;
	static final Logger log = Logger.getLogger(TaskTracingExecutorTest.class.getName());

	@BeforeClass
	public static void setupLogging() {
		try {
			LOG_LEVEL = Level.parse(System.getProperty(
					TaskTracingExecutorTest.class.getPackageName() + ".level"));
		} catch (Exception ignored) {}
		log.setLevel(LOG_LEVEL);
		for (final var handler: Logger.getLogger("").getHandlers()) handler.setLevel(LOG_LEVEL);
	}
}
