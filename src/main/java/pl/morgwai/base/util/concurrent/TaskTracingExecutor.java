// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
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

		final Set<TaskHolder> runningTasks;
		static class TaskHolder { volatile Runnable task; }

		final Function<List<Runnable>, List<Runnable>> unwrapTasks;
		static final Function<List<Runnable>, List<Runnable>> UNWRAP_TASKS =
				(tasks) -> tasks.stream()
					.map(task -> ((TaskWrapper) task).wrappedTask)
					.collect(Collectors.toList());



		public TaskTracingExecutorDecorator(ExecutorService backingExecutor) {
			this(backingExecutor, true, 1);
		}

		public TaskTracingExecutorDecorator(ThreadPoolExecutor backingExecutor) {
			this(backingExecutor, true, 1);
			decorateRejectedExecutionHandler(backingExecutor);
		}

		public TaskTracingExecutorDecorator(
				ExecutorService backingExecutor, boolean unwrap, int poolSize) {
			runningTasks = ConcurrentHashMap.newKeySet(poolSize);
			unwrapTasks = unwrap ? UNWRAP_TASKS : Function.identity();
			this.backingExecutor = backingExecutor;
		}

		public static void decorateRejectedExecutionHandler(ThreadPoolExecutor executor) {
			final var originalHandler = executor.getRejectedExecutionHandler();
			executor.setRejectedExecutionHandler(
				(wrappedTask, rejectingExecutor) -> originalHandler.rejectedExecution(
					((TaskWrapper) wrappedTask).wrappedTask,
					rejectingExecutor
				)
			);
		}



		ForcedShutdownAftermath aftermath;

		@Override
		public Optional<ForcedShutdownAftermath> getForcedShutdownAftermath() {
			return Optional.ofNullable(aftermath);
		}

		@Override
		public List<Runnable> shutdownNow() {
			aftermath = new ForcedShutdownAftermath(
				runningTasks.stream()
					.map((holder) -> holder.task)
					.collect(Collectors.toList()),
				unwrapTasks.apply(backingExecutor.shutdownNow())
			);
			return aftermath.unexecutedTasks;
		}




		ThreadLocal<TaskHolder> taskHolder = new ThreadLocal<>();

		public void beforeExecute(Runnable task) {
			var localHolder = taskHolder.get();
			if (localHolder == null) {
				localHolder = new TaskHolder();
				taskHolder.set(localHolder);
				runningTasks.add(localHolder);
			}
			localHolder.task = task;
		}

		public void afterExecute() {
			taskHolder.get().task = null;
		}



		@Override
		public void execute(Runnable task) {
			backingExecutor.execute(new TaskWrapper(task));
		}

		protected class TaskWrapper implements Runnable {

			final Runnable wrappedTask;

			protected TaskWrapper(Runnable taskToWrap) {
				wrappedTask = taskToWrap;
			}

			@Override public void run() {
				beforeExecute(wrappedTask);
				try {
					wrappedTask.run();
				} finally {
					afterExecute();
				}
			}

			@Override public String toString() {
				return  wrappedTask.toString();
			}
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
