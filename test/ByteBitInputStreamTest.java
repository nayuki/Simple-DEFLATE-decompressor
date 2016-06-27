/* 
 * Simple DEFLATE decompressor
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/simple-deflate-decompressor
 * https://github.com/nayuki/Simple-DEFLATE-decompressor
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public final class ByteBitInputStreamTest {
	
	@Test public void testMixedReadBitsAndBytes() throws IOException {
		BitInputStream in = new ByteBitInputStream(new ByteArrayInputStream(new byte[] {
			// Merely a random sequence to prevent accidental repeats
			(byte)0xB7, (byte)0xC5, (byte)0xBD, (byte)0xDA, (byte)0x5B, (byte)0xD0,
			(byte)0x3A, (byte)0xD5, (byte)0x19, (byte)0x3A, (byte)0x41, (byte)0xA6,
		}));
		
		// Read bits of 0th byte
		assertEquals(0, in.getBitPosition());
		assertEquals(1, in.read());
		assertEquals(1, in.getBitPosition());
		assertEquals(1, in.read());
		assertEquals(2, in.getBitPosition());
		assertEquals(1, in.read());
		assertEquals(3, in.getBitPosition());
		assertEquals(0, in.read());
		assertEquals(4, in.getBitPosition());
		assertEquals(1, in.read());
		assertEquals(5, in.getBitPosition());
		assertEquals(1, in.read());
		assertEquals(6, in.getBitPosition());
		assertEquals(0, in.read());
		assertEquals(7, in.getBitPosition());
		assertEquals(1, in.read());
		
		// Read bits of 1st byte
		assertEquals(0, in.getBitPosition());
		assertEquals(1, in.read());
		assertEquals(1, in.getBitPosition());
		assertEquals(0, in.read());
		assertEquals(2, in.getBitPosition());
		assertEquals(1, in.read());
		assertEquals(0, in.read());
		assertEquals(0, in.read());
		assertEquals(5, in.getBitPosition());
		
		// Read 2nd byte
		assertEquals(0xBD, in.readByte());
		
		// Read bits of 3rd byte
		assertEquals(0, in.getBitPosition());
		assertEquals(0, in.read());
		assertEquals(1, in.read());
		assertEquals(0, in.read());
		assertEquals(1, in.read());
		assertEquals(1, in.read());
		assertEquals(0, in.read());
		assertEquals(6, in.getBitPosition());
		assertEquals(1, in.read());
		assertEquals(7, in.getBitPosition());
		assertEquals(1, in.read());
		
		// Read 4th byte
		assertEquals(0x5B, in.readByte());
		
		// Read bits of 5th byte
		assertEquals(0, in.getBitPosition());
		assertEquals(0, in.read());
		assertEquals(1, in.getBitPosition());
		
		// Read 6th byte
		assertEquals(0x3A, in.readByte());
		
		// Read bits of 7th byte
		assertEquals(0, in.getBitPosition());
		assertEquals(1, in.read());
		assertEquals(0, in.read());
		assertEquals(2, in.getBitPosition());
		
		// Read 8th byte
		assertEquals(0x19, in.readByte());
		
		// Read bits of 9th byte
		assertEquals(0, in.getBitPosition());
		assertEquals(0, in.read());
		assertEquals(1, in.read());
		assertEquals(0, in.read());
		assertEquals(1, in.read());
		assertEquals(1, in.read());
		assertEquals(1, in.read());
		assertEquals(0, in.read());
		assertEquals(7, in.getBitPosition());
		
		// Read 10th and 11th bytes
		assertEquals(0x41, in.readByte());
		assertEquals(0xA6, in.readByte());
		assertEquals(0, in.getBitPosition());
	}
	
}
