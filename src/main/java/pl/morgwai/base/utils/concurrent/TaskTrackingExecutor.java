// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static java.util.stream.Collectors.toUnmodifiableList;



/**
 * {@link ExecutorService} that allows to obtain a {@link #getRunningTasks() List of currently
 * running tasks}.
 * Useful for debugging which tasks got stuck and prevented clean
 * {@link ExecutorService#awaitTermination(long, TimeUnit) termination} or for monitoring.
 * @see TaskTrackingThreadPoolExecutor
 * @see ScheduledTaskTrackingThreadPoolExecutor
 */
public interface TaskTrackingExecutor extends ExecutorService {



	/**
	 * Returns a {@code List} of tasks currently being run by the worker {@code Threads}.
	 * Unless stated otherwise by an implementing class, this may be a subject to all kind of races
	 * and thus may sometimes not even be fully consistent with any point in the past. This method
	 * is intended for spotting long-running or stuck tasks or for general overview of types
	 * of tasks being executed.
	 */
	List<Runnable> getRunningTasks();



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
	 * Decorator for an {@link ExecutorService} that makes it a {@link TaskTrackingExecutor}.
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
		 * {@code executorToDecorate} must be idle at the time of decoration.
		 * Afterwards {@code executorToDecorate} must be used only via the constructed decorator.
		 * @param threadPoolSize used as a concurrency-level hint.
		 */
		public TaskTrackingExecutorDecorator(ExecutorService executorToDecorate, int threadPoolSize)
		{
			this(executorToDecorate, false, threadPoolSize);
		}



		/** See {@link #TaskTrackingExecutorDecorator(ExecutorService, int)}. */
		public TaskTrackingExecutorDecorator(ExecutorService executorToDecorate) {
			this(executorToDecorate, -1);
		}



		/**
		 * Hooking capabilities allow to avoid wrapping tasks with {@link TrackableTask}.
		 * Note: unless an implementing class states otherwise, hooks are not guaranteed to be
		 * isolated from each other nor from tasks being executed, so if a hook throws, the others
		 * and the given task may not be executed.
		 */
		public interface HookableExecutor extends ExecutorService {
			void addBeforeExecuteHook(BiConsumer<Thread, Runnable> hook);
			void addAfterExecuteHook(BiConsumer<Runnable, Throwable> hook);
		}



		/** See {@link #TaskTrackingExecutorDecorator(ExecutorService, int)}. */
		public TaskTrackingExecutorDecorator(
			HookableExecutor executorToDecorate,
			int threadPoolSize
		) {
			this(executorToDecorate, true, threadPoolSize);
			executorToDecorate.addBeforeExecuteHook(
				(worker, task) -> storeTaskIntoHolderBeforeExecute(task));
			executorToDecorate.addAfterExecuteHook((task, error) -> clearTaskHolderAfterExecute());
		}



		/** See {@link #TaskTrackingExecutorDecorator(ExecutorService, int)}. */
		public TaskTrackingExecutorDecorator(HookableExecutor executorToDecorate) {
			this(executorToDecorate, -1);
		}



		/**
		 * Decorates {@code executorToDecorate}.
		 * Decorates {@link RejectedExecutionHandler} and {@link ThreadFactory} of
		 * {@code executorToDecorate} in the process.
		 * Afterwards {@code executorToDecorate} must be used only via the constructed decorator.
		 * @throws IllegalStateException if {@code executorToDecorate} is not idle.
		 */
		public TaskTrackingExecutorDecorator(ThreadPoolExecutor executorToDecorate) {
			this(executorToDecorate, false, executorToDecorate.getCorePoolSize());
			if (executorToDecorate.getActiveCount() > 0) {
				throw new IllegalStateException(
						"executor must be idle to decorate it with TaskTrackingExecutorDecorator");
			}
			final var originalHandler = executorToDecorate.getRejectedExecutionHandler();
			executorToDecorate.setRejectedExecutionHandler(
				(wrappedTask, rejectingExecutor) -> originalHandler.rejectedExecution(
					((TrackableTask) wrappedTask).wrappedTask,
					rejectingExecutor
				)
			);
			executorToDecorate.setThreadFactory(
					decorateThreadFactory(executorToDecorate.getThreadFactory()));
			final int corePoolSize = executorToDecorate.getCorePoolSize();
			executorToDecorate.setCorePoolSize(0);
			executorToDecorate.setCorePoolSize(corePoolSize);
		}



		/**
		 * Decorates {@code factoryToDecorate} to create worker {@link Thread}s, that upon their
		 * exit remove their monitoring hooks from this {@code TaskTrackingExecutorDecorator}.
		 */
		public ThreadFactory decorateThreadFactory(ThreadFactory factoryToDecorate) {
			return (task) -> factoryToDecorate.newThread(() -> {
				try {
					task.run();
				} finally {
					final var taskHolder = threadLocalTaskHolder.get();
					if (taskHolder != null) runningTasks.remove(taskHolder);
				}
			});
		}



		final boolean backingExecutorHookable;



		TaskTrackingExecutorDecorator(
			ExecutorService executorToDecorate,
			boolean backingExecutorHookable,
			int threadPoolSize
		) {
			runningTasks = (threadPoolSize > 0)
					? ConcurrentHashMap.newKeySet(threadPoolSize) : ConcurrentHashMap.newKeySet();
			this.backingExecutor = executorToDecorate;
			this.backingExecutorHookable = backingExecutorHookable;
		}



		@Override
		public List<Runnable> getRunningTasks() {
			return runningTasks.stream()
				.map((holder) -> holder.task)
				.filter(Objects::nonNull)
				.collect(toUnmodifiableList());
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



		ThreadLocal<TaskHolder> threadLocalTaskHolder = new ThreadLocal<>();



		void storeTaskIntoHolderBeforeExecute(Runnable task) {
			var taskHolder = threadLocalTaskHolder.get();
			if (taskHolder == null) {
				taskHolder = new TaskHolder();
				threadLocalTaskHolder.set(taskHolder);
				runningTasks.add(taskHolder);
			}
			taskHolder.task = task;
		}



		void clearTaskHolderAfterExecute() {
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



		/** A decorator that tracks execution of its wrapped task. */
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
