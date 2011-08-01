package nayuki.huffmancoding;

import java.io.IOException;
import java.io.OutputStream;


public final class CircularDictionary {
	
	private byte[] data;
	
	private int index;
	
	
	
	public CircularDictionary() {
		data = new byte[32 * 1024];
		index = 0;
	}
	
	
	
	public void append(int b) {
		data[index] = (byte)b;
		index = (index + 1) % data.length;
	}
	
	
	public void copy(int dist, int len, OutputStream out) throws IOException {
		if (len < 0 || dist < 1 || dist > data.length)
			throw new IllegalArgumentException();
		int readIndex = (index - dist + data.length) % data.length;
		for (int i = 0; i < len; i++) {
			out.write(data[readIndex]);
			data[index] = data[readIndex];
			readIndex = (readIndex + 1) % data.length;
			index = (index + 1) % data.length;
		}
	}
	
}
