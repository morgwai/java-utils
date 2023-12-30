# Java utils

Some utility classes.<br/>
<br/>
**latest release: [4.0](https://search.maven.org/artifact/pl.morgwai.base/java-utils/4.0/jar)**
([javadoc](https://javadoc.io/doc/pl.morgwai.base/java-utils/4.0))

**Note:** from version 2.0, `java.util.logging` utilities have been moved to a separate [repo](https://github.com/morgwai/jul-utils).

## MAIN USER CLASSES

### [OrderedConcurrentOutputBuffer](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/concurrent/OrderedConcurrentOutputBuffer.html)
Buffers messages sent to some output stream until all of those that should be written before are available, so that they all can be written in the correct order. Useful for processing input streams in several concurrent threads when order of response messages must reflect the order of request messages. See a usage example [here](https://github.com/morgwai/grpc-utils/blob/v6.0/src/main/java/pl/morgwai/base/grpc/utils/OrderedConcurrentInboundObserver.java).

### [CallableTaskExecution](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/concurrent/CallableTaskExecution.html)
Adapter for a `Callable` so that it may be passed to `Executor.execute(Runnable)` and its result accessed using [CompletableFuture](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/CompletableFuture.html) API.

### [TaskTrackingExecutor](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/concurrent/TaskTrackingExecutor.html)
An `ExecutorService` that allows to obtain a list of tasks that were still running when an attempt to force terminate was made. 2 implementations are provided: [TaskTrackingThreadPoolExecutor](src/main/java/pl/morgwai/base/utils/concurrent/TaskTrackingThreadPoolExecutor.java) extending `ThreadPoolExecutor` and [ScheduledTaskTrackingThreadPoolExecutor](src/main/java/pl/morgwai/base/utils/concurrent/ScheduledTaskTrackingThreadPoolExecutor.java) extending `ScheduledThreadPoolExecutor`.

### [Awaitable](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/concurrent/Awaitable.html)
Utilities to await for multiple timed blocking operations, such as `Thread.join(timeout)`, `ExecutorService.awaitTermination(...)` etc. See a usage example [here](https://github.com/morgwai/grpc-utils/blob/v6.0/sample/src/main/java/pl/morgwai/samples/grpc/utils/SqueezedServer.java#L488-L497).

### [ConcurrentUtils](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/concurrent/ConcurrentUtils.html)
Some helper functions.

### [NoCopyByteArrayOutputStream](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/io/NoCopyByteArrayOutputStream.html)
A `ByteArrayOutputStream` that allows to directly access its underlying buffer after the stream was closed.
