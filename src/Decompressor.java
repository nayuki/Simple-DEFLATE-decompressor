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
import java.util.zip.DataFormatException;


/**
 * Decompresses raw DEFLATE data (without zlib or gzip container) into bytes.
 */
public final class Decompressor {
	
	/*---- Public methods ----*/
	
	public static byte[] decompress(BitInputStream in) throws IOException, DataFormatException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new Decompressor(in, out);
		return out.toByteArray();
	}
	
	
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
			// Read block header
			isFinal = in.readNoEof() == 1;  // bfinal
			int type = readInt(2);  // btype
			
			// Decompress rest of block based on the type
			if (type == 0)
				decompressUncompressedBlock();
			else if (type == 1 || type == 2) {
				CodeTree litLenCode, distCode;
				if (type == 1) {
					litLenCode = fixedLiteralLengthCode;
					distCode = fixedDistanceCode;
				} else {
					CodeTree[] temp = decodeHuffmanCodes(in);
					litLenCode = temp[0];
					distCode = temp[1];
				}
				decompressHuffmanBlock(litLenCode, distCode);
				
			} else if (type == 3)
				throw new DataFormatException("Reserved block type");
			else
				throw new AssertionError("Impossible value");
		} while (!isFinal);
	}
	
	
	/* Tables for static Huffman codes (btype = 1) */
	
	private static CodeTree fixedLiteralLengthCode;
	private static CodeTree fixedDistanceCode;
	
	static {
		int[] llcodelens = new int[288];
		Arrays.fill(llcodelens,   0, 144, 8);
		Arrays.fill(llcodelens, 144, 256, 9);
		Arrays.fill(llcodelens, 256, 280, 7);
		Arrays.fill(llcodelens, 280, 288, 8);
		fixedLiteralLengthCode = new CodeTree(llcodelens);
		
		int[] distcodelens = new int[32];
		Arrays.fill(distcodelens, 5);
		fixedDistanceCode = new CodeTree(distcodelens);
	}
	
	
	/* Method for reading and decoding dynamic Huffman codes (btype = 2) */
	
	private CodeTree[] decodeHuffmanCodes(BitInputStream in) throws IOException, DataFormatException {
		int numLitLenCodes = readInt(5) + 257;  // hlit  + 257
		int numDistCodes = readInt(5) + 1;      // hdist +   1
		
		int numCodeLenCodes = readInt(4) + 4;   // hclen +   4
		int[] codeLenCodeLen = new int[19];
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
		CodeTree codeLenCode;
		try {
			codeLenCode = new CodeTree(codeLenCodeLen);
		} catch (IllegalStateException e) {
			throw new DataFormatException(e.getMessage());
		}
		
		int[] codeLens = new int[numLitLenCodes + numDistCodes];
		int runVal = -1;
		int runLen = 0;
		for (int i = 0; i < codeLens.length; i++) {
			if (runLen > 0) {
				codeLens[i] = runVal;
				runLen--;
				
			} else {
				int sym = decodeSymbol(codeLenCode);
				if (sym < 16) {
					codeLens[i] = sym;
					runVal = sym;
				} else {
					if (sym == 16) {
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
						throw new AssertionError();
					i--;
				}
			}
		}
		if (runLen > 0)
			throw new DataFormatException("Run exceeds number of codes");
		
		// Create code trees
		int[] litLenCodeLen = Arrays.copyOf(codeLens, numLitLenCodes);
		CodeTree litLenCode;
		try {
			litLenCode = new CodeTree(litLenCodeLen);
		} catch (IllegalStateException e) {
			throw new DataFormatException(e.getMessage());
		}
		
		int[] distCodeLen = Arrays.copyOfRange(codeLens, numLitLenCodes, codeLens.length);
		CodeTree distCode;
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
				distCode = new CodeTree(distCodeLen);
			} catch (IllegalStateException e) {
				throw new DataFormatException(e.getMessage());
			}
		}
		
		return new CodeTree[]{litLenCode, distCode};
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
	private void decompressHuffmanBlock(CodeTree litLenCode, CodeTree distCode) throws IOException, DataFormatException {
		if (litLenCode == null)
			throw new NullPointerException();
		
		while (true) {
			int sym = decodeSymbol(litLenCode);
			if (sym == 256)  // End of block
				break;
			
			if (sym < 256) {  // Literal byte
				output.write(sym);
				dictionary.append(sym);
			} else {  // Length and distance for copying
				int len = decodeRunLength(sym);
				if (distCode == null)
					throw new DataFormatException("Length symbol encountered with empty distance code");
				int distSym = decodeSymbol(distCode);
				int dist = decodeDistance(distSym);
				dictionary.copy(dist, len, output);
			}
		}
	}
	
	
	/* Symbol decoding methods */
	
	// Decodes the next symbol from the bit input stream based on
	// the given code tree. The returned symbol value is at least 0.
	private int decodeSymbol(CodeTree code) throws IOException {
		InternalNode currentNode = code.root;
		while (true) {
			int temp = input.readNoEof();
			Node nextNode;
			if      (temp == 0) nextNode = currentNode.leftChild;
			else if (temp == 1) nextNode = currentNode.rightChild;
			else throw new AssertionError();
			
			if (nextNode instanceof Leaf)
				return ((Leaf)nextNode).symbol;
			else if (nextNode instanceof InternalNode)
				currentNode = (InternalNode)nextNode;
			else
				throw new AssertionError();
		}
	}
	
	
	// Returns the run length based on the given symbol and possibly reading more bits.
	private int decodeRunLength(int sym) throws IOException, DataFormatException {
		if (sym < 257 || sym > 285)
			throw new DataFormatException("Invalid run length symbol: " + sym);
		else if (sym <= 264)
			return sym - 254;
		else if (sym <= 284) {
			int i = (sym - 261) / 4;  // Number of extra bits to read
			return (((sym - 265) % 4 + 4) << i) + 3 + readInt(i);
		} else  // sym == 285
			return 258;
	}
	
	
	// Returns the distance based on the given symbol and possibly reading more bits.
	private int decodeDistance(int sym) throws IOException, DataFormatException {
		if (sym <= 3)
			return sym + 1;
		else if (sym <= 29) {
			int i = sym / 2 - 1;  // Number of extra bits to read
			return ((sym % 2 + 2) << i) + 1 + readInt(i);
		} else
			throw new DataFormatException("Invalid distance symbol: " + sym);
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
