// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.concurrent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;



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
		final ConcurrentMap<Thread, Runnable> runningTasks = new ConcurrentHashMap<>();



		public TaskTracingExecutorDecorator(ExecutorService backingExecutor) {
			this.backingExecutor = backingExecutor;
		}



		public void beforeExecute(Thread worker, Runnable task) {
			runningTasks.put(worker, task);
		}

		public void afterExecute() {
			runningTasks.remove(Thread.currentThread());
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
				backingExecutor.shutdownNow()
			);
			return aftermath.unexecutedTasks;
		}



		// only dumb delegations to backingExecutor below:

		@Override
		public void execute(Runnable task) {
			backingExecutor.execute(task);
		}

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
