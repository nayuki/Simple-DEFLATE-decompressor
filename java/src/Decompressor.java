/* 
 * Simple DEFLATE decompressor (Java)
 * 
 * Copyright (c) Project Nayuki
 * MIT License. See readme file.
 * https://www.nayuki.io/page/simple-deflate-decompressor
 */

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.DataFormatException;


/**
 * Decompresses raw DEFLATE data (without zlib or gzip container) into bytes.
 */
public final class Decompressor {
	
	/*---- Public functions ----*/
	
	/**
	 * Reads from the specified input stream, decompress the data, and returns a new byte array.
	 * @param in the bit input stream to read from (not {@code null})
	 * @throws NullPointerException if the input stream is {@code null}
	 * @throws DataFormatException if the DEFLATE data is malformed
	 */
	public static byte[] decompress(BitInputStream in) throws IOException, DataFormatException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		decompress(in, out);
		return out.toByteArray();
	}
	
	
	/**
	 * Reads from the specified input stream, decompress the data, and writes to the specified output stream.
	 * @param in the bit input stream to read from (not {@code null})
	 * @param out the byte output stream to write to (not {@code null})
	 * @throws NullPointerException if the input or output stream is {@code null}
	 * @throws DataFormatException if the DEFLATE data is malformed
	 */
	public static void decompress(BitInputStream in, OutputStream out) throws IOException, DataFormatException {
		new Decompressor(in, out);
	}
	
	
	
	/*---- Private implementation ----*/
	
	/*-- Fields --*/
	
	private BitInputStream input;
	
	private OutputStream output;
	
	private ByteHistory dictionary;
	
	
	
	/*-- Constructor --*/
	
	// Constructor, which immediately performs decompression
	private Decompressor(BitInputStream in, OutputStream out) throws IOException, DataFormatException {
		// Initialize fields
		input = Objects.requireNonNull(in);
		output = Objects.requireNonNull(out);
		dictionary = new ByteHistory(32 * 1024);
		
		// Process the stream of blocks
		boolean isFinal;
		do {
			// Read the block header
			isFinal = input.readUint(1) != 0;  // bfinal
			int type = input.readUint(2);  // btype
			
			// Decompress rest of block based on the type
			if (type == 0)
				decompressUncompressedBlock();
			else if (type == 1)
				decompressHuffmanBlock(FIXED_LITERAL_LENGTH_CODE, FIXED_DISTANCE_CODE);
			else if (type == 2) {
				CanonicalCode[] litLenAndDist = decodeHuffmanCodes();
				decompressHuffmanBlock(litLenAndDist[0], litLenAndDist[1]);
			} else if (type == 3)
				throw new DataFormatException("Reserved block type");
			else
				throw new AssertionError("Impossible value");
		} while (!isFinal);
	}
	
	
	/*-- Constants: The code trees for static Huffman codes (btype = 1) --*/
	
	private static final CanonicalCode FIXED_LITERAL_LENGTH_CODE;
	private static final CanonicalCode FIXED_DISTANCE_CODE;
	
	static {  // Make temporary tables of canonical code lengths
		int[] llcodelens = new int[288];
		Arrays.fill(llcodelens,   0, 144, 8);
		Arrays.fill(llcodelens, 144, 256, 9);
		Arrays.fill(llcodelens, 256, 280, 7);
		Arrays.fill(llcodelens, 280, 288, 8);
		FIXED_LITERAL_LENGTH_CODE = new CanonicalCode(llcodelens);
		
		int[] distcodelens = new int[32];
		Arrays.fill(distcodelens, 5);
		FIXED_DISTANCE_CODE = new CanonicalCode(distcodelens);
	}
	
	
	/*-- Method: Reading and decoding dynamic Huffman codes (btype = 2) --*/
	
	// Reads from the bit input stream, decodes the Huffman code
	// specifications into code trees, and returns the trees.
	private CanonicalCode[] decodeHuffmanCodes() throws IOException, DataFormatException {
		int numLitLenCodes = input.readUint(5) + 257;  // hlit + 257
		int numDistCodes = input.readUint(5) + 1;      // hdist + 1
		
		// Read the code length code lengths
		int numCodeLenCodes = input.readUint(4) + 4;   // hclen + 4
		int[] codeLenCodeLen = new int[19];  // This array is filled in a strange order
		codeLenCodeLen[16] = input.readUint(3);
		codeLenCodeLen[17] = input.readUint(3);
		codeLenCodeLen[18] = input.readUint(3);
		codeLenCodeLen[ 0] = input.readUint(3);
		for (int i = 0; i < numCodeLenCodes - 4; i++) {
			int j = (i % 2 == 0) ? (8 + i / 2) : (7 - i / 2);
			codeLenCodeLen[j] = input.readUint(3);
		}
		
		// Create the code length code
		CanonicalCode codeLenCode;
		try {
			codeLenCode = new CanonicalCode(codeLenCodeLen);
		} catch (IllegalArgumentException e) {
			throw new DataFormatException(e.getMessage());
		}
		
		// Read the main code lengths and handle runs
		int[] codeLens = new int[numLitLenCodes + numDistCodes];
		for (int codeLensIndex = 0; codeLensIndex < codeLens.length; ) {
			int sym = codeLenCode.decodeNextSymbol(input);
			if (0 <= sym && sym <= 15) {
				codeLens[codeLensIndex] = sym;
				codeLensIndex++;
			} else {
				int runLen;
				int runVal = 0;
				if (sym == 16) {
					if (codeLensIndex == 0)
						throw new DataFormatException("No code length value to copy");
					runLen = input.readUint(2) + 3;
					runVal = codeLens[codeLensIndex - 1];
				} else if (sym == 17)
					runLen = input.readUint(3) + 3;
				else if (sym == 18)
					runLen = input.readUint(7) + 11;
				else
					throw new AssertionError("Symbol out of range");
				int end = codeLensIndex + runLen;
				if (end > codeLens.length)
					throw new DataFormatException("Run exceeds number of codes");
				Arrays.fill(codeLens, codeLensIndex, end, runVal);
				codeLensIndex = end;
			}
		}
		
		// Create literal-length code tree
		int[] litLenCodeLen = Arrays.copyOf(codeLens, numLitLenCodes);
		CanonicalCode litLenCode;
		try {
			litLenCode = new CanonicalCode(litLenCodeLen);
		} catch (IllegalArgumentException e) {
			throw new DataFormatException(e.getMessage());
		}
		
		// Create distance code tree with some extra processing
		int[] distCodeLen = Arrays.copyOfRange(codeLens, numLitLenCodes, codeLens.length);
		CanonicalCode distCode;
		if (distCodeLen.length == 1 && distCodeLen[0] == 0)
			distCode = null;  // Empty distance code; the block shall be all literal symbols
		else {
			// Get statistics for upcoming logic
			int oneCount = 0;
			int otherPositiveCount = 0;
			for (int x : distCodeLen) {
				if (x == 1)
					oneCount++;
				else if (x > 1)
					otherPositiveCount++;
			}
			
			// Handle the case where only one distance code is defined
			if (oneCount == 1 && otherPositiveCount == 0) {
				// Add a dummy invalid code to make the Huffman tree complete
				distCodeLen = Arrays.copyOf(distCodeLen, 32);
				distCodeLen[31] = 1;
			}
			try {
				distCode = new CanonicalCode(distCodeLen);
			} catch (IllegalArgumentException e) {
				throw new DataFormatException(e.getMessage());
			}
		}
		
		return new CanonicalCode[]{litLenCode, distCode};
	}
	
	
	/*-- Methods: Block decompression --*/
	
	// Handles and copies an uncompressed block from the bit input stream.
	private void decompressUncompressedBlock() throws IOException, DataFormatException {
		// Discard bits to align to byte boundary
		while (input.getBitPosition() != 0)
			input.readUint(1);
		
		// Read length
		int  len = input.readUint(16);
		int nlen = input.readUint(16);
		if ((len ^ 0xFFFF) != nlen)
			throw new DataFormatException("Invalid length in uncompressed block");
		
		// Copy bytes
		for (int i = 0; i < len; i++) {
			int b = input.readUint(8);  // Byte is aligned
			if (b == -1)
				throw new EOFException("Unexpected end of stream");
			output.write(b);
			dictionary.append(b);
		}
	}
	
	
	// Decompresses a Huffman-coded block from the bit input stream based on the given Huffman codes.
	private void decompressHuffmanBlock(CanonicalCode litLenCode, CanonicalCode distCode)
			throws IOException, DataFormatException {
		Objects.requireNonNull(litLenCode);
		// distCode is allowed to be null
		
		while (true) {
			int sym = litLenCode.decodeNextSymbol(input);
			if (sym == 256)  // End of block
				break;
			
			if (sym < 256) {  // Literal byte
				output.write(sym);
				dictionary.append(sym);
			} else {  // Length and distance for copying
				int run = decodeRunLength(sym);
				assert 3 <= run && run <= 258 : "Invalid run length";
				if (distCode == null)
					throw new DataFormatException("Length symbol encountered with empty distance code");
				int distSym = distCode.decodeNextSymbol(input);
				int dist = decodeDistance(distSym);
				assert 1 <= dist && dist <= 32768 : "Invalid distance";
				dictionary.copy(dist, run, output);
			}
		}
	}
	
	
	/*-- Methods: Symbol decoding --*/
	
	// Returns the run length based on the given symbol and possibly reading more bits.
	private int decodeRunLength(int sym) throws IOException, DataFormatException {
		// Symbols outside the range cannot occur in the bit stream;
		// they would indicate that the decompressor is buggy
		assert 257 <= sym && sym <= 287 : "Invalid run length symbol: " + sym;
		
		if (sym <= 264)
			return sym - 254;
		else if (sym <= 284) {
			int numExtraBits = (sym - 261) / 4;
			return (((sym - 265) % 4 + 4) << numExtraBits) + 3 + input.readUint(numExtraBits);
		} else if (sym == 285)
			return 258;
		else  // sym is 286 or 287
			throw new DataFormatException("Reserved length symbol: " + sym);
	}
	
	
	// Returns the distance based on the given symbol and possibly reading more bits.
	private int decodeDistance(int sym) throws IOException, DataFormatException {
		// Symbols outside the range cannot occur in the bit stream;
		// they would indicate that the decompressor is buggy
		assert 0 <= sym && sym <= 31 : "Invalid distance symbol: " + sym;
		
		if (sym <= 3)
			return sym + 1;
		else if (sym <= 29) {
			int numExtraBits = sym / 2 - 1;
			return ((sym % 2 + 2) << numExtraBits) + 1 + input.readUint(numExtraBits);
		} else  // sym is 30 or 31
			throw new DataFormatException("Reserved distance symbol: " + sym);
	}
	
}
