// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

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
	 * Calls {@link #shutdownNow()} and returns an object containing a list of tasks that were still
	 * running when this method was called, together with the list of tasks returned by
	 * {@link #shutdownNow()}.
	 */
	ForcedTerminationAftermath tryForceTerminate();



	/** Returned by {@link #tryForceTerminate()}. */
	class ForcedTerminationAftermath {

		/** List of tasks that were still running when {@link #tryForceTerminate()} was called. */
		public List<Runnable> getRunningTasks() { return runningTasks; }
		public final List<Runnable> runningTasks;

		/** List of tasks returned by {@link #shutdownNow()}. */
		public List<Runnable> getUnexecutedTasks() { return unexecutedTasks; }
		public final List<Runnable> unexecutedTasks;

		public ForcedTerminationAftermath(
				List<Runnable> runningTasks, List<Runnable> unexecutedTasks) {
			this.runningTasks = runningTasks;
			this.unexecutedTasks = unexecutedTasks;
		}
	}



	default Awaitable.WithUnit toAwaitableOfTermination() {
		return Awaitable.ofTermination(this);
	}



	default Awaitable.WithUnit toAwaitableOfEnforcedTermination() {
		return Awaitable.ofEnforcedTermination(this);
	}



	default void awaitTermination() throws InterruptedException {
		while ( !awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS));
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
					.map(task -> ((TrackableTask) task).wrappedTask)
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
		 * @param delegatingExecute should be {@code true} for executors that delegate
		 *     {@link #execute(Runnable)} to this decorator, {@code false} for those that use
		 *     execution hooks ({@link #storeTaskIntoHolderBeforeExecute(Runnable)},
		 *     {@link #clearTaskHolderAfterExecute()}) directly instead. If set to {@code true},
		 *     tasks returned by {@link #shutdownNow()} will be mapped to remove their wrapping
		 *     {@link TrackableTask} created by {@link #execute(Runnable)}.
		 * @param threadPoolSize size of {@code executorToDecorate}'s threadPool for optimization
		 *     purposes: will be used as an initial size for an internal {@link ConcurrentHashMap}.
		 * @see TaskTrackingThreadPoolExecutor TaskTrackingThreadPoolExecutor for a usage example.
		 */
		public TaskTrackingExecutorDecorator(
				ExecutorService executorToDecorate, boolean delegatingExecute, int threadPoolSize) {
			runningTasks = ConcurrentHashMap.newKeySet(threadPoolSize);
			unwrapTasks = delegatingExecute ? UNWRAP_TASKS : Function.identity();
			this.backingExecutor = executorToDecorate;
		}



		/**
		 * Decorates {@code executor}'s {@link RejectedExecutionHandler} to unwrap tasks from
		 * {@link TrackableTask} before passing them to the original handler.
		 */
		public static void decorateRejectedExecutionHandler(ThreadPoolExecutor executor) {
			final var originalHandler = executor.getRejectedExecutionHandler();
			executor.setRejectedExecutionHandler(
				(wrappedTask, rejectingExecutor) -> originalHandler.rejectedExecution(
					((TrackableTask) wrappedTask).wrappedTask,
					rejectingExecutor
				)
			);
		}



		@Override
		public ForcedTerminationAftermath tryForceTerminate() {
			return new ForcedTerminationAftermath(
				runningTasks.stream()
					.map((holder) -> holder.task)
					.filter(Objects::nonNull)
					.collect(Collectors.toList()),
				shutdownNow()
			);
		}



		@Override
		public List<Runnable> shutdownNow() {
			return unwrapTasks.apply(backingExecutor.shutdownNow());
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
		public void storeTaskIntoHolderBeforeExecute(Runnable task) {
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
		 * @see #storeTaskIntoHolderBeforeExecute(Runnable)
		 */
		public void clearTaskHolderAfterExecute() {
			taskHolder.get().task = null;
		}



		/** Wraps {@code task} with a {@link TrackableTask} and passes it to its backing executor.*/
		@Override
		public void execute(Runnable task) {
			backingExecutor.execute(new TrackableTask(task));
		}



		/**
		 * A decorator that automatically calls {@link #storeTaskIntoHolderBeforeExecute(Runnable)}
		 * and {@link #clearTaskHolderAfterExecute()}.
		 */
		public class TrackableTask implements Runnable {

			public Runnable getWrappedTask() { return wrappedTask; }
			final Runnable wrappedTask;

			protected TrackableTask(Runnable taskToWrap) {
				wrappedTask = taskToWrap;
			}

			@Override public void run() {
				storeTaskIntoHolderBeforeExecute(wrappedTask);
				try {
					wrappedTask.run();
				} finally {
					clearTaskHolderAfterExecute();
				}
			}

			@Override public String toString() {
				return  wrappedTask.toString();
			}
		}



		/**
		 * If {@code task} is an instance of {@link TrackableTask} returns
		 * {@link TrackableTask#getWrappedTask() task.getWrappedTask()}, otherwise just
		 * {@code task}.
		 */
		public static Runnable unwrapTask(Runnable task) {
			return task instanceof TrackableTask ? ((TrackableTask) task).wrappedTask : task;
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
