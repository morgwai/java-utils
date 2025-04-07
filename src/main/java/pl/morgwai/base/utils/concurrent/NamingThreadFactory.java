// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;



/**
 * {@link ThreadFactory} that {@link Thread#getName() names} new {@link Thread}s based on a
 * constructor supplied generator/name.
 * Each instance is associated with a {@link ThreadGroup} which newly created {@link Thread}s will
 * belong to.
 */
public class NamingThreadFactory implements ThreadFactory {



	final ThreadGroup threadGroup;
	final AtomicInteger threadNumber = new AtomicInteger(0);
	final IntFunction<String> threadNameGenerator;



	/**
	 * Constructs a {@link ThreadFactory} of {@link Thread#isDaemon() non-daemon} {@link Thread}s
	 * with {@link Thread#NORM_PRIORITY NORM_PRIORITY} and {@link Thread#getName() named}
	 * {@code name +} {@value THREAD_NAME_INFIX} {@code + sequenceNumber}.
	 * Created {@link Thread}s will belong to a newly created {@link ThreadGroup}
	 * {@link ThreadGroup#getName() named} {@code name} associated with this {@link ThreadFactory}.
	 */
	public NamingThreadFactory(String name) {
		this(createThreadGroup(name), (i) -> name + THREAD_NAME_INFIX + i);
	}

	static final String THREAD_NAME_INFIX = "-thread-";

	static ThreadGroup createThreadGroup(String name) {
		final var securityManager = System.getSecurityManager();
		final var parentGroup = securityManager != null
			? securityManager.getThreadGroup()
			: Thread.currentThread().getThreadGroup();
		final var newGroup = new ThreadGroup(parentGroup, name);
		newGroup.setMaxPriority(Thread.NORM_PRIORITY);
		newGroup.setDaemon(false);
		return newGroup;
	}



	/**
	 * Constructs a {@link ThreadFactory} of {@link Thread}s belonging to {@code threadGroup} with
	 * {@link Thread#getName() names} constructed using {@code threadNameGenerator}.
	 * Created {@link Thread}s will derive {@link Thread#getPriority() priority} from
	 * {@link ThreadGroup#getMaxPriority() threadGroup.getMaxPriority()} and
	 * {@link Thread#isDaemon() daemon status} from
	 * {@link ThreadGroup#isDaemon() threadGroup.isDaemon()}.
	 */
	public NamingThreadFactory(ThreadGroup threadGroup, IntFunction<String> threadNameGenerator) {
		this.threadGroup = threadGroup;
		this.threadNameGenerator = threadNameGenerator;
	}



	@Override
	public Thread newThread(Runnable task) {
		final var newThread = new Thread(
			threadGroup,
			task,
			threadNameGenerator.apply(threadNumber.incrementAndGet())
		);
		newThread.setPriority(threadGroup.getMaxPriority());
		newThread.setDaemon(threadGroup.isDaemon());
		return newThread;
	}
}
