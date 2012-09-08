package nayuki.deflate;

import static org.junit.Assert.*;

import java.io.EOFException;
import java.io.IOException;

import org.junit.Test;


public final class DecompressorTest {
	
	/* Test cases */
	
	@Test(expected=FormatException.class)
	public void testReservedBlockType() throws IOException {
		// Reserved block type
		test("1 11 00000", "");
	}
	
	
	@Test(expected=EOFException.class)
	public void testEofInBlockType() throws IOException {
		// Partial block type
		test("1 0", "");
	}
	
	
	@Test
	public void testUncompressedEmpty() throws IOException {
		// Uncompressed block len=0: (empty)
		test("1 00 00000   0000000000000000 1111111111111111", "");
	}
	
	
	@Test
	public void testUncompressedThreeBytes() throws IOException {
		// Uncompressed block len=3: 05 14 23
		test("1 00 00000   1100000000000000 0011111111111111   10100000 00101000 11000100", "05 14 23");
	}
	
	
	@Test
	public void testUncompressedTwoBlocks() throws IOException {
		// Uncompressed block len=1: 05
		// Uncompressed block len=2: 14 23
		test("0 00 00000   0100000000000000 1011111111111111   10100000 00101000   1 00 00000   1000000000000000 0111111111111111   11000100", "05 14 23");
	}
	
	
	@Test(expected=EOFException.class)
	public void testUncompressedEofBeforeLength() throws IOException {
		// Uncompressed block (partial padding) (no length)
		test("1 00 000", "");
	}
	
	
	@Test(expected=EOFException.class)
	public void testUncompressedEofInLength() throws IOException {
		// Uncompressed block (partial length)
		test("1 00 00000 0000000000", "");
	}
	
	
	@Test(expected=FormatException.class)
	public void testUncompressedMismatchedLength() throws IOException {
		// Uncompressed block (mismatched len and nlen)
		test("1 00 00000 0010000000010000 1111100100110101", "");
	}
	
	
	@Test(expected=EOFException.class)
	public void testUncompressedBlockNoFinalBlock() throws IOException {
		// Uncompressed block len=0: (empty)
		// No final block
		test("0 00 00000   0000000000000000 1111111111111111", "");
	}
	
	
	@Test
	public void testFixedHuffmanEmpty() throws IOException {
		// Fixed Huffman block: End
		test("1 10 0000000", "");
	}
	
	
	@Test
	public void testFixedHuffmanLiterals() throws IOException {
		// Fixed Huffman block: 00 80 8F 90 C0 FF End
		test("1 10 00110000 10110000 10111111 110010000 111000000 111111111 0000000", "00 80 8F 90 C0 FF");
	}
	
	
	@Test
	public void testFixedHuffmanNonOverlappingRun() throws IOException {
		// Fixed Huffman block: 00 01 02 (3,3) End
		test("1 10 00110000 00110001 00110010 0000001 00010 0000000", "00 01 02 00 01 02");
	}
	
	
	@Test
	public void testFixedHuffmanOverlappingRun0() throws IOException {
		// Fixed Huffman block: 01 (1,4) End
		test("1 10 00110001 0000010 00000 0000000", "01 01 01 01 01");
	}
	
	
	@Test
	public void testFixedHuffmanOverlappingRun1() throws IOException {
		// Fixed Huffman block: 8E 8F (2,5) End
		test("1 10 10111110 10111111 0000011 00001 0000000", "8E 8F 8E 8F 8E 8F 8E");
	}
	
	
	@Test(expected=FormatException.class)
	public void testFixedHuffmanInvalidLengthCode286() throws IOException {
		// Fixed Huffman block: #286
		test("1 10 11000110", "");
	}
	
	
	@Test(expected=FormatException.class)
	public void testFixedHuffmanInvalidLengthCode287() throws IOException {
		// Fixed Huffman block: #287
		test("1 10 11000111", "");
	}
	
	
	@Test(expected=FormatException.class)
	public void testFixedHuffmanInvalidDistanceCode30() throws IOException {
		// Fixed Huffman block: 00 #257 #30
		test("1 10 00110000 0000001 11110", "");
	}
	
	
	@Test(expected=FormatException.class)
	public void testFixedHuffmanInvalidDistanceCode31() throws IOException {
		// Fixed Huffman block: 00 #257 #31
		test("1 10 00110000 0000001 11111", "");
	}
	
	
	@Test
	public void testDynamicHuffmanEmpty() throws IOException {
		// Dynamic Huffman block:
		//   numCodeLen=19
		//     codeLenCodeLen = 0:0, 1:1, 2:0, ..., 15:0, 16:0, 17:0, 18:1
		//   numLitLen=257, numDist=2
		//     litLenCodeLen = 0:1, 1:0, ..., 255:0, 256:1
		//     distCodeLen = 0:1, 1:1
		//   Data: End
		String blockHeader = "1 01";
		String codeCounts = "00000 10000 1111";
		String codeLenCodeLens = "000 000 100 000 000 000 000 000 000 000 000 000 000 000 000 000 000 100 000";
		String codeLens = "0 11111111 10101011 0 0 0";
		String data = "1";
		test(blockHeader + codeCounts + codeLenCodeLens + codeLens + data, "");
	}
	
	
	@Test
	public void testDynamicHuffmanEmptyNoDistanceCode() throws IOException {
		// Dynamic Huffman block:
		//   numCodeLen=18
		//     codeLenCodeLen = 0:2, 1:2, 2:0, ..., 15:0, 16:0, 17:0, 18:1
		//   numLitLen=257, numDist=1
		//     litLenCodeLen = 0:0, ..., 254:0, 255:1, 256:1
		//     distCodeLen = 0:0
		//   Data: End
		String blockHeader = "1 01";
		String codeCounts = "00000 00000 0111";
		String codeLenCodeLens = "000 000 100 010 000 000 000 000 000 000 000 000 000 000 000 000 000 010";
		String codeLens = "01111111 00101011 11 11 10";
		String data = "1";
		test(blockHeader + codeCounts + codeLenCodeLens + codeLens + data, "");
	}
	
	
	@Test(expected=FormatException.class)
	public void testDynamicHuffmanCodeLengthRepeatAtStart() throws IOException {
		// Dynamic Huffman block:
		//   numLitLen=257, numDist=1, numCodeLen=18
		//   codeLenCodeLen = 0:0, 1:1, 2:0, ..., 15:0, 16:1, 17:0, 18:0
		//   Literal/length/distance code lengths: #16+00
		String blockHeader = "1 01";
		String codeCounts = "00000 00000 0111";
		String codeLenCodeLens = "100 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 000 100";
		String codeLens = "1";
		test(blockHeader + codeCounts + codeLenCodeLens + codeLens, "");
	}
	
	
	@Test(expected=FormatException.class)
	public void testDynamicHuffmanTooManyCodeLengthItems() throws IOException {
		// Dynamic Huffman block:
		//   numLitLen=257, numDist=1, numCodeLen=18
		//   codeLenCodeLen = 0:0, 1:1, 2:0, ..., 15:0, 16:0, 17:0, 18:1
		//   Literal/length/distance code lengths: 1 1 #18+1111111 #18+1101100
		String blockHeader = "1 01";
		String codeCounts = "00000 00000 0111";
		String codeLenCodeLens = "000 000 100 000 000 000 000 000 000 000 000 000 000 000 000 000 000 100";
		String codeLens = "0 0 11111111 10011011";
		test(blockHeader + codeCounts + codeLenCodeLens + codeLens, "");
	}
	
	
	
	/* Utility method */
	
	// 'input' is a string of 0's and 1's (with optional spaces) representing the input bit sequence.
	// 'refOutput' is a string of pairs of hexadecimal digits (with optional spaces) representing
	// the expected decompressed output byte sequence.
	private static void test(String input, String refOutput) throws IOException {
		refOutput = refOutput.replace(" ", "");
		if (refOutput.length() % 2 != 0)
			throw new IllegalArgumentException();
		byte[] refOut = new byte[refOutput.length() / 2];
		for (int i = 0; i < refOut.length; i++)
			refOut[i] = (byte)Integer.parseInt(refOutput.substring(i * 2, (i + 1) * 2), 16);
		
		input = input.replace(" ", "");
		BitInputStream in = new StringBitInputStream(input);
		byte[] actualOut = Decompressor.decompress(in);
		assertArrayEquals(refOut, actualOut);
	}
	
}
