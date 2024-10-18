# Java utils

Some utility classes.<br/>
Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0.<br/>
<br/>
**latest release: [5.1](https://search.maven.org/artifact/pl.morgwai.base/java-utils/5.1/jar)**
([javadoc](https://javadoc.io/doc/pl.morgwai.base/java-utils/5.1))

**Note:** from version 2.0, `java.util.logging` utilities have been moved to a separate [repo](https://github.com/morgwai/jul-utils).

## MAIN USER CLASSES

### [OrderedConcurrentOutputBuffer](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/concurrent/OrderedConcurrentOutputBuffer.html)
Buffers messages until all of those that should be written before to the underlying output stream are available, so that they all can be written in the desired order. Useful for processing input streams in several concurrent Threads when the order of the resulting outbound messages must reflect the order of inbound messages. See a usage example [here](https://github.com/morgwai/grpc-utils/blob/v6.0/src/main/java/pl/morgwai/base/grpc/utils/OrderedConcurrentInboundObserver.java).

### [CallableTaskExecution](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/concurrent/CallableTaskExecution.html)
Adapter for a `Callable` that allows to pass it to `Executor.execute(Runnable)` and access its results using [CompletableFuture](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/CompletableFuture.html) API.

### [TaskTrackingExecutor](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/concurrent/TaskTrackingExecutor.html)
`ExecutorService` that allows to obtain a List of currently running tasks. Useful for monitoring or debugging which tasks got stuck and prevented clean termination. 2 implementations are provided: [TaskTrackingThreadPoolExecutor](src/main/java/pl/morgwai/base/utils/concurrent/TaskTrackingThreadPoolExecutor.java) extending `ThreadPoolExecutor` and [ScheduledTaskTrackingThreadPoolExecutor](src/main/java/pl/morgwai/base/utils/concurrent/ScheduledTaskTrackingThreadPoolExecutor.java) extending `ScheduledThreadPoolExecutor`.

### [Awaitable](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/concurrent/Awaitable.html)
Utilities to await for multiple timed blocking operations, such as `Thread.join(timeout)`, `ExecutorService.awaitTermination(...)` etc. See a usage example [here](https://github.com/morgwai/grpc-utils/blob/v6.0/sample/src/main/java/pl/morgwai/samples/grpc/utils/SqueezedServer.java#L488-L497).

### [NoCopyByteArrayOutputStream](https://javadoc.io/doc/pl.morgwai.base/java-utils/latest/pl/morgwai/base/utils/io/NoCopyByteArrayOutputStream.html)
`ByteArrayOutputStream` that allows to directly access its underlying buffer (without copying) after the stream was closed.
