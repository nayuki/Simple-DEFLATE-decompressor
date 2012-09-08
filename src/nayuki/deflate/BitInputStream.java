package nayuki.deflate;

import java.io.IOException;


public interface BitInputStream {
	
	// Reads a bit from the stream. Returns 0 or 1 if a bit is available, or -1 if the end of stream is reached. The end of stream always occurs on a byte boundary.
	public int read() throws IOException;
	
	
	// Reads a bit from the stream. Returns 0 or 1 if a bit is available, or throws an EOFException if the end of stream is reached.
	public int readNoEof() throws IOException;
	
	
	// Closes this stream and the underlying InputStream.
	public void close() throws IOException;
	
	
	// Returns the current bit position, which is between 0 and 7 inclusive. The number of bits remaining in the current byte is 8 minus this number.
	public int getBitPosition();
	
	
	// Discards the remainder of the current byte and reads the next byte from the stream.
	public int readByte() throws IOException;
	
}
