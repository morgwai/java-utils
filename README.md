# Java utils

Some utility classes.<br/>
<br/>
**latest release: [1.9](https://search.maven.org/artifact/pl.morgwai.base/java-utils/1.9/jar)**
([javadoc](https://javadoc.io/doc/pl.morgwai.base/java-utils/1.9))


## MAIN USER CLASSES

### [OrderedConcurrentOutputBuffer](src/main/java/pl/morgwai/base/concurrent/OrderedConcurrentOutputBuffer.java)
Buffers messages sent to some output stream until all of those that should be written before are available, so that they all can be written in the correct order. Useful for processing input streams in several concurrent threads when order of response messages must reflect the order of request messages.

### [Awaitable](src/main/java/pl/morgwai/base/concurrent/Awaitable.java)
Utilities to await for multiple timed blocking operations, such as `Thread.join(timeout)`, `ExecutorService.awaitTermination(...)` etc.

### [JulFormatter](src/main/java/pl/morgwai/base/logging/JulFormatter.java)
A text log formatter similar to `SimpleFormatter` that additionally allows to format stack trace elements and to add log sequence id and thread id to log entries.

### [JulConfig](src/main/java/pl/morgwai/base/logging/JulConfig.java)
Overrides logging levels of `java.util.logging` `Logger`s `Handler`s with values obtained from system properties.<br/>
Note: overriding can be applied to an existing java app at startup: just add java-utils jar to the class-path and define desired system properties.

### [JulManualResetLogManager](src/main/java/pl/morgwai/base/logging/JulManualResetLogManager.java)
A LogManager that does not get reset automatically at JVM shutdown. Useful if logs from user shutdown hooks are important.
