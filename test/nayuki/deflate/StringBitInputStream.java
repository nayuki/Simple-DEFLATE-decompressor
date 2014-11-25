package nayuki.deflate;

import java.io.EOFException;
import java.io.IOException;


final class StringBitInputStream implements BitInputStream {
	
	private final String data;
	
	private int index;
	
	
	
	public StringBitInputStream(String str) {
		if (!str.matches("[01]*"))
			throw new IllegalArgumentException();
		data = str;
		index = 0;
	}
	
	
	
	public int read() {
		if (index >= data.length())
			return -1;
		else {
			int result = data.charAt(index) - '0';
			index++;
			return result;
		}
	}
	
	
	public int readNoEof() throws IOException {
		int result = read();
		if (result != -1)
			return result;
		else
			throw new EOFException("End of stream reached");
	}
	
	
	public void close() {
		index = data.length();
	}
	
	
	public int getBitPosition() {
		return index % 8;
	}
	
	
	public int readByte() throws IOException {
		index = (index + 7) / 8 * 8;
		if (data.length() - index < 8)
			return -1;
		
		int result = 0;
		for (int i = 0; i < 8; i++)
			result |= readNoEof() << i;
		return result;
	}
	
}
