/* 
 * Simple DEFLATE decompressor
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/simple-deflate-decompressor
 * https://github.com/nayuki/Simple-DEFLATE-decompressor
 */


/**
 * A bit input stream based on an ASCII string of 0s and 1s, useful for test vectors.
 * <p>Note that for {@code readByte()}, whole bytes are serialized unintuitively in reverse order.</p>
 */
final class StringBitInputStream implements BitInputStream {
	
	/*---- Fields ----*/
	
	private final String data;
	
	private int index;
	
	
	
	/*---- Constructor ----*/
	
	public StringBitInputStream(String str) {
		if (!str.matches("[01]*"))
			throw new IllegalArgumentException("Invalid string format");
		while (str.length() % 8 != 0)
			str += "0";  // Pad with '0' bits until a byte boundary
		data = str;
		index = 0;
	}
	
	
	
	/*---- Methods ----*/
	
	public int getBitPosition() {
		return index % 8;
	}
	
	
	public int readBitMaybe() {
		if (index >= data.length())
			return -1;
		else {
			int result = data.charAt(index) - '0';
			index++;
			return result;
		}
	}
	
	
	public void close() {
		index = data.length();
	}
	
}
