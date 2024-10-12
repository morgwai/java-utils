// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.utils.io;

import java.io.ByteArrayOutputStream;



/**
 * {@link ByteArrayOutputStream} that allows to directly {@link #getBuffer() access its underlying
 * buffer} after the stream was closed.
 * Forbids further writing and resetting after its closure.
 */
public class NoCopyByteArrayOutputStream extends ByteArrayOutputStream {



	/**
	 * Constructs a new stream.
	 * @param initialBufferSize initial size of the underlying buffer. This should be a reasonably
	 * accurate estimate of the size of the whole data that will be written. If this value is
	 * heavily underestimated, it will have significant performance consequences due to repeated
	 * buffer extending.
	 */
	public NoCopyByteArrayOutputStream(int initialBufferSize) {
		super(initialBufferSize);
	}



	/** Reference to the underlying buffer initialized in {@link #close()}. */
	byte[] closedBuffer = null;



	/**
	 * Marks this stream as closed. After this method is called no further writes are allowed and
	 * access to {@link #getBuffer() the underlying buffer} is granted.
	 */
	@Override
	public void close() {
		closedBuffer = buf;
		buf = null;
	}



	/**
	 * Returns the whole underlying output buffer without copying it. This method may only be used
	 * after the stream was closed. Note that the buffer may be greater than the number of bytes,
	 * that were actually written to this stream: use {@link #size()} to determine the size of the
	 * data rather than the length of the returned array.
	 * @return the whole underlying output buffer.
	 * @throws IllegalStateException if this stream has not been closed yet.
	 */
	public byte[] getBuffer() {
		if (closedBuffer == null) throw new IllegalStateException("stream not closed yet");
		return closedBuffer;
	}



	/**
	 * Ensures {@link #close()} hasn't been called yet and calls {@code super}.
	 * @throws IllegalStateException if this stream has already been closed.
	 */
	public void write(int b) {
		try {
			super.write(b);
		} catch (NullPointerException e) {
			throw new IllegalStateException(STREAM_CLOSED_MESSAGE);
		}
	}



	/**
	 * Ensures {@link #close()} hasn't been called yet and calls {@code super}.
	 * @throws IllegalStateException if this stream has already been closed.
	 */
	public void write(byte[] bytes, int offset, int len) {
		try {
			super.write(bytes, offset, len);
		} catch (NullPointerException e) {
			if (bytes == null) throw e;
			throw new IllegalStateException(STREAM_CLOSED_MESSAGE);
		}
	}



	/**
	 * Ensures {@link #close()} hasn't been called yet and calls {@code super}.
	 * @throws IllegalStateException if this stream has already been closed.
	 */
	public void reset() {
		if (buf == null) throw new IllegalStateException(STREAM_CLOSED_MESSAGE);
		super.reset();
	}



	static final String STREAM_CLOSED_MESSAGE = "stream already closed";
}
