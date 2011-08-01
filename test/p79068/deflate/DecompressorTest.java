package p79068.deflate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;

import nayuki.huffmancoding.BitInputStream;

import org.junit.Test;


public final class DecompressorTest {
	
	@Test
	public void testUncompressedEmpty() {
		// Uncompressed block len=0: (empty)
		test("1 00 00000   0000000000000000 1111111111111111", "");
	}
	
	
	@Test
	public void testUncompressedThreeBytes() {
		// Uncompressed block len=3: 05 14 23
		test("1 00 00000   1100000000000000 0011111111111111   10100000 00101000 11000100", "05 14 23");
	}
	
	
	@Test
	public void testUncompressedTwoBlocks() {
		// Uncompressed block len=1: 05
		// Uncompressed block len=2: 14 23
		test("0 00 00000   0100000000000000 1011111111111111   10100000 00101000   1 00 00000   1000000000000000 0111111111111111   11000100", "05 14 23");
	}
	
	
	@Test
	public void testFixedHuffmanEmpty() {
		// Fixed Huffman block: End
		test("1 10 0000000", "");
	}
	
	
	@Test
	public void testFixedHuffmanLiterals() {
		// Fixed Huffman block: 00 80 8F 90 C0 FF End
		test("1 10 00110000 10110000 10111111 110010000 111000000 111111111 0000000", "00 80 8F 90 C0 FF");
	}
	
	
	@Test
	public void testFixedHuffmanNonOverlappingRun() {
		// Fixed Huffman block: 00 01 02 (3,3) End
		test("1 10 00110000 00110001 00110010 0000001 00010 0000000", "00 01 02 00 01 02");
	}
	
	
	@Test
	public void testFixedHuffmanOverlappingRun0() {
		// Fixed Huffman block: 01 (1,4) End
		test("1 10 00110001 0000010 00000 0000000", "01 01 01 01 01");
	}
	
	
	@Test
	public void testFixedHuffmanOverlappingRun1() {
		// Fixed Huffman block: 8E 8F (2,5) End
		test("1 10 10111110 10111111 0000011 00001 0000000", "8E 8F 8E 8F 8E 8F 8E");
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
			assertEquals(Integer.parseInt(output.substring(i * 2, (i + 1) * 2), 16), out[i] & 0xFF);
	}
	
}
