/* 
 * Simple DEFLATE decompressor (C++)
 * 
 * Copyright (c) Project Nayuki
 * MIT License. See readme file.
 * https://www.nayuki.io/page/simple-deflate-decompressor
 */

#pragma once

#include <cstddef>
#include <cstdint>
#include <istream>
#include <optional>
#include <ostream>
#include <unordered_map>
#include <utility>
#include <vector>


/**
 * A stream of bits that can be read. Bits are packed in little endian within a byte.
 * For example, the byte 0x87 reads as the sequence of bits [1,1,1,0,0,0,0,1].
 */
class BitInputStream {
	
	/*---- Fields ----*/
	
	// The underlying byte stream to read from.
	private: std::istream &input;
	
	// Either in the range [0x00, 0xFF] if bits are available, or EOF if end of stream is reached.
	private: int currentByte;
	
	// Number of remaining bits in the current byte, always between 0 and 7 (inclusive).
	private: int numBitsRemaining;
	
	
	/*---- Constructor ----*/
	
	// Constructs a bit input stream based on the given byte input stream.
	public: explicit BitInputStream(std::istream &in);
	
	
	/*---- Methods ----*/
	
	public: int getBitPosition() const;
	
	
	// Reads a bit from this stream. Returns 0 or 1 if a bit is available, or -1 if
	// the end of stream is reached. The end of stream always occurs on a byte boundary.
	public: int readBitMaybe();
	
	
	// Reads the given number of bits from this stream,
	// packing them in little endian as an unsigned integer.
	public: int readUint(int numBits);
	
};



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
class CanonicalCode final {
	
	/*---- Field ----*/
	
	// This dictionary maps Huffman codes to symbol values. Each key is the
	// Huffman code padded with a 1 bit at the beginning to disambiguate codes
	// of different lengths (e.g. otherwise we can't distinguish 0b01 from
	// 0b0001). For the example of codeLengths=[1,0,3,2,3], we would have:
	//     0b1_0 -> 0
	//    0b1_10 -> 3
	//   0b1_110 -> 2
	//   0b1_111 -> 4
	private: std::unordered_map<long,int> codeBitsToSymbol;
	
	
	/*---- Constructor ----*/
	
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
	public: explicit CanonicalCode(const std::vector<int> &codeLengths);
	
	
	/*---- Method ----*/
	
	// Decodes the next symbol from the given bit input stream based on this
	// canonical code. The returned symbol value is in the range [0, codeLengths.size()).
	public: int decodeNextSymbol(BitInputStream &in) const;
	
	
	/*---- Constant ----*/
	
	// The maximum Huffman code length allowed in the DEFLATE standard.
	private: static const int MAX_CODE_LENGTH = 15;
	
};



/**
 * Stores a finite recent history of a byte stream. Useful as an implicit
 * dictionary for Lempel-Ziv schemes. Mutable and not thread-safe.
 */
class ByteHistory final {
	
	/*---- Fields ----*/
	
	// Circular buffer of byte data.
	private: std::vector<std::uint8_t> data;
	
	// Index of next byte to write to, always in the range [0, data.size()).
	private: std::size_t index;
	
	// Number of bytes written, saturating at data.size().
	private: std::size_t length;
	
	
	/*---- Constructor ----*/
	
	// Constructs a byte history of the given size, initialized to zeros.
	public: explicit ByteHistory(std::size_t size);
	
	
	/*---- Methods ----*/
	
	// Appends the specified byte to this history.
	// This overwrites the byte value at `size` positions ago.
	public: void append(std::uint8_t b);
	
	
	// Copies `len` bytes starting at `dist` bytes ago to the
	// given output stream and also back into this buffer itself.
	// Note that if the count exceeds the distance, then some of the output
	// data will be a copy of data that was copied earlier in the process.
	public: void copy(long dist, int len, std::ostream &out);
	
};



/**
 * Decompresses raw DEFLATE data (without zlib or gzip container) into bytes.
 */
class Decompressor final {
	
	/*---- Public functions ----*/
	
	/**
	 * Reads from the given input stream, decompresses the data, and returns a new byte array.
	 */
	public: static std::vector<std::uint8_t> decompress(BitInputStream &in);
	
	
	/**
	 * Reads from the given input stream, decompresses the data, and writes to the given output stream.
	 */
	public: static void decompress(BitInputStream &in, std::ostream &out);
	
	
	
	/*---- Private implementation ----*/
	
	/*-- Fields --*/
	
	private: BitInputStream &input;
	
	private: std::ostream &output;
	
	private: ByteHistory dictionary;
	
	
	/*-- Constructor --*/
	
	// Constructor, which immediately performs decompression
	private: explicit Decompressor(BitInputStream &in, std::ostream &out);
	
	
	/*-- Constants: The code trees for static Huffman codes (btype = 1) --*/
	
	private: static const CanonicalCode FIXED_LITERAL_LENGTH_CODE;
	private: static std::vector<int> makeFixedLiteralLengthCode();
	
	private: static const CanonicalCode FIXED_DISTANCE_CODE;
	private: static std::vector<int> makeFixedDistanceCode();
	
	
	/*-- Method: Reading and decoding dynamic Huffman codes (btype = 2) --*/
	
	// Reads from the bit input stream, decodes the Huffman code
	// specifications into code trees, and returns the trees.
	private: std::pair<CanonicalCode,std::optional<CanonicalCode>> decodeHuffmanCodes();
	
	
	/*-- Methods: Block decompression --*/
	
	// Handles and copies an uncompressed block from the bit input stream.
	private: void decompressUncompressedBlock();
	
	// Decompresses a Huffman-coded block from the bit input stream based on the given Huffman codes.
	private: void decompressHuffmanBlock(const CanonicalCode &litLenCode, const std::optional<CanonicalCode> &distCode);
	
	
	/*-- Methods: Symbol decoding --*/
	
	// Returns the run length based on the given symbol and possibly reading more bits.
	private: int decodeRunLength(int sym);
	
	// Returns the distance based on the given symbol and possibly reading more bits.
	private: long decodeDistance(int sym);
	
};
