# Java utils

Some utility classes.<br/>
<br/>
**latest release: [2.2](https://search.maven.org/artifact/pl.morgwai.base/java-utils/2.2/jar)**
([javadoc](https://javadoc.io/doc/pl.morgwai.base/java-utils/2.2))

**Note:** from version 2.0, `java.util.logging` utilities have been moved to a separate [repo](https://github.com/morgwai/jul-utils).

## MAIN USER CLASSES

### [OrderedConcurrentOutputBuffer](src/main/java/pl/morgwai/base/utils/concurrent/OrderedConcurrentOutputBuffer.java)
Buffers messages sent to some output stream until all of those that should be written before are available, so that they all can be written in the correct order. Useful for processing input streams in several concurrent threads when order of response messages must reflect the order of request messages. See a usage example [here](https://github.com/morgwai/grpc-utils/blob/v3.1/src/main/java/pl/morgwai/base/grpc/utils/OrderedConcurrentInboundObserver.java).

### [Awaitable](src/main/java/pl/morgwai/base/utils/concurrent/Awaitable.java)
Utilities to await for multiple timed blocking operations, such as `Thread.join(timeout)`, `ExecutorService.awaitTermination(...)` etc. See a usage example [here](https://github.com/morgwai/grpc-utils/blob/v3.1/sample/src/main/java/pl/morgwai/samples/grpc/utils/SqueezedServer.java#L502).

### [ConcurrentUtils](src/main/java/pl/morgwai/base/utils/concurrent/ConcurrentUtils.java)
Some helper functions.

### [TaskTrackingExecutor](src/main/java/pl/morgwai/base/utils/concurrent/TaskTrackingExecutor.java)
An `ExecutorService` that allows to obtain a list of tasks that were still running when an attempt to force terminate was made. 2 implementations are provided: [TaskTrackingThreadPoolExecutor](src/main/java/pl/morgwai/base/utils/concurrent/TaskTrackingThreadPoolExecutor.java) extending `ThreadPoolExecutor` and [ScheduledTaskTrackingThreadPoolExecutor](src/main/java/pl/morgwai/base/utils/concurrent/ScheduledTaskTrackingThreadPoolExecutor.java) extending `ScheduledThreadPoolExecutor`.

### [NoCopyByteArrayOutputStream](src/main/java/pl/morgwai/base/utils/io/NoCopyByteArrayOutputStream.java)
A `ByteArrayOutputStream` that allows to directly access its underlying buffer after the stream was closed.
