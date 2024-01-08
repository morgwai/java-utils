// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.concurrent;

import java.util.LinkedList;
import java.util.List;



/**
 * Buffers messages until all of those that should be written before to
 * {@link OutputStream the underlying output stream} are available, so that they all can be
 * {@link OutputStream#write(Object) written} in the desired order.
 * Useful for processing input streams in several concurrent threads when the order of the resulting
 * outbound messages must reflect the order of the inbound messages.
 * <p>
 * A buffer consists of ordered buckets that implement {@link OutputStream} just as the underlying
 * output stream passed to {@link #OrderedConcurrentOutputBuffer(OutputStream) the constructor}.
 * A user can {@link #addBucket() add a new bucket} at the end of the buffer,
 * {@link OutputStream#write(Object) write messages} to any open bucket and finally
 * {@link OutputStream#close() close a bucket} to indicate that no more messages will be written to
 * it. Each bucket gets flushed automatically to the underlying output stream after all the
 * preceding buckets are closed.<br/>
 * Within each bucket, messages will be written to the output in the order they were buffered.</p>
 * <p>
 * When it is known that no more buckets will be added (usually upon the end of the input stream
 * being processed), {@link #signalNoMoreBuckets()} should be called. After this, when all existing
 * buckets get closed, the underlying output stream will be {@link OutputStream#close() closed}
 * automatically.</p>
 * <p>
 * All bucket methods are thread-safe. Also {@link #signalNoMoreBuckets()} may be called
 * concurrently with any existing bucket methods without an additional synchronization.<br/>
 * {@link #addBucket()} is <b>not</b> thread-safe and its concurrent invocations as well as
 * invocations of {@link #signalNoMoreBuckets()} concurrent with {@link #addBucket()}  must be
 * properly synchronized.<br/>
 * Note that in case of websockets and gRPC, it is usually not an issue as {@code Endpoints} and
 * inbound {@code StreamObservers} are guaranteed to be called by only 1 thread at a time.</p>
 * <p>
 * Note: this class should only be used if the outbound messages order requirement cannot be
 * dropped: if you control a given stream API, then it's more efficient to add some unique id to
 * inbound messages, include it in the corresponding outbound messages and send them as soon as they
 * are produced, so nothing needs to be buffered.</p>
 */
public class OrderedConcurrentOutputBuffer<MessageT> {



	/** Interface for the underlying stream as well as for {@link #addBucket() the added buckets}.*/
	public interface OutputStream<MessageT> {
		void write(MessageT message);
		void close();
	}



	/** The underlying output stream. */
	final OutputStream<MessageT> output;

	/**
	 * A buffer always has a preallocated guard bucket at the tail of its bucket queue. As
	 * {@link #addBucket()} is synchronized on the tail, having a guard prevents
	 * {@link #addBucket()} to be delayed if the last "real" bucket handed out is just being
	 * {@link Bucket#flush() flushed}.
	 */
	Bucket tailGuard;

	/** Switched by {@link #signalNoMoreBuckets()}. */
	boolean noMoreBuckets = false;



	public OrderedConcurrentOutputBuffer(OutputStream<MessageT> outputStream) {
		this.output = outputStream;
		tailGuard = new Bucket();
		tailGuard.buffer = null;  // the first bucket is initially flushed.
	}



	/**
	 * Adds a new empty bucket at the end of this buffer.
	 * <p>
	 * This method is <b>not</b> thread-safe and its concurrent invocations as well as invocations
	 * of {@link #signalNoMoreBuckets()} concurrent with {@code addBucket()} must be properly
	 * synchronized. Note that in case of websockets and gRPC, it is usually not an issue as
	 * {@code Endpoints} and inbound {@code StreamObservers} are guaranteed to be called by only 1
	 * thread at a time.<br/>
	 * This method may nevertheless be called concurrently with any existing bucket methods without
	 * an additional synchronization.</p>
	 * @return bucket placed right after the one returned by the previous call to this method (or
	 *     the first one if this is the first call). All methods of the returned bucket are
	 *     thread-safe.
	 * @throws IllegalStateException if {@link #signalNoMoreBuckets()} has already been called.
	 */
	public OutputStream<MessageT> addBucket() {
		// The below synchronization does not guarantee thread-safety: if 2 threads that call
		// addBucket() synchronize on the same tailGuard, they will branch the queue into 2.
		// Synchronization here is for memory consistency with threads that may be trying to flush
		// the tailGuard at the same time.
		synchronized (tailGuard.lock) {
			if (noMoreBuckets) {
				throw new IllegalStateException("noMoreBuckets already signaled");
			}

			// return the current tailGuard after adding a new one after it
			tailGuard.next = new Bucket();
			final var result = tailGuard;
			tailGuard = tailGuard.next;
			return result;
		}
	}



	/**
	 * A call to this method indicates that no more new buckets will be {@link #addBucket() added}.
	 * This is usually done upon the end of the input stream being processed. As a result, when
	 * all the existing buckets are {@link OutputStream#close() closed}, the underlying output
	 * stream will be closed automatically.
	 * <p>
	 * This method may be may be called concurrently with any existing bucket methods without an
	 * additional synchronization. However invocations concurrent with {@link #addBucket()} must be
	 * properly synchronized.</p>
	 */
	public void signalNoMoreBuckets() {
		synchronized (tailGuard.lock) {
			noMoreBuckets = true;
			// tailGuard has no buffer <=> it's flushed <=> all previous buckets closed & flushed
			if (tailGuard.buffer == null) output.close();
		}
	}



	/**
	 * A list of messages that will have a fixed position relatively to other buckets within
	 * {@link #output the underlying output stream}.<br/>
	 * All methods are thread-safe.
	 * @see #addBucket()
	 */
	class Bucket implements OutputStream<MessageT> {

		/** null <=> {@link #flush() flushed} <=> all previous {@link #closed closed} && flushed */
		List<MessageT> buffer = new LinkedList<>();

		/**
		 * Switched in {@link #close()}.<br/>
		 * (buffer == null && ! closed) <=> this is the current head bucket (the first unclosed one)
		 */
		boolean closed = false;

		/** null <=> this bucket is the current {@link #tailGuard}. */
		Bucket next;

		/** All bucket methods are synchronized on this lock. */
		final Object lock = new Object();



		/**
		 * Appends {@code message} to the end of this bucket. If this is the head bucket (the first
		 * unclosed one), then {@code message} will be written directly to {@link #output the
		 * underlying output stream}. Otherwise it will be buffered in this bucket until all the
		 * previous buckets are {@link #close() closed} (and flushed).
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
		 * Marks this bucket as {@link #closed}. If this bucket is the head bucket (the first
		 * unclosed one), then all the subsequent closed buckets and the first unclosed one are
		 * {@link #flush() flushed}.
		 * <p>
		 * The first unclosed bucket becomes the new head and its messages will be
		 * {@link OutputStream#write(Object) written} directly to
		 * {@link #output the underlying output stream} from now on.</p>
		 * <p>
		 * If all the buckets are closed (and flushed) and {@link #signalNoMoreBuckets()} has
		 * already been called, then {@link #output the underlying output stream} will be
		 * {@link OutputStream#close() closed}.</p>
		 */
		@Override
		public void close() {
			synchronized (lock) {
				if (closed) throw new IllegalStateException(BUCKET_CLOSED_MESSAGE);
				closed = true;
				// if this was the head bucket, then flush the subsequent continuous closed chain
				if (buffer == null) next.flush();
			}
		}



		/**
		 * {@link OutputStream#write(Object) Writes} all messages in this bucket to
		 * {@link #output the underlying output stream}. If this bucket is already
		 * {@link #closed}, then recursively flushes {@link #next the next bucket}.
		 * <p>
		 * If there is no next bucket (meaning this is {@link #tailGuard}) and
		 * {@link #signalNoMoreBuckets()} has already been called, then
		 * {@link #output the underlying output stream} will be {@link OutputStream#close() closed}.
		 * </p>
		 * <p>
		 * Flushing of each bucket is synchronized only on its own {@link #lock}, so any operations
		 * on subsequent buckets performed by other threads are not delayed.</p>
		 */
		private void flush() {
			synchronized (lock) {
				for (var bufferedMessage: buffer) output.write(bufferedMessage);
				buffer = null;
				if (next != null) {
					if (closed) next.flush();
				} else {  // this is the tailGuard, so all "real" buckets are closed && flushed
					if (noMoreBuckets) output.close();
				}
			}
		}
	}



	static final String BUCKET_CLOSED_MESSAGE = "bucket already closed";
}
