// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.util.io;

import java.io.IOException;

import org.junit.Test;

import static org.junit.Assert.*;



public class NoCopyByteArrayOutputStreamTest {



	static class VerifyingStream extends NoCopyByteArrayOutputStream {

		byte[] bufferReference;

		VerifyingStream() { super(32); }

		@Override public void close() {
			bufferReference = buf;
			super.close();
		}
	}

	final VerifyingStream stream = new VerifyingStream();



	@Test
	public void testGetBufferThrowsIfStreamUnclosed() {
		try {
			stream.getBuffer();
			fail("IllegalStateException expected");
		} catch (IllegalStateException expected) {}
	}



	@Test
	public void testGetBufferReturnsUnderlyingBufferReference() {
		stream.close();
		assertSame("getBuffer() should return reference to the underlying buffer",
				stream.bufferReference, stream.getBuffer());
	}



	@Test
	public void testWriteByteThrowsIfStreamClosed() {
		stream.close();
		try {
			stream.write(32);
			fail("IllegalStateException expected");
		} catch (IllegalStateException expected) {}
	}



	@Test
	public void testWriteBufferThrowsNPEIfBufferIsNull() throws IOException {
		stream.close();
		try {
			stream.write(null);
			fail("NPE expected");
		} catch (NullPointerException expected) {}
	}



	@Test
	public void testWriteBufferThrowsIfStreamClosed() throws IOException {
		stream.close();
		try {
			stream.write(new byte[5]);
			fail("IllegalStateException expected");
		} catch (IllegalStateException expected) {}
	}



	@Test
	public void testWriteBufferWithOffsetThrowsNPEIfBufferIsNull() {
		stream.close();
		try {
			stream.write(null, 1, 1);
			fail("NPE expected");
		} catch (NullPointerException expected) {}
	}



	@Test
	public void testWriteBufferWithOffsetThrowsIfStreamClosed() {
		stream.close();
		try {
			stream.write(new byte[5], 1, 1);
			fail("IllegalStateException expected");
		} catch (IllegalStateException expected) {}
	}



	@Test
	public void testWriteBytesThrowsNPEIfBytesAreNull() {
		stream.close();
		try {
			stream.writeBytes(null);
			fail("NPE expected");
		} catch (NullPointerException expected) {}
	}



	@Test
	public void testWriteBytesThrowsIfStreamClosed() {
		stream.close();
		try {
			stream.writeBytes(new byte[5]);
			fail("IllegalStateException expected");
		} catch (IllegalStateException expected) {}
	}



	@Test
	public void testResetThrowsIfStreamClosed() {
		stream.close();
		try {
			stream.reset();
			fail("IllegalStateException expected");
		} catch (IllegalStateException expected) {}
	}
}
