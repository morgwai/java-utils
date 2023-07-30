// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;



/**
 * An {@link ExecutorService} that allows to obtain a list of tasks that were still running when
 * an {@link #tryForceTerminate() attempt to force terminate} was made.
 * @see TaskTrackingThreadPoolExecutor
 * @see ScheduledTaskTrackingThreadPoolExecutor
 */
public interface TaskTrackingExecutor extends ExecutorService {



	/**
	 * Calls {@link #shutdownNow()} and returns an object containing a list of tasks that that were
	 * still running when this method was called together with the list of tasks that were removed
	 * from this executor's queue as returned by {@link #shutdownNow()}.
	 */
	ForcedTerminateAftermath tryForceTerminate();

	/** Returned by {@link #tryForceTerminate()}. */
	class ForcedTerminateAftermath {

		/** List of tasks that were still running when {@link #tryForceTerminate()} was called. */
		public List<Runnable> getRunningTasks() { return runningTasks; }
		public final List<Runnable> runningTasks;

		/** List of tasks returned by {@link #shutdownNow()}. */
		public List<Runnable> getUnexecutedTasks() { return unexecutedTasks; }
		public final List<Runnable> unexecutedTasks;

		public ForcedTerminateAftermath(List<Runnable> runningTasks, List<Runnable> unexecutedTasks)
		{
			this.runningTasks = runningTasks;
			this.unexecutedTasks = unexecutedTasks;
		}
	}



	/**
	 * A decorator for an {@link ExecutorService} that makes its target a
	 * {@link TaskTrackingExecutor}.
	 * @see TaskTrackingThreadPoolExecutor
	 * @see ScheduledTaskTrackingThreadPoolExecutor
	 */
	class TaskTrackingExecutorDecorator extends AbstractExecutorService
			implements TaskTrackingExecutor {



		final ExecutorService backingExecutor;

		final Set<TaskHolder> runningTasks;
		static class TaskHolder { volatile Runnable task; }

		final Function<List<Runnable>, List<Runnable>> unwrapTasks;
		static final Function<List<Runnable>, List<Runnable>> UNWRAP_TASKS =
				(tasks) -> tasks.stream()
					.map(task -> ((TaskWrapper) task).wrappedTask)
					.collect(Collectors.toList());



		/** Decorates {@code executorToDecorate}. */
		public TaskTrackingExecutorDecorator(ExecutorService executorToDecorate) {
			this(executorToDecorate, true, 1);
		}

		/**
		 * Decorates {@code executorToDecorate} and calls
		 * {@link #decorateRejectedExecutionHandler(ThreadPoolExecutor)
		 * decorateRejectedExecutionHandler(executorToDecorate)}.
		 */
		public TaskTrackingExecutorDecorator(ThreadPoolExecutor executorToDecorate) {
			this(executorToDecorate, true, executorToDecorate.getCorePoolSize());
			decorateRejectedExecutionHandler(executorToDecorate);
		}

		/**
		 * Decorates {@code executorToDecorate}. This is a low-level constructor for subclasses of
		 * various {@link ExecutorService}s, that embed {@link TaskTrackingExecutorDecorator} to
		 * delegate methods to it and thus provide {@link TaskTrackingExecutor} API.
		 * @param executorToDecorate executor to decorate.
		 * @param delegatingExecute must be {@code true} for executors that delegate
		 *     {@link #execute(Runnable)} to this decorator, {@code false} for those that use
		 *     execution hooks ({@link #beforeExecute(Runnable)}, {@link #afterExecute()}) directly
		 *     instead. If set to {@code true} tasks returned by {@link #shutdownNow()} will be
		 *     mapped to remove their wrapping decorators created internally by
		 *     {@link #execute(Runnable)}.
		 * @param threadPoolSize size of {@code executorToDecorate}'s threadPool for optimization
		 *     purposes: will be used a size for internal {@link ConcurrentHashMap}.
		 * @see TaskTrackingThreadPoolExecutor TaskTrackingThreadPoolExecutor for usage example.
		 */
		public TaskTrackingExecutorDecorator(
				ExecutorService executorToDecorate, boolean delegatingExecute, int threadPoolSize) {
			runningTasks = ConcurrentHashMap.newKeySet(threadPoolSize);
			unwrapTasks = delegatingExecute ? UNWRAP_TASKS : Function.identity();
			this.backingExecutor = executorToDecorate;
		}

		/**
		 * Decorates {@code executor}'s {@link RejectedExecutionHandler} to unwrap tasks from
		 * {@link TaskTrackingExecutorDecorator}'s internal wrappers before passing them to the
		 * original handler.
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



		@Override
		public ForcedTerminateAftermath tryForceTerminate() {
			return new ForcedTerminateAftermath(
				runningTasks.stream()
					.map((holder) -> holder.task)
					.collect(Collectors.toList()),
				unwrapTasks.apply(backingExecutor.shutdownNow())
			);
		}




		ThreadLocal<TaskHolder> taskHolder = new ThreadLocal<>();

		/**
		 * Hook to be called by a worker thread right before running {@code task}. This method is
		 * called automatically by this decorator's {@link #execute(Runnable)} method: it is
		 * exposed for low-level subclassing of various {@link ExecutorService}s, that embed
		 * {@link TaskTrackingExecutorDecorator} and allow to hook actions before and after task
		 * executions (such as {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable)} and
		 * {@link ThreadPoolExecutor#afterExecute(Runnable, Throwable)}) <b>instead</b> of
		 * delegating its {@link #execute(Runnable)} method to this decorator.
		 * @see TaskTrackingThreadPoolExecutor TaskTrackingThreadPoolExecutor for a usage example.
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
		public List<Runnable> shutdownNow() {
			return backingExecutor.shutdownNow();
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
