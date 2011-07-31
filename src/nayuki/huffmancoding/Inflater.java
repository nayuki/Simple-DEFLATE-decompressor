package nayuki.huffmancoding;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;


public final class Inflater {
	
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
	
	
	public static byte[] decompress(BitInputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		while (true) {
			int bfinal = in.readNoEof();
			int btype = in.readNoEof() << 0 | in.readNoEof() << 1;
			
			if (btype == 0)
				decompressUncompressedBlock(in, out);
			else if (btype == 1 || btype == 2) {
				throw new UnsupportedOperationException("Not implemented yet");
					
			} else if (btype == 3)
				throw new RuntimeException("Invalid block type");
			else
				throw new AssertionError();
			
			if (bfinal == 1)
				break;
		}
		
		return out.toByteArray();
	}
	
	
	private static void decompressUncompressedBlock(BitInputStream in, ByteArrayOutputStream out) throws IOException {
		int len = readInt(in, 16);
		int nlen = readInt(in, 16);
		if (~len != nlen)
			throw new RuntimeException("Invalid length in uncompressed block");
		for (int i = 0; i < len; i++) {
			int temp = in.readByte();
			if (temp == -1)
				throw new EOFException();
			out.write(temp);
		}
	}
	
	
	private static int readInt(BitInputStream in, int numBits) throws IOException {
		if (numBits < 0 || numBits >= 32)
			throw new IllegalArgumentException();
		
		int result = 0;
		for (int i = 0; i < numBits; i++)
			result |= in.readNoEof() << i;
		return result;
	}
	
	
	
	private Inflater() {}
	
}
