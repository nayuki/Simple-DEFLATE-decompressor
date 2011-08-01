package p79068.deflate;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

import nayuki.huffmancoding.BitInputStream;
import nayuki.huffmancoding.CanonicalCode;
import nayuki.huffmancoding.CircularDictionary;
import nayuki.huffmancoding.CodeTree;
import nayuki.huffmancoding.InternalNode;
import nayuki.huffmancoding.Leaf;
import nayuki.huffmancoding.Node;


public final class Decompressor {
	
	public static byte[] decompress(BitInputStream in) throws IOException {
		Decompressor decomp = new Decompressor(in);
		return decomp.output.toByteArray();
	}
	
	
	
	
	private static CodeTree fixedLiteralLengthCode;
	private static CodeTree fixedDistanceCode;
	
	static {
		int[] llcodelens = new int[288];
		Arrays.fill(llcodelens,   0, 144, 8);
		Arrays.fill(llcodelens, 144, 256, 9);
		Arrays.fill(llcodelens, 256, 280, 7);
		Arrays.fill(llcodelens, 280, 288, 8);
		fixedLiteralLengthCode = new CanonicalCode(llcodelens).toCodeTree();
		
		int[] distcodelens = new int[32];
		Arrays.fill(distcodelens, 5);
		fixedDistanceCode = new CanonicalCode(distcodelens).toCodeTree();
	}
	
	
	
	private BitInputStream input;
	
	private ByteArrayOutputStream output;
	
	private CircularDictionary dictionary;
	
	
	
	private Decompressor(BitInputStream in) throws IOException {
		input = in;
		output = new ByteArrayOutputStream();
		dictionary = new CircularDictionary();
		
		while (true) {
			int bfinal = in.readNoEof();
			int btype = readInt(2);
			
			if (btype == 0)
				decompressUncompressedBlock();
			else if (btype == 1 || btype == 2) {
				CodeTree litLenCode, distCode;
				if (btype == 1) {
					litLenCode = fixedLiteralLengthCode;
					distCode = fixedDistanceCode;
				} else {
					throw new UnsupportedOperationException("Not implemented yet");
				}
				decompressHuffmanBlock(litLenCode, distCode);
			
			} else if (btype == 3)
				throw new RuntimeException("Invalid block type");
			else
				throw new AssertionError();
			
			if (bfinal == 1)
				break;
		}
	}
	
	
	private void decompressUncompressedBlock() throws IOException {
		while (input.getBitPosition() != 0)
			input.readNoEof();
		int len = readInt(16);
		int nlen = readInt(16);
		if ((~len & 0xFFFF) != nlen)
			throw new RuntimeException("Invalid length in uncompressed block");
		for (int i = 0; i < len; i++) {
			int temp = input.readByte();
			if (temp == -1)
				throw new EOFException();
			output.write(temp);
			dictionary.append(temp);
		}
	}
	
	
	private void decompressHuffmanBlock(CodeTree litLenCode, CodeTree distCode) throws IOException {
		while (true) {
			int sym = decodeSymbol(litLenCode);
			if (sym == 256)  // End of block
				break;
			
			if (sym < 256) {  // Literal
				output.write(sym);
				dictionary.append(sym);
			} else {
				int len = decodeRunLength(sym);
				int distSym = decodeSymbol(distCode);
				int dist = decodeDistance(distSym);
				dictionary.copy(dist, len, output);
			}
		}
	}
	
	
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
	
	
	private int decodeRunLength(int sym) throws IOException {
		if      (257 <= sym && sym <= 264) return ((sym - 257) << 0) +   3 + readInt(0);
		else if (265 <= sym && sym <= 268) return ((sym - 265) << 1) +  11 + readInt(1);
		else if (265 <= sym && sym <= 268) return ((sym - 269) << 2) +  19 + readInt(2);
		else if (265 <= sym && sym <= 268) return ((sym - 273) << 3) +  35 + readInt(3);
		else if (265 <= sym && sym <= 268) return ((sym - 277) << 4) +  67 + readInt(4);
		else if (265 <= sym && sym <= 268) return ((sym - 281) << 5) + 115 + readInt(5);
		else if (sym == 285              ) return ((sym - 285) << 0) + 258 + readInt(0);
		else throw new IllegalArgumentException("Invalid run length symbol: " + sym);
	}
	
	
	private int decodeDistance(int sym) throws IOException {
		if (sym <= 3)
			return sym + 1;
		else if (sym <= 29) {
			int i = sym / 2 - 1;
			return ((sym % 2 + 1) << i) + readInt(i);
		} else throw new IllegalArgumentException("Invalid distance symbol: " + sym);
	}
	
	
	private int readInt(int numBits) throws IOException {
		if (numBits < 0 || numBits >= 32)
			throw new IllegalArgumentException();
		
		int result = 0;
		for (int i = 0; i < numBits; i++)
			result |= input.readNoEof() << i;
		return result;
	}
	
	
	
	private Decompressor() {}
	
}
