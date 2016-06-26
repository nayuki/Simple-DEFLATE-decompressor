import java.io.IOException;
import java.io.OutputStream;


/**
 * A finite circular buffer of bytes, useful as an implicit dictionary for Lempel-Ziv schemes.
 */
final class CircularDictionary {
	
	/*---- Fields ----*/
	
	private byte[] data;
	
	private int index;
	
	private int mask;
	
	
	
	/*---- Constructor ----*/
	
	/**
	 * Constructs a circular dictionary of the specified size, initialized to zeros.
	 * @param size the size, which must be positive
	 */
	public CircularDictionary(int size) {
		if (size < 1)
			throw new IllegalArgumentException("Size must be positive");
		data = new byte[size];
		index = 0;
		
		if (size > 0 && (size & (size - 1)) == 0)  // Test if size is a power of 2
			mask = size - 1;
		else
			mask = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Appends the specified byte to this circular dictionary.
	 * This overwrites the byte value at {@code size} positions ago.
	 * @param b the byte value to append
	 */
	public void append(int b) {
		data[index] = (byte)b;
		if (mask != 0)
			index = (index + 1) & mask;
		else
			index = (index + 1) % data.length;
	}
	
	
	/**
	 * Copies {@code len} bytes starting at {@code dist} bytes ago to
	 * the specified output stream and also back into this buffer itself.
	 * <p>Note that if the length exceeds the distance, then some of the output
	 * data will be a copy of data that was copied earlier in the process.</p>
	 * @param dist the distance to go back, which must be positive but no greater than the buffer's size
	 * @param len the length to copy, which must be non-negative and is allowed to exceed the distance
	 * @param out the output stream to write to
	 * @throws NullPointerException if the output stream is {@code null}
	 * @throws IllegalArgumentException if the length is negative,
	 * distance is not positive, or distance is greater than the buffer size
	 * @throws IOException if an I/O exception occurs
	 */
	public void copy(int dist, int len, OutputStream out) throws IOException {
		if (out == null)
			throw new NullPointerException();
		if (len < 0 || dist < 1 || dist > data.length)
			throw new IllegalArgumentException();
		
		if (mask != 0) {
			int readIndex = (index - dist + data.length) & mask;
			for (int i = 0; i < len; i++) {
				out.write(data[readIndex]);
				data[index] = data[readIndex];
				readIndex = (readIndex + 1) & mask;
				index = (index + 1) & mask;
			}
		} else {
			int readIndex = (index - dist + data.length) % data.length;
			for (int i = 0; i < len; i++) {
				out.write(data[readIndex]);
				data[index] = data[readIndex];
				readIndex = (readIndex + 1) % data.length;
				index = (index + 1) % data.length;
			}
		}
	}
	
}
