# Summaries of visible changes between releases

### 5.2
- `TaskTrackingExecutor`: add convenience `tryEnforceTermination(...)` method.
- `Awaitable`: `awaitMultiple(...)`: ensure operations do not block after an interrupt, clear interrupt status when throwing an `AwaitInterruptedException`.

### 5.1
- `TaskTrackingThreadPoolExecutor`: add constructor(poolSize, threadFactory).

### 5.0
- `CallableTaskExecution`: `run()`: also pass `Error`s thrown by `call()` to `completeExceptionally(e)`.
- `TaskTrackingExecutor`: remove deprecated `decorateRejectedExecutionHandler(executor)` static helper.
