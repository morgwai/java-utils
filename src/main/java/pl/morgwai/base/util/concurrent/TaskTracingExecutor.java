// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;



/**
 * An {@link ExecutorService} that allows to obtain a list of tasks that were still running when
 * {@link #shutdownNow()} was called.
 * @see TaskTracingThreadPoolExecutor
 * @see ScheduledTaskTracingThreadPoolExecutor
 */
public interface TaskTracingExecutor extends ExecutorService {



	/**
	 * Returns an object containing a list of tasks that that were still running when
	 * {@link #shutdownNow()} was called most recently together with its returned list of tasks that
	 * were removed from this executor's queue.
	 */
	Optional<ForcedShutdownAftermath> getForcedShutdownAftermath();

	/** Returned by {@link #getForcedShutdownAftermath()}. */
	class ForcedShutdownAftermath {

		/** List of tasks that were still running during last call to {@link #shutdownNow()}. */
		public List<Runnable> getRunningTasks() { return runningTasks; }
		public final List<Runnable> runningTasks;

		/** Result of the most recent {@link ExecutorService#shutdownNow()} call. */
		public List<Runnable> getUnexecutedTasks() { return unexecutedTasks; }
		public final List<Runnable> unexecutedTasks;

		public ForcedShutdownAftermath(List<Runnable> runningTasks, List<Runnable> unexecutedTasks)
		{
			this.runningTasks = runningTasks;
			this.unexecutedTasks = unexecutedTasks;
		}
	}



	/**
	 * A decorator for an {@link ExecutorService} that makes its target a
	 * {@link TaskTracingExecutor}.
	 * @see TaskTracingThreadPoolExecutor
	 * @see ScheduledTaskTracingThreadPoolExecutor
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



		/** Decorates {@code executorToDecorate}. */
		public TaskTracingExecutorDecorator(ExecutorService executorToDecorate) {
			this(executorToDecorate, true, 1);
		}

		/**
		 * Decorates {@code executorToDecorate} and calls
		 * {@link #decorateRejectedExecutionHandler(ThreadPoolExecutor)} to ensure that
		 * {@code executorToDecorate}'s {@link RejectedExecutionHandler} receives original tasks
		 * passed to {@link #execute(Runnable)}.
		 */
		public TaskTracingExecutorDecorator(ThreadPoolExecutor executorToDecorate) {
			this(executorToDecorate, true, executorToDecorate.getCorePoolSize());
			decorateRejectedExecutionHandler(executorToDecorate);
		}

		/**
		 * Decorates {@code executorToDecorate}. This is a low-level constructor for subclasses of
		 * various {@link ExecutorService}s, that embed {@link TaskTracingExecutorDecorator} to
		 * delegate methods to it and thus provide {@link TaskTracingExecutor} API.
		 * @param executorToDecorate executor to decorate.
		 * @param delegatingExecute must be {@code true} for executors that delegate
		 *     {@link #execute(Runnable)} to this decorator, {@code false} for those that use hooks
		 *     instead.
		 * @param threadPoolSize size of {@code executorToDecorate}'s threadPool for optimization
		 *     purposes: will be used a size for internal {@link ConcurrentHashMap}.
		 */
		public TaskTracingExecutorDecorator(
				ExecutorService executorToDecorate, boolean delegatingExecute, int threadPoolSize) {
			runningTasks = ConcurrentHashMap.newKeySet(threadPoolSize);
			unwrapTasks = delegatingExecute ? UNWRAP_TASKS : Function.identity();
			this.backingExecutor = executorToDecorate;
		}

		/**
		 * Decorates {@code executor}'s {@link RejectedExecutionHandler} to unwrap tasks from
		 * {@link TaskTracingExecutorDecorator}'s internal wrappers.
		 */
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

		/**
		 * Stores a {@link ForcedShutdownAftermath} for future use with
		 * {@link #getForcedShutdownAftermath()} and calls its backing executor.
		 */
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

		/**
		 * Hook to be called by a worker thread right before running {@code task}. This method is
		 * called automatically by this decorator's {@link #execute(Runnable)} method: it is
		 * exposed for low-level subclassing of various {@link ExecutorService}s, that embed
		 * {@link TaskTracingExecutorDecorator} and allow to hook actions before and after task
		 * executions (such as {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable)} and
		 * {@link ThreadPoolExecutor#afterExecute(Runnable, Throwable)}) <b>instead</b> of
		 * delegating its {@link #execute(Runnable)} method to this decorator.
		 * @see TaskTracingThreadPoolExecutor TaskTracingThreadPoolExecutor for a usage example.
		 */
		public void beforeExecute(Runnable task) {
			var localHolder = taskHolder.get();
			if (localHolder == null) {
				localHolder = new TaskHolder();
				taskHolder.set(localHolder);
				runningTasks.add(localHolder);
			}
			localHolder.task = task;
		}

		/**
		 * Hook to be called by a worker thread right after running a task. This method is
		 * called automatically by this decorator's {@link #execute(Runnable)} method: it is
		 * exposed for low-level subclassing of various {@link ExecutorService}s.
		 * @see #beforeExecute(Runnable)
		 */
		public void afterExecute() {
			taskHolder.get().task = null;
		}



		/**
		 * Wraps {@code task} with a decorator that automatically calls
		 * {@link #beforeExecute(Runnable)} and {@link #afterExecute()} and passes it to its backing
		 * executor.
		 */
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
