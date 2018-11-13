/* 
 * Simple DEFLATE decompressor
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/simple-deflate-decompressor
 * https://github.com/nayuki/Simple-DEFLATE-decompressor
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
	
	/*---- Public methods ----*/
	
	// Reads from the given input stream, decompress the data, and returns a new byte array.
	public static byte[] decompress(BitInputStream in) throws IOException, DataFormatException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		decompress(in, out);
		return out.toByteArray();
	}
	
	
	// Reads from the given input stream, decompress the data, and writes to the given output stream.
	public static void decompress(BitInputStream in, OutputStream out) throws IOException, DataFormatException {
		new Decompressor(in, out);
	}
	
	
	
	/*---- Private implementation ----*/
	
	/* Fields */
	
	private BitInputStream input;
	
	private OutputStream output;
	
	private CircularDictionary dictionary;
	
	
	
	// Constructor, which immediately performs decompression
	private Decompressor(BitInputStream in, OutputStream out) throws IOException, DataFormatException {
		// Initialize fields
		input = in;
		output = out;
		dictionary = new CircularDictionary(32 * 1024);
		
		// Process the stream of blocks
		boolean isFinal = false;
		do {
			// Read the block header
			isFinal = in.readNoEof() == 1;  // bfinal
			int type = readInt(2);  // btype
			
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
	
	
	/* Code trees for static Huffman codes (btype = 1) */
	
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
	
	
	/* Method for reading and decoding dynamic Huffman codes (btype = 2) */
	
	// Reads from the bit input stream, decodes the Huffman code
	// specifications into code trees, and returns the trees.
	private CanonicalCode[] decodeHuffmanCodes() throws IOException, DataFormatException {
		int numLitLenCodes = readInt(5) + 257;  // hlit  + 257
		int numDistCodes = readInt(5) + 1;      // hdist + 1
		
		// Read the code length code lengths
		int numCodeLenCodes = readInt(4) + 4;   // hclen + 4
		int[] codeLenCodeLen = new int[19];  // This array is filled in a strange order
		codeLenCodeLen[16] = readInt(3);
		codeLenCodeLen[17] = readInt(3);
		codeLenCodeLen[18] = readInt(3);
		codeLenCodeLen[ 0] = readInt(3);
		for (int i = 0; i < numCodeLenCodes - 4; i++) {
			if (i % 2 == 0)
				codeLenCodeLen[8 + i / 2] = readInt(3);
			else
				codeLenCodeLen[7 - i / 2] = readInt(3);
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
		int runVal = -1;
		int runLen = 0;
		for (int i = 0; i < codeLens.length; ) {
			if (runLen > 0) {
				if (runVal == -1)
					throw new AssertionError("Impossible state");
				codeLens[i] = runVal;
				runLen--;
				i++;
			} else {
				int sym = decodeSymbol(codeLenCode);
				if (0 <= sym && sym <= 15) {
					codeLens[i] = sym;
					runVal = sym;
					i++;
				} else if (sym == 16) {
					if (runVal == -1)
						throw new DataFormatException("No code length value to copy");
					runLen = readInt(2) + 3;
				} else if (sym == 17) {
					runVal = 0;
					runLen = readInt(3) + 3;
				} else if (sym == 18) {
					runVal = 0;
					runLen = readInt(7) + 11;
				} else
					throw new AssertionError("Symbol out of range");
			}
		}
		if (runLen > 0)
			throw new DataFormatException("Run exceeds number of codes");
		
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
	
	
	/* Block decompression methods */
	
	// Handles and copies an uncompressed block from the input bit stream.
	private void decompressUncompressedBlock() throws IOException, DataFormatException {
		// Discard bits to align to byte boundary
		while (input.getBitPosition() != 0)
			input.readNoEof();
		
		// Read length
		int len  = readInt(16);
		int nlen = readInt(16);
		if ((len ^ 0xFFFF) != nlen)
			throw new DataFormatException("Invalid length in uncompressed block");
		
		// Copy bytes
		for (int i = 0; i < len; i++) {
			int temp = input.readByte();
			if (temp == -1)
				throw new EOFException();
			output.write(temp);
			dictionary.append(temp);
		}
	}
	
	
	// Decompresses a Huffman-coded block from the input bit stream based on the given Huffman codes.
	private void decompressHuffmanBlock(CanonicalCode litLenCode, CanonicalCode distCode)
			throws IOException, DataFormatException {
		Objects.requireNonNull(litLenCode);
		// distCode is allowed to be null
		
		while (true) {
			int sym = decodeSymbol(litLenCode);
			if (sym == 256)  // End of block
				break;
			
			if (sym < 256) {  // Literal byte
				output.write(sym);
				dictionary.append(sym);
			} else {  // Length and distance for copying
				int run = decodeRunLength(sym);
				if (run < 3 || run > 258)
					throw new AssertionError("Invalid run length");
				if (distCode == null)
					throw new DataFormatException("Length symbol encountered with empty distance code");
				int distSym = decodeSymbol(distCode);
				int dist = decodeDistance(distSym);
				if (dist < 1 || dist > 32768)
					throw new AssertionError("Invalid distance");
				dictionary.copy(dist, run, output);
			}
		}
	}
	
	
	/* Symbol decoding methods */
	
	// Decodes the next symbol from the bit input stream based on
	// the given code tree. The returned symbol value is at least 0.
	private int decodeSymbol(CanonicalCode code) throws IOException {
		InternalNode currentNode = code.root;
		while (true) {
			int temp = input.readNoEof();
			Node nextNode;
			if      (temp == 0) nextNode = currentNode.leftChild;
			else if (temp == 1) nextNode = currentNode.rightChild;
			else throw new AssertionError("Illegal subclass");
			
			if (nextNode instanceof Leaf)
				return ((Leaf)nextNode).symbol;
			else if (nextNode instanceof InternalNode)
				currentNode = (InternalNode)nextNode;
			else
				throw new AssertionError("Illegal subclass");
		}
	}
	
	
	// Returns the run length based on the given symbol and possibly reading more bits.
	private int decodeRunLength(int sym) throws IOException, DataFormatException {
		if (sym < 257 || sym > 287)  // Cannot occur in the bit stream; indicates the decompressor is buggy
			throw new AssertionError("Invalid run length symbol: " + sym);
		else if (sym <= 264)
			return sym - 254;
		else if (sym <= 284) {
			int numExtraBits = (sym - 261) / 4;
			return (((sym - 265) % 4 + 4) << numExtraBits) + 3 + readInt(numExtraBits);
		} else if (sym == 285)
			return 258;
		else  // sym is 286 or 287
			throw new DataFormatException("Reserved length symbol: " + sym);
	}
	
	
	// Returns the distance based on the given symbol and possibly reading more bits.
	private int decodeDistance(int sym) throws IOException, DataFormatException {
		if (sym < 0 || sym > 31)  // Cannot occur in the bit stream; indicates the decompressor is buggy
			throw new AssertionError("Invalid distance symbol: " + sym);
		if (sym <= 3)
			return sym + 1;
		else if (sym <= 29) {
			int numExtraBits = sym / 2 - 1;
			return ((sym % 2 + 2) << numExtraBits) + 1 + readInt(numExtraBits);
		} else  // sym is 30 or 31
			throw new DataFormatException("Reserved distance symbol: " + sym);
	}
	
	
	/* Utility method */
	
	// Reads the given number of bits from the bit input stream as a single integer, packed in little endian.
	private int readInt(int numBits) throws IOException {
		if (numBits < 0 || numBits >= 32)
			throw new IllegalArgumentException();
		int result = 0;
		for (int i = 0; i < numBits; i++)
			result |= input.readNoEof() << i;
		return result;
	}
	
}
