package p79068.deflate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;

import nayuki.huffmancoding.BitInputStream;

import org.junit.Test;


public final class DecompressorTest {
	
	@Test
	public void testUncompressedEmpty() {
		test("1 00 00000 0000000000000000 1111111111111111", "");
	}
	
	
	
	private static void test(String input, String output) {
		input = input.replace(" ", "");
		output = output.replace(" ", "");
		if (output.length() % 2 != 0)
			throw new IllegalArgumentException();
		
		BitInputStream in = new StringBitInputStream(input);
		byte[] out;
		try {
			out = Decompressor.decompress(in);
		} catch (IOException e) {
			fail("I/O exception");
			return;
		}
		
		assertEquals(output.length() / 2, out.length);
		for (int i = 0; i < out.length; i++)
			assertEquals(Integer.parseInt(output.substring(i * 2, (i + 1) * 2)), out[i] & 0xFF);
	}
	
}
