package pl.morgwai.base.utils.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;



/**
 * A factory that names new {@link Thread}s based on constructor supplied generator/name.
 * Each instance has an associated thread group, which newly created threads will belong to.
 */
public class NamingThreadFactory implements ThreadFactory {



	final ThreadGroup threadGroup;
	final AtomicInteger threadNumber = new AtomicInteger(0);
	final IntFunction<String> threadNameGenerator;



	/**
	 * Constructs a factory that will create non-daemon threads with {@link Thread#NORM_PRIORITY}
	 * and names constructed using scheme {@code <name>-thread-<sequenceNumber>}. Created threads
	 * will belong to a newly created {@link ThreadGroup} named {@code name} associated with this
	 * factory.
	 */
	public NamingThreadFactory(String name) {
		this(createThreadGroup(name), (i) -> name + "-thread-" + i);
	}

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
	 * Constructs a factory that will create threads inside {@code threadGroup} with names
	 * constructed using {@code threadNameGenerator}. Created threads will derive priority from
	 * {@link ThreadGroup#getMaxPriority() threadGroup.getMaxPriority()} and daemon status from
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
