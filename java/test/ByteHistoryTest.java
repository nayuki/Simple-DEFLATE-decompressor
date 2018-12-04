/* 
 * Simple DEFLATE decompressor
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/simple-deflate-decompressor
 * https://github.com/nayuki/Simple-DEFLATE-decompressor
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;


public final class ByteHistoryTest {
	
	@Test public void testTiny() {
		ByteHistory d = new ByteHistory(1);
		d.append(8);
		checkCopy(d, 1, 8);
		checkCopy(d, 1, 8, 8);
	}
	
	
	@Test public void testSmall() {
		ByteHistory d = new ByteHistory(5);
		d.append(2);
		d.append(7);
		d.append(1);
		d.append(8);
		d.append(3);
		d.append(4);
		checkCopy(d, 3, 8);
		checkCopy(d, 5, 1, 8, 3, 4, 8);
		checkCopy(d, 2, 4, 8, 4, 8, 4, 8, 4);
	}
	
	
	@Test public void testRandomly() {
		for (int i = 0; i < 3000; i++) {
			// Initialize randomly sized circular dictionary and a naive buffer
			int size = rand.nextInt(300) + 1;
			ByteHistory d = new ByteHistory(size);
			int maxCopy = size * 2;  // Arbitrary
			byte[] buf = new byte[30000];
			int index = 0;
			
			// Fill the first 'size' elements of both structures
			for (int j = 0; j < size; j++) {
				byte b = (byte)rand.nextInt(256);
				buf[index] = b;
				index++;
				d.append(b);
			}
			
			// Repeatedly either append a byte or perform a copy operation
			while (index < buf.length) {
				if (rand.nextInt(size) == 0) {  // Probability of 1/size
					if (buf.length - index < maxCopy)
						break;
					int dist = rand.nextInt(size) + 1;
					int len = rand.nextInt(maxCopy);
					int[] expect = new int[len];
					for (int j = 0; j < len; j++) {
						byte b = buf[index - dist];
						buf[index] = b;
						index++;
						expect[j] = b;
					}
					checkCopy(d, dist, expect);
					
				} else {
					byte b = (byte)rand.nextInt(256);
					buf[index] = b;
					index++;
					d.append(b);
				}
			}
		}
	}
	
	
	private static void checkCopy(ByteHistory d, int dist, int... expectBytes) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			d.copy(dist, expectBytes.length, out);
			byte[] actualBytes = out.toByteArray();
			Assert.assertEquals(expectBytes.length, actualBytes.length);
			for (int i = 0; i < expectBytes.length; i++) {
				int b = expectBytes[i];
				if ((b & 0xFF) != b && (byte)b != b)
					throw new IllegalArgumentException();
				Assert.assertEquals(b & 0xFF, actualBytes[i] & 0xFF);
			}
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
	
	
	private static Random rand = new Random();
	
}
