// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;



/**
 * blah
 */
public interface TaskTracingExecutor extends ExecutorService {



	Optional<ForcedShutdownAftermath> getForcedShutdownAftermath();



	/** todo: blah... */
	class ForcedShutdownAftermath {

		/** List of tasks that were being executed when this method was called. */
		public List<Runnable> getRunningTasks() { return runningTasks; }
		public final List<Runnable> runningTasks;

		/** Result of {@link ExecutorService#shutdownNow()}. */
		public List<Runnable> getUnexecutedTasks() { return unexecutedTasks; }
		public final List<Runnable> unexecutedTasks;

		public ForcedShutdownAftermath(List<Runnable> runningTasks, List<Runnable> unexecutedTasks)
		{
			this.runningTasks = runningTasks;
			this.unexecutedTasks = unexecutedTasks;
		}
	}



	/**
	 * blah
	 */
	class TaskTracingExecutorDecorator extends AbstractExecutorService
		implements TaskTracingExecutor {



		final ExecutorService backingExecutor;
		final ConcurrentMap<Thread, Runnable> runningTasks;



		public TaskTracingExecutorDecorator(ExecutorService backingExecutor) {
			runningTasks = new ConcurrentHashMap<>();
			this.backingExecutor = backingExecutor;
		}



		public TaskTracingExecutorDecorator(ThreadPoolExecutor backingExecutor) {
			runningTasks = new ConcurrentHashMap<>(backingExecutor.getCorePoolSize());
			this.backingExecutor = backingExecutor;
			decorateRejectedExecutionHandler(backingExecutor);
		}

		public static void decorateRejectedExecutionHandler(ThreadPoolExecutor executor) {
			final var originalHandler = executor.getRejectedExecutionHandler();
			executor.setRejectedExecutionHandler(
					(wrappedTask, executor2) -> originalHandler.rejectedExecution(
							((TaskWrapper) wrappedTask).wrappedTask, executor2));
		}



		protected class TaskWrapper implements Runnable {

			final Runnable wrappedTask;

			protected TaskWrapper(Runnable taskToWrap) {
				wrappedTask = taskToWrap;
			}

			@Override public void run() {
				runningTasks.put(Thread.currentThread(), wrappedTask);
				try {
					wrappedTask.run();
				} finally {
					runningTasks.remove(Thread.currentThread());
				}
			}

			@Override public String toString() {
				return  wrappedTask.toString();
			}
		}



		@Override
		public void execute(Runnable task) {
			backingExecutor.execute(new TaskWrapper(task));
		}



		ForcedShutdownAftermath aftermath;

		@Override
		public Optional<ForcedShutdownAftermath> getForcedShutdownAftermath() {
			return Optional.ofNullable(aftermath);
		}

		@Override
		public List<Runnable> shutdownNow() {
			aftermath = new ForcedShutdownAftermath(
				List.copyOf(runningTasks.values()),
				backingExecutor.shutdownNow().stream()
					.map((wrappedTask) -> ((TaskWrapper) wrappedTask).wrappedTask)
					.collect(Collectors.toList())
			);
			return aftermath.unexecutedTasks;
		}



		// only dumb delegations to backingExecutor below:

		@Override
		public void shutdown() {
			backingExecutor.shutdown();
		}

		@Override
		public boolean isShutdown() {
			return backingExecutor.isShutdown();
		}

		@Override
		public boolean isTerminated() {
			return backingExecutor.isTerminated();
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
			return backingExecutor.awaitTermination(timeout, unit);
		}
	}
}
