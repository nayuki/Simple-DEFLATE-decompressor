/* 
 * Simple DEFLATE decompressor (Java)
 * 
 * Copyright (c) Project Nayuki
 * MIT License. See readme file.
 * https://www.nayuki.io/page/simple-deflate-decompressor
 */

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;


/**
 * A stream of bits that can be read. Bits are packed in little endian within a byte.
 * For example, the byte 0x87 reads as the sequence of bits [1,1,1,0,0,0,0,1].
 */
public interface BitInputStream extends Closeable {
	
	/**
	 * Returns the current bit position, which ascends from 0 to 7 as bits are read.
	 * @return the current bit position, which is between 0 and 7
	 */
	public int getBitPosition();
	
	
	/**
	 * Reads a bit from this stream. Returns 0 or 1 if a bit is available, or -1 if
	 * the end of stream is reached. The end of stream always occurs on a byte boundary.
	 * @return the next bit of 0 or 1, or -1 for the end of stream
	 * @throws IOException if an I/O exception occurred
	 */
	public int readBitMaybe() throws IOException;
	
	
	/**
	 * Reads the specified number of bits from this stream, packing them in little endian as an unsigned integer.
	 * @param numBits the number of bits to read, in the range [0, 31]
	 * @return a number in the range [0, 2<sup>numBits</sup>)
	 * @throws IllegalArgumentException if the number of bits is out of range
	 * @throws IOException if an I/O exception occurred
	 * @throws EOFException if the end of stream is reached
	 */
	public default int readUint(int numBits) throws IOException {
		if (numBits < 0 || numBits > 31)
			throw new IllegalArgumentException();
		int result = 0;
		for (int i = 0; i < numBits; i++) {
			int bit = readBitMaybe();
			if (bit == -1)
				throw new EOFException();
			result |= bit << i;
		}
		return result;
	}
	
	
	/**
	 * Closes this stream and the underlying input stream.
	 * @throws IOException if an I/O exception occurred
	 */
	@Override public void close() throws IOException;
	
}
