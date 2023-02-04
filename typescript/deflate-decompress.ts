/* 
 * Simple DEFLATE decompressor (TypeScript)
 * 
 * Copyright (c) Project Nayuki
 * MIT License. See readme file.
 * https://www.nayuki.io/page/simple-deflate-decompressor
 */


namespace deflate {
	
	type byte = number;
	type int = number;
	
	
	/* 
	 * A canonical Huffman code, where the code values for each symbol is
	 * derived from a given sequence of code lengths. This data structure is
	 * immutable. This could be transformed into an explicit Huffman code tree.
	 * 
	 * Example:
	 *   Code lengths (canonical code):
	 *     Symbol A: 1
	 *     Symbol B: 0 (no code)
	 *     Symbol C: 3
	 *     Symbol D: 2
	 *     Symbol E: 3
	 *  
	 *   Generated Huffman codes:
	 *     Symbol A: 0
	 *     Symbol B: (Absent)
	 *     Symbol C: 110
	 *     Symbol D: 10
	 *     Symbol E: 111
	 *  
	 *   Huffman code tree:
	 *       .
	 *      / \
	 *     A   .
	 *        / \
	 *       D   .
	 *          / \
	 *         C   E
	 */
	class CanonicalCode {
		
		// This dictionary maps Huffman codes to symbol values. Each key is the
		// Huffman code padded with a 1 bit at the beginning to disambiguate codes
		// of different lengths (e.g. otherwise we can't distinguish 0b01 from
		// 0b0001). For the example of codeLengths=[1,0,3,2,3], we would have:
		//     0b1_0 -> 0
		//    0b1_10 -> 3
		//   0b1_110 -> 2
		//   0b1_111 -> 4
		private codeBitsToSymbol = new Map<int,int>();
		
		
		// Constructs a canonical Huffman code from the given list of symbol code lengths.
		// Each code length must be non-negative. Code length 0 means no code for the symbol.
		// The collection of code lengths must represent a proper full Huffman code tree.
		// Examples of code lengths that result in correct full Huffman code trees:
		// - [1, 1] (result: A=0, B=1)
		// - [2, 2, 1, 0, 0, 0] (result: A=10, B=11, C=0)
		// - [3, 3, 3, 3, 3, 3, 3, 3] (result: A=000, B=001, C=010, ..., H=111)
		// Examples of code lengths that result in under-full Huffman code trees:
		// - [0, 2, 0] (result: B=00, unused=01, unused=1)
		// - [0, 1, 0, 2] (result: B=0, D=10, unused=11)
		// Examples of code lengths that result in over-full Huffman code trees:
		// - [1, 1, 1] (result: A=0, B=1, C=overflow)
		// - [1, 1, 2, 2, 3, 3, 3, 3] (result: A=0, B=1, C=overflow, ...)
		public constructor(codeLengths: Readonly<Array<int>>) {
			let nextCode: int = 0;
			for (let codeLength = 1; codeLength <= CanonicalCode.MAX_CODE_LENGTH; codeLength++) {
				nextCode <<= 1;
				const startBit: int = 1 << codeLength;
				codeLengths.forEach((cl: int, symbol: int) => {
					if (cl != codeLength)
						return;
					if (nextCode >= startBit)
						throw new RangeError("This canonical code produces an over-full Huffman code tree");
					this.codeBitsToSymbol.set(startBit | nextCode, symbol);
					nextCode++;
				});
			}
			if (nextCode != 1 << CanonicalCode.MAX_CODE_LENGTH)
				throw new RangeError("This canonical code produces an under-full Huffman code tree");
		}
		
		
		// Decodes the next symbol from the given bit input stream based on this
		// canonical code. The returned symbol value is in the range [0, codeLengths.size()).
		public decodeNextSymbol(inp: BitInputStream): int {
			let codeBits: int = 1;
			while (true) {
				codeBits = codeBits << 1 | inp.readUint(1);
				const result: int|undefined = this.codeBitsToSymbol.get(codeBits);
				if (result !== undefined)
					return result;
			}
		}
		
		
		// The maximum Huffman code length allowed in the DEFLATE standard.
		private static readonly MAX_CODE_LENGTH: int = 15;
		
	}
	
	
	
	/* 
	 * Decompresses raw DEFLATE data (without zlib or gzip container) into bytes.
	 */
	export class Decompressor {
		
		// Reads from the given input stream, decompresses the data, and returns a new byte array.
		public static decompress(input: BitInputStream): Uint8Array {
			return new Uint8Array(new Decompressor(input).output);
		}
		
		
		private output: Array<byte> = [];
		
		private dictionary = new ByteHistory(32 * 1024);
		
		
		// Constructor, which immediately performs decompression.
		private constructor(
				private input: BitInputStream) {
			
			// Process the stream of blocks
			let isFinal: boolean;
			do {
				// Read the block header
				isFinal = this.input.readUint(1) != 0;  // bfinal
				const type: int = this.input.readUint(2);  // btype
				
				// Decompress rest of block based on the type
				if (type == 0)
					this.decompressUncompressedBlock();
				else if (type == 1)
					this.decompressHuffmanBlock(Decompressor.FIXED_LITERAL_LENGTH_CODE, Decompressor.FIXED_DISTANCE_CODE);
				else if (type == 2) {
					const [litLenCode, distCode]: [CanonicalCode,CanonicalCode|null] = this.decodeHuffmanCodes();
					this.decompressHuffmanBlock(litLenCode, distCode);
				} else if (type == 3)
					throw new Error("Reserved block type");
				else
					throw new Error("Unreachable value");
			} while (!isFinal);
			
			// Discard bits to align to byte boundary
			while (this.input.getBitPosition() != 0)
				this.input.readUint(1);
		}
		
		
		private static FIXED_LITERAL_LENGTH_CODE: CanonicalCode = Decompressor.makeFixedLiteralLengthCode();
		
		private static makeFixedLiteralLengthCode(): CanonicalCode {
			let codeLens: Array<int> = [];
			for (let i = 0; i < 144; i++) codeLens.push(8);
			for (let i = 0; i < 112; i++) codeLens.push(9);
			for (let i = 0; i <  24; i++) codeLens.push(7);
			for (let i = 0; i <   8; i++) codeLens.push(8);
			return new CanonicalCode(codeLens);
		}
		
		
		private static FIXED_DISTANCE_CODE: CanonicalCode = Decompressor.makeFixedDistanceCode();
		
		private static makeFixedDistanceCode(): CanonicalCode {
			let codeLens: Array<int> = [];
			for (let i = 0; i < 32; i++) codeLens.push(5);
			return new CanonicalCode(codeLens);
		}
		
		
		// Reads from the bit input stream, decodes the Huffman code
		// specifications into code trees, and returns the trees.
		private decodeHuffmanCodes(): [CanonicalCode,CanonicalCode|null] {
			const numLitLenCodes: int = this.input.readUint(5) + 257;  // hlit + 257
			const numDistCodes: int = this.input.readUint(5) + 1;      // hdist + 1
			
			// Read the code length code lengths
			const numCodeLenCodes: int = this.input.readUint(4) + 4;   // hclen + 4
			let codeLenCodeLen: Array<int> = [];  // This array is filled in a strange order
			for (let i = 0; i < 19; i++)
				codeLenCodeLen.push(0);
			codeLenCodeLen[16] = this.input.readUint(3);
			codeLenCodeLen[17] = this.input.readUint(3);
			codeLenCodeLen[18] = this.input.readUint(3);
			codeLenCodeLen[ 0] = this.input.readUint(3);
			for (let i = 0; i < numCodeLenCodes - 4; i++) {
				const j: int = (i % 2 == 0) ? (8 + Math.floor(i / 2)) : (7 - Math.floor(i / 2));
				codeLenCodeLen[j] = this.input.readUint(3);
			}
			
			// Create the code length code
			const codeLenCode = new CanonicalCode(codeLenCodeLen);
			
			// Read the main code lengths and handle runs
			let codeLens: Array<int> = [];
			while (codeLens.length < numLitLenCodes + numDistCodes) {
				const sym: int = codeLenCode.decodeNextSymbol(this.input);
				if (0 <= sym && sym <= 15)
					codeLens.push(sym);
				else if (sym == 16) {
					if (codeLens.length == 0)
						throw new Error("No code length value to copy");
					const runLen: int = this.input.readUint(2) + 3;
					for (let i = 0; i < runLen; i++)
						codeLens.push(codeLens[codeLens.length - 1]);
				} else if (sym == 17) {
					const runLen: int = this.input.readUint(3) + 3;
					for (let i = 0; i < runLen; i++)
						codeLens.push(0);
				} else if (sym == 18) {
					const runLen: int = this.input.readUint(7) + 11;
					for (let i = 0; i < runLen; i++)
						codeLens.push(0);
				} else
					throw new Error("Symbol out of range");
			}
			if (codeLens.length > numLitLenCodes + numDistCodes)
				throw new Error("Run exceeds number of codes");
			
			// Create literal-length code tree
			const litLenCodeLen: Array<int> = codeLens.slice(0, numLitLenCodes);
			const litLenCode = new CanonicalCode(litLenCodeLen);
			
			// Create distance code tree with some extra processing
			let distCodeLen: Array<int> = codeLens.slice(numLitLenCodes);
			let distCode: CanonicalCode|null;
			if (distCodeLen.length == 1 && distCodeLen[0] == 0)
				distCode = null;  // Empty distance code; the block shall be all literal symbols
			else {
				// Get statistics for upcoming logic
				const oneCount: int = distCodeLen.filter(x => x == 1).length;
				const otherPositiveCount: int = distCodeLen.filter(x => x > 1).length;
				
				// Handle the case where only one distance code is defined
				if (oneCount == 1 && otherPositiveCount == 0) {
					while (distCodeLen.length < 32)
						distCodeLen.push(0);
					distCodeLen[31] = 1;
				}
				distCode = new CanonicalCode(distCodeLen);
			}
			return [litLenCode, distCode];
		}
		
		
		// Handles and copies an uncompressed block from the bit input stream.
		private decompressUncompressedBlock(): void {
			// Discard bits to align to byte boundary
			while (this.input.getBitPosition() != 0)
				this.input.readUint(1);
			
			// Read length
			const  len: int = this.input.readUint(16);
			const nlen: int = this.input.readUint(16);
			if ((len ^ 0xFFFF) != nlen)
				throw new Error("Invalid length in uncompressed block");
			
			// Copy bytes
			for (let i = 0; i < len; i++) {
				const b: byte = this.input.readUint(8);  // Byte is aligned
				this.output.push(b);
				this.dictionary.append(b);
			}
		}
		
		
		// Decompresses a Huffman-coded block from the bit input stream based on the given Huffman codes.
		private decompressHuffmanBlock(litLenCode: CanonicalCode, distCode: CanonicalCode|null): void {
			while (true) {
				const sym: int = litLenCode.decodeNextSymbol(this.input);
				if (sym == 256)  // End of block
					break;
				
				else if (sym < 256) {  // Literal byte
					this.output.push(sym);
					this.dictionary.append(sym);
				} else {  // Length and distance for copying
					const run: int = this.decodeRunLength(sym);
					if (!(3 <= run && run <= 258))
						throw new Error("Invalid run length");
					if (distCode === null)
						throw new Error("Length symbol encountered with empty distance code");
					const distSym: int = distCode.decodeNextSymbol(this.input);
					const dist: int = this.decodeDistance(distSym);
					if (!(1 <= dist && dist <= 32768))
						throw new Error("Invalid distance");
					this.dictionary.copy(dist, run, this.output);
				}
			}
		}
		
		
		// Returns the run length based on the given symbol and possibly reading more bits.
		private decodeRunLength(sym: int): int {
			// Symbols outside the range cannot occur in the bit stream;
			// they would indicate that the decompressor is buggy
			if (!(257 <= sym && sym <= 287))
				throw new RangeError("Invalid run length symbol");
			
			if (sym <= 264)
				return sym - 254;
			else if (sym <= 284) {
				const numExtraBits: int = Math.floor((sym - 261) / 4);
				return (((sym - 265) % 4 + 4) << numExtraBits) + 3 + this.input.readUint(numExtraBits);
			} else if (sym == 285)
				return 258;
			else  // sym is 286 or 287
				throw new RangeError("Reserved length symbol");
		}
		
		
		// Returns the distance based on the given symbol and possibly reading more bits.
		private decodeDistance(sym: int): int {
			// Symbols outside the range cannot occur in the bit stream;
			// they would indicate that the decompressor is buggy
			if (!(0 <= sym && sym <= 31))
				throw new RangeError("Invalid distance symbol");
			
			if (sym <= 3)
				return sym + 1;
			else if (sym <= 29) {
				const numExtraBits: int = Math.floor(sym / 2) - 1;
				return ((sym % 2 + 2) << numExtraBits) + 1 + this.input.readUint(numExtraBits);
			} else  // sym is 30 or 31
				throw new RangeError("Reserved distance symbol");
		}
		
	}
	
	
	
	/* 
	 * Stores a finite recent history of a byte stream. Useful as an implicit
	 * dictionary for Lempel-Ziv schemes.
	 */
	class ByteHistory {
		
		// Maximum number of bytes stored in this history.
		private size: int;
		
		// Circular buffer of byte data.
		private data: Array<byte> = [];
		
		// Index of next byte to write to, always in the range [0, data.length).
		private index: int = 0;
		
		
		// Constructs a byte history of the given size.
		public constructor(size: int) {
			if (size < 1)
				throw new RangeError("Size must be positive");
			this.size = size;
		}
		
		
		// Appends the given byte to this history.
		// This overwrites the byte value at `size` positions ago.
		public append(b: byte): void {
			if (this.data.length < this.size)
				this.data.push(0);  // Dummy value
			if (!(0 <= this.index && this.index < this.data.length))
				throw new Error("Unreachable state");
			this.data[this.index] = b;
			this.index = (this.index + 1) % this.size;
		}
		
		
		// Copies `len` bytes starting at `dist` bytes ago to the
		// given output array and also back into this buffer itself.
		// Note that if the count exceeds the distance, then some of the output
		// data will be a copy of data that was copied earlier in the process.
		public copy(dist: int, len: int, out: Array<byte>): void {
			if (len < 0 || !(1 <= dist && dist <= this.data.length))
				throw new RangeError("Invalid length or distance");
			let readIndex: int = (this.index + this.size - dist) % this.size;
			for (let i = 0; i < len; i++) {
				const b: byte = this.data[readIndex];
				readIndex = (readIndex + 1) % this.size;
				out.push(b);
				this.append(b);
			}
		}
		
	}
	
	
	
	/* 
	 * A stream of bits that can be read. Because they come from an underlying byte stream, the
	 * total number of bits is always a multiple of 8. Bits are packed in little endian within
	 * a byte. For example, the byte 0x87 reads as the sequence of bits [1,1,1,0,0,0,0,1].
	 */
	export class BitInputStream {
		
		// In the range [0, data.length*8].
		private bitIndex: int = 0;
		
		
		// Constructs a bit input stream based on the given byte array.
		public constructor(
			private data: Uint8Array) {}
		
		
		// Returns the current bit position, which ascends from 0 to 7 as bits are read.
		public getBitPosition(): int {
			return this.bitIndex % 8;
		}
		
		
		// Reads a bit from this stream. Returns 0 or 1 if a bit is available, or -1 if
		// the end of stream is reached. The end of stream always occurs on a byte boundary.
		public readBitMaybe(): -1|0|1 {
			const byteIndex: int = this.bitIndex >>> 3;
			if (byteIndex >= this.data.length)
				return -1;
			const result = ((this.data[byteIndex] >>> (this.bitIndex & 7)) & 1) as (0|1);
			this.bitIndex++;
			return result;
		}
		
		
		// Reads the given number of bits from this stream,
		// packing them in little endian as an unsigned integer.
		public readUint(numBits: int): int {
			if (numBits < 0 || numBits > 31)
				throw new RangeError("Number of bits out of range");
			let result: int = 0;
			for (let i = 0; i < numBits; i++) {
				const bit: -1|0|1 = this.readBitMaybe();
				if (bit == -1)
					throw new Error("Unexpected end of data");
				result |= bit << i;
			}
			return result;
		}
		
	}
	
}
