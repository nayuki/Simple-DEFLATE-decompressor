package nayuki.deflate;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;


/**
 * A stream of bits that can be read.
 */
public final class ByteBitInputStream implements BitInputStream {
	
	private InputStream input;  // Underlying byte stream to read from
	
	private int nextBits;  // Either in the range 0x00 to 0xFF, or -1 if the end of stream is reached
	
	private int bitPosition;  // Always between 1 and 8, inclusive
	
	private boolean isEndOfStream;
	
	
	
	public ByteBitInputStream(InputStream in) {
		if (in == null)
			throw new NullPointerException("Argument is null");
		input = in;
		bitPosition = 8;
		isEndOfStream = false;
	}
	
	
	
	// Reads a bit from the stream. Returns 0 or 1 if a bit is available, or -1 if the end of stream is reached. The end of stream always occurs on a byte boundary.
	public int read() throws IOException {
		if (isEndOfStream)
			return -1;
		if (bitPosition == 8) {
			nextBits = input.read();
			if (nextBits == -1) {
				isEndOfStream = true;
				return -1;
			}
			bitPosition = 0;
		}
		int result = (nextBits >>> bitPosition) & 1;
		bitPosition++;
		return result;
	}
	
	
	// Reads a bit from the stream. Returns 0 or 1 if a bit is available, or throws an EOFException if the end of stream is reached.
	public int readNoEof() throws IOException {
		int result = read();
		if (result != -1)
			return result;
		else
			throw new EOFException("End of stream reached");
	}
	
	
	// Returns the current bit position, which is between 0 and 7 inclusive. The number of bits remaining in the current byte is 8 minus this number.
	public int getBitPosition() {
		return bitPosition % 8;
	}
	
	
	// Discards the remainder of the current byte and reads the next byte from the stream.
	public int readByte() throws IOException {
		bitPosition = 8;
		return input.read();
	}
	
	
	// Closes this stream and the underlying InputStream.
	public void close() throws IOException {
		input.close();
	}
	
}
