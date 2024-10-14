// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.LinkedList;
import java.util.List;



/**
 * Buffers messages until all of those that should be written before to
 * {@link OutputStream the underlying output stream} are available, so that they all can be
 * {@link OutputStream#write(Object) written} in the desired order.
 * Useful for processing input streams in several concurrent {@code Threads} when the order of the
 * resulting outbound messages must reflect the order of inbound messages.
 * <p>
 * An {@code OrderedConcurrentOutputBuffer} consists of ordered {@code Bucket}s that implement
 * {@link OutputStream} just as the underlying output stream passed to
 * {@link #OrderedConcurrentOutputBuffer(OutputStream) the constructor}. The user can
 * {@link #addBucket() add a new Bucket} at the end of the {@code OrderedConcurrentOutputBuffer},
 * {@link OutputStream#write(Object) write messages} to any open {@code Bucket} and finally
 * {@link OutputStream#close() close a Bucket} to indicate that no more messages will be written to
 * it. Each {@code Bucket} gets flushed automatically to the underlying output stream after all the
 * preceding {@code Bucket}s are closed.<br/>
 * Within each {@code Bucket}, messages will be written to the output in the order they were
 * buffered.</p>
 * <p>
 * When it is known that no more {@code Bucket}s will be added (usually upon the end of the input
 * stream being processed), {@link #signalNoMoreBuckets()} should be called. After this, when all
 * existing {@code Bucket}s get closed, the underlying output stream will be
 * {@link OutputStream#close() closed} automatically.</p>
 * <p>
 * All {@code Bucket} methods are thread-safe. Each {@code Bucket} has its own lock, so operations
 * on separate {@code Bucket}s do not block each other. Also {@link #signalNoMoreBuckets()} may be
 * called concurrently with any existing {@code Bucket} methods without an additional
 * synchronization.<br/>
 * {@link #addBucket()} is <b>not</b> thread-safe however and its concurrent invocations as well as
 * invocations concurrent with {@link #signalNoMoreBuckets()} must be properly synchronized. Note
 * that in case of websockets and gRPC, it is usually not an issue as {@code Endpoint}s and inbound
 * {@code StreamObserver}s are guaranteed to be called by only one {@code Thread} at a time.</p>
 * <p>
 * Note: this class should only be used if the outbound messages order requirement cannot be
 * dropped: if you control a given stream API, then it's more efficient to add some unique ids to
 * inbound messages, include them in the corresponding outbound messages and send them as soon as
 * they are produced, so nothing needs to be buffered.</p>
 */
public class OrderedConcurrentOutputBuffer<MessageT> {



	/** Interface for the underlying stream as well as for {@link #addBucket() added Buckets}. */
	public interface OutputStream<MessageT> {
		void write(MessageT message);
		void close();
	}



	/** The underlying output stream. */
	final OutputStream<MessageT> output;

	/**
	 * An {@code OrderedConcurrentOutputBuffer} always has a preallocated (not yet handed out to the
	 * user) guard {@code Bucket} at the tail of its {@code Bucket} queue. As {@link #addBucket()}
	 * is synchronized on {@code tailGuard}, it prevents {@link #addBucket()} to be delayed if the
	 * last "real" {@code Bucket} handed out is just being {@link Bucket#flush() flushed}.
	 */
	Bucket tailGuard;

	/** Switched by {@link #signalNoMoreBuckets()}. */
	boolean noMoreBuckets = false;



	public OrderedConcurrentOutputBuffer(OutputStream<MessageT> outputStream) {
		this.output = outputStream;
		tailGuard = new Bucket();
		tailGuard.buffer = null;  // the first Bucket is initially flushed.
	}



	/**
	 * Adds a new empty {@code Bucket} at the end of this {@code OrderedConcurrentOutputBuffer}.
	 * <p>
	 * This method is <b>not</b> thread-safe and its concurrent invocations as well as invocations
	 * concurrent with {@link #signalNoMoreBuckets()} must be properly synchronized. Note that in
	 * case of websockets and gRPC, it is usually not an issue as {@code Endpoint}s and inbound
	 * {@code StreamObserver}s are guaranteed to be called by only one {@code Thread} at a
	 * time.<br/>
	 * This method may nevertheless be called concurrently with any existing {@code Bucket} methods
	 * without an additional synchronization.</p>
	 * @return an empty {@code Bucket} placed right after the one returned by the previous call to
	 *     this method (or the first one if this is the first call). All methods of the returned
	 *     {@code Bucket} are thread-safe.
	 * @throws IllegalStateException if {@link #signalNoMoreBuckets()} has already been called.
	 */
	public OutputStream<MessageT> addBucket() {
		// The below synchronization does not guarantee thread-safety: if 2 Threads that call
		// addBucket() synchronize on the same tailGuard, they will branch the queue into 2.
		// Synchronization here is for memory consistency with Threads that may be trying to flush
		// the tailGuard at the same time.
		synchronized (tailGuard.lock) {
			if (noMoreBuckets) {
				throw new IllegalStateException("noMoreBuckets already signaled");
			}

			// return the current tailGuard after adding a new one after it
			final var newRealTail = tailGuard;
			tailGuard = new Bucket();
			newRealTail.next = tailGuard;
			return newRealTail;
		}
	}



	/**
	 * A call to this method indicates that no more new {@code Bucket}s will be
	 * {@link #addBucket() added}.
	 * This is usually done upon the end of the input stream being processed. As a result, when
	 * all the existing {@code Bucket}s are {@link OutputStream#close() closed}, the underlying
	 * output stream will be closed automatically.
	 * <p>
	 * This method may be may be called concurrently with any existing {@code Bucket} methods
	 * without an additional synchronization. However invocations concurrent with
	 * {@link #addBucket()} must be properly synchronized.</p>
	 */
	public void signalNoMoreBuckets() {
		synchronized (tailGuard.lock) {
			noMoreBuckets = true;
			// tailGuard has no buffer <=> it's flushed <=> all previous Buckets closed & flushed
			if (tailGuard.buffer == null) output.close();
		}
	}



	/**
	 * A list of messages with a fixed position relatively to other {@code Bucket}s of the enclosing
	 * {@code OrderedConcurrentOutputBuffer}.<br/>
	 * All methods are thread-safe.
	 * @see #addBucket()
	 */
	class Bucket implements OutputStream<MessageT> {

		/** null <=> {@link #flush() flushed} <=> all previous {@link #closed closed} && flushed */
		List<MessageT> buffer = new LinkedList<>();

		/**
		 * Switched in {@link #close()}.<br/>
		 * ({@link #buffer} == null && !closed) <=> this is the head (the first unclosed one)
		 */
		boolean closed = false;

		/** null <=> this {@code Bucket} is the current {@link #tailGuard}. */
		Bucket next;

		/** All {@code Bucket} methods are synchronized on this lock. */
		final Object lock = new Object();



		/**
		 * Appends {@code message} to the end of this {@code Bucket}. If this is the head (the first
		 * unclosed one), then {@code message} will be written directly to {@link #output the
		 * underlying output stream}. Otherwise it will be buffered in {@link #buffer} until all
		 * the previous {@code Bucket} are {@link #close() closed} (and {@link #flush() flushed}).
		 */
		@Override
		public void write(MessageT message) {
			synchronized (lock) {
				if (closed) throw new IllegalStateException(BUCKET_CLOSED_MESSAGE);
				if (buffer == null) {
					output.write(message);
				} else {
					buffer.add(message);
				}
			}
		}



		/**
		 * Marks this {@code Bucket} as {@link #closed}. If this {@code Bucket} is the head one (the
		 * first unclosed one), then all the subsequent closed {@code Bucket}s and the first
		 * unclosed one are {@link #flush() flushed}.
		 * <p>
		 * The first unclosed {@code Bucket} becomes the new head and its messages will be
		 * {@link OutputStream#write(Object) written} directly to
		 * {@link #output the underlying output stream} from now on.</p>
		 * <p>
		 * If all the {@code Bucket}s are closed (and flushed) and {@link #signalNoMoreBuckets()}
		 * has already been called, then {@link #output the underlying output stream} will be
		 * {@link OutputStream#close() closed}.</p>
		 */
		@Override
		public void close() {
			synchronized (lock) {
				if (closed) throw new IllegalStateException(BUCKET_CLOSED_MESSAGE);
				closed = true;
				// if this was the head Bucket, then flush the subsequent continuous closed chain
				if (buffer == null) next.flush();
			}
		}



		/**
		 * {@link OutputStream#write(Object) Writes} all messages in this {@code Bucket} to
		 * {@link #output the underlying output stream}. If this {@code Bucket} is already
		 * {@link #closed}, then recursively flushes {@link #next the next Bucket}.
		 * <p>
		 * If there is no next {@code Bucket} (meaning this is {@link #tailGuard}) and
		 * {@link #signalNoMoreBuckets()} has already been called, then
		 * {@link #output the underlying output stream} will be {@link OutputStream#close() closed}.
		 * </p>
		 * <p>
		 * Flushing of each {@code Bucket} is synchronized only on its own {@link #lock}, so any
		 * operations on subsequent {@code Bucket}s performed by other {@code Threads} are not
		 * delayed.</p>
		 */
		private void flush() {
			synchronized (lock) {
				for (var bufferedMessage: buffer) output.write(bufferedMessage);
				buffer = null;
				if (next != null) {
					if (closed) next.flush();
				} else {  // this is tailGuard, so all "real" Buckets are closed && flushed
					if (noMoreBuckets) output.close();
				}
			}
		}
	}



	static final String BUCKET_CLOSED_MESSAGE = "bucket already closed";
}
