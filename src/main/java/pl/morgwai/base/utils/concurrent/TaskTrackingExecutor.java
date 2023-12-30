// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static java.util.stream.Collectors.toUnmodifiableList;



/**
 * An {@link ExecutorService} that allows to obtain a list of tasks that were still running when
 * an {@link #tryForceTerminate() attempt to force terminate} was made.
 * @see TaskTrackingThreadPoolExecutor
 * @see ScheduledTaskTrackingThreadPoolExecutor
 */
public interface TaskTrackingExecutor extends ExecutorService {



	/**
	 * Calls {@link #shutdownNow()} and returns an object containing a {@code List} of tasks, that
	 * were still running right before the call.
	 * The returned object also contains the {@code List} of tasks returned by
	 * {@link #shutdownNow()} itself.
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



		/**
		 * Decorates {@code executorToDecorate}.
		 * @param threadPoolSize used as a concurrency-level hint.
		 */
		public TaskTrackingExecutorDecorator(ExecutorService executorToDecorate, int threadPoolSize)
		{
			this(executorToDecorate, true, threadPoolSize);
		}



		/** Decorates {@code executorToDecorate}. */
		public TaskTrackingExecutorDecorator(ExecutorService executorToDecorate) {
			this(executorToDecorate, -1);
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



		/** Hooking capabilities allow to avoid wrapping tasks with {@link TrackableTask}. */
		public interface HookableExecutor extends ExecutorService {
			void addBeforeExecuteHook(BiConsumer<Thread, Runnable> hook);
			void addAfterExecuteHook(BiConsumer<Runnable, Throwable> hook);
		}



		/**
		 * Decorates {@code executorToDecorate}.
		 * @param threadPoolSize used as a concurrency-level hint.
		 */
		public TaskTrackingExecutorDecorator(
			HookableExecutor executorToDecorate,
			int threadPoolSize
		) {
			this(executorToDecorate, false, threadPoolSize);
			executorToDecorate.addBeforeExecuteHook(
					(thread, task) -> storeTaskIntoHolderBeforeExecute(task));
			executorToDecorate.addAfterExecuteHook((task, error) -> clearTaskHolderAfterExecute());
		}



		public TaskTrackingExecutorDecorator(HookableExecutor executorToDecorate) {
			this(executorToDecorate, -1);
		}



		final boolean backingExecutorHookable;



		/** @deprecated use {@link #TaskTrackingExecutorDecorator(HookableExecutor, int)} instead.*/
		@Deprecated(forRemoval = true)
		public TaskTrackingExecutorDecorator(
			ExecutorService executorToDecorate,
			boolean delegatingExecute,
			int threadPoolSize
		) {
			runningTasks = (threadPoolSize > 0)
					? ConcurrentHashMap.newKeySet(threadPoolSize) : ConcurrentHashMap.newKeySet();
			this.backingExecutor = executorToDecorate;
			this.backingExecutorHookable = !delegatingExecute;
		}



		@Override
		public List<Runnable> shutdownNow() {
			return backingExecutorHookable
					? backingExecutor.shutdownNow()
					: backingExecutor.shutdownNow().stream()
						.map(TrackableTask.class::cast)
						.map(TrackableTask::getWrappedTask)
						.collect(toUnmodifiableList());
		}



		@Override
		public ForcedTerminationAftermath tryForceTerminate() {
			return new ForcedTerminationAftermath(
				runningTasks.stream()
					.map((holder) -> holder.task)
					.filter(Objects::nonNull)
					.collect(toUnmodifiableList()),
				shutdownNow()
			);
		}



		ThreadLocal<TaskHolder> threadLocalTaskHolder = new ThreadLocal<>();



		/** @deprecated use {@link #TaskTrackingExecutorDecorator(HookableExecutor, int)}. */
		@Deprecated(forRemoval = true)
		public void storeTaskIntoHolderBeforeExecute(Runnable task) {
			var taskHolder = threadLocalTaskHolder.get();
			if (taskHolder == null) {
				taskHolder = new TaskHolder();
				threadLocalTaskHolder.set(taskHolder);
				runningTasks.add(taskHolder);
			}
			taskHolder.task = task;
		}



		/** @deprecated use {@link #TaskTrackingExecutorDecorator(HookableExecutor, int)}. */
		@Deprecated(forRemoval = true)
		public void clearTaskHolderAfterExecute() {
			threadLocalTaskHolder.get().task = null;
		}



		/**
		 * Wraps {@code task} with a {@link TrackableTask} if needed and passes it to its backing
		 * executor.
		 * If the backing executor is a {@link HookableExecutor}, no wrapping is needed and
		 * {@code task} is directly passed to the backing executor.
		 */
		@Override
		public void execute(Runnable task) {
			backingExecutor.execute(backingExecutorHookable ? task : new TrackableTask(task));
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
