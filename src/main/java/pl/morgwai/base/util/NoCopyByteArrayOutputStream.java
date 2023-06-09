// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util;

import java.io.ByteArrayOutputStream;



/**
 * A {@link ByteArrayOutputStream} that allows to directly {@link #getBuffer() access its underlying
 * buffer} after the stream was closed. Forbids further writing and resetting after its closure.
 */
public class NoCopyByteArrayOutputStream extends ByteArrayOutputStream {



	boolean closed = false;



	@Override
	public void close() {
		closed = true;
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
		if ( !closed) throw new IllegalStateException("stream not closed yet");
		return buf;
	}



	/**
	 * Ensures {@link #close()} hasn't been called yet and calls {@code super}.
	 * @throws IllegalStateException if this stream has already been closed.
	 */
	public void write(int b) {
		if (closed) throw new IllegalStateException(STREAM_CLOSED_MESSAGE);
		super.write(b);
	}



	/**
	 * Ensures {@link #close()} hasn't been called yet and calls {@code super}.
	 * @throws IllegalStateException if this stream has already been closed.
	 */
	public void write(byte[] b, int off, int len) {
		if (closed) throw new IllegalStateException(STREAM_CLOSED_MESSAGE);
		super.write(b, off, len);
	}



	/**
	 * Ensures {@link #close()} hasn't been called yet and calls {@code super}.
	 * @throws IllegalStateException if this stream has already been closed.
	 */
	public void reset() {
		if (closed) throw new IllegalStateException(STREAM_CLOSED_MESSAGE);
		super.reset();
	}



	public NoCopyByteArrayOutputStream() {}

	public NoCopyByteArrayOutputStream(int size) {
		super(size);
	}

	static final String STREAM_CLOSED_MESSAGE = "stream already closed";
}
