/* 
 * Simple DEFLATE decompressor (C++)
 * 
 * Copyright (c) Project Nayuki
 * MIT License. See readme file.
 * https://www.nayuki.io/page/simple-deflate-decompressor
 */

#include <cassert>
#include <climits>
#include <sstream>
#include <stdexcept>
#include <string>
#include "DeflateDecompress.hpp"

using std::uint8_t;
using std::size_t;
using std::vector;
using std::domain_error;
using std::logic_error;
using std::runtime_error;


/*---- BitInputStream class ----*/

BitInputStream::BitInputStream(std::istream &in) :
	input(in),
	currentByte(0),
	numBitsRemaining(0) {}


int BitInputStream::getBitPosition() const {
	if (numBitsRemaining < 0 || numBitsRemaining > 7)
		throw logic_error("Unreachable state");
	return (8 - numBitsRemaining) % 8;
}


int BitInputStream::readBitMaybe() {
	if (currentByte == std::char_traits<char>::eof())
		return -1;
	if (numBitsRemaining == 0) {
		currentByte = input.get();  // Note: istream.get() returns int, not char
		if (currentByte == std::char_traits<char>::eof())
			return -1;
		if (currentByte < 0 || currentByte > 255)
			throw logic_error("Unreachable value");
		numBitsRemaining = 8;
	}
	if (numBitsRemaining <= 0)
		throw logic_error("Unreachable state");
	numBitsRemaining--;
	return (currentByte >> (7 - numBitsRemaining)) & 1;
}


int BitInputStream::readUint(int numBits) {
	if (numBits < 0 || numBits > 15)
		throw domain_error("Number of bits out of range");
	int result = 0;
	for (int i = 0; i < numBits; i++) {
		int bit = readBitMaybe();
		if (bit == -1)
			throw runtime_error("Unexpected end of stream");
		result |= bit << i;
	}
	return result;
}



/*---- CanonicalCode class ----*/

CanonicalCode::CanonicalCode(const vector<int> &codeLengths) {
	// Check argument values
	if (codeLengths.size() > INT_MAX)
		throw domain_error("Too many symbols");
	for (int x : codeLengths) {
		if (x < 0)
			throw domain_error("Negative code length");
		if (x > MAX_CODE_LENGTH)
			throw domain_error("Maximum code length exceeded");
	}
	
	// Allocate code values to symbols. Symbols are processed in the order
	// of shortest code length first, breaking ties by lowest symbol value.
	long nextCode = 0;
	for (int codeLength = 1; codeLength <= MAX_CODE_LENGTH; codeLength++) {
		nextCode <<= 1;
		long startBit = 1L << codeLength;
		for (int symbol = 0; symbol < static_cast<int>(codeLengths.size()); symbol++) {
			if (codeLengths[symbol] != codeLength)
				continue;
			if (nextCode >= startBit)
				throw domain_error("This canonical code produces an over-full Huffman code tree");
			codeBitsToSymbol[startBit | nextCode] = symbol;
			nextCode++;
		}
	}
	if (nextCode != 1L << MAX_CODE_LENGTH)
		throw domain_error("This canonical code produces an under-full Huffman code tree");
}


int CanonicalCode::decodeNextSymbol(BitInputStream &in) const {
	long codeBits = 1;  // The start bit
	while (true) {
		// Accumulate one bit at a time on the right side until a match is found
		// in the symbolCodeBits array. Because the Huffman code tree is full,
		// this loop must terminate after at most MAX_CODE_LENGTH iterations.
		codeBits = (codeBits << 1) | in.readUint(1);
		auto it = codeBitsToSymbol.find(codeBits);
		if (it != codeBitsToSymbol.end())
			return it->second;
	}
}



/*---- ByteHistory class ----*/

ByteHistory::ByteHistory(size_t sz) :
		size(sz),
		index(0) {
	if (sz < 1)
		throw domain_error("Size must be positive");
}


void ByteHistory::append(uint8_t b) {
	if (data.size() < size)
		data.push_back(0);  // Dummy value
	assert(index < data.size());
	data[index] = b;
	index = (index + 1U) % size;
}


void ByteHistory::copy(long dist, int len, std::ostream &out) {
	if (len < 0 || dist < 1 || static_cast<unsigned long>(dist) > data.size())
		throw domain_error("Invalid length or distance");
	
	size_t readIndex = (0U + size - dist + index) % size;
	for (int i = 0; i < len; i++) {
		uint8_t b = data[readIndex];
		readIndex = (readIndex + 1U) % size;
		out.put(static_cast<char>(b));
		append(b);
	}
}



/*---- Decompressor class ----*/

vector<uint8_t> Decompressor::decompress(BitInputStream &in) {
	std::stringstream ss;
	decompress(in, ss);
	vector<uint8_t> result;
	while (true) {
		int b = ss.get();
		if (b == std::char_traits<char>::eof())
			break;
		result.push_back(static_cast<uint8_t>(b));
	}
	return result;
}


void Decompressor::decompress(BitInputStream &in, std::ostream &out) {
	Decompressor(in, out);
}


Decompressor::Decompressor(BitInputStream &in, std::ostream &out) :
		// Initialize fields
		input(in),
		output(out),
		dictionary(32U * 1024) {
	
	// Process the stream of blocks
	bool isFinal;
	do {
		// Read the block header
		isFinal = in.readUint(1) != 0;  // bfinal
		int type = input.readUint(2);  // btype
		
		// Decompress rest of block based on the type
		if (type == 0)
			decompressUncompressedBlock();
		else if (type == 1)
			decompressHuffmanBlock(FIXED_LITERAL_LENGTH_CODE, FIXED_DISTANCE_CODE);
		else if (type == 2) {
			std::pair<CanonicalCode,std::optional<CanonicalCode>> litLenAndDist = decodeHuffmanCodes();
			decompressHuffmanBlock(litLenAndDist.first, litLenAndDist.second);
		} else if (type == 3)
			throw domain_error("Reserved block type");
		else
			throw logic_error("Unreachable value");
	} while (!isFinal);
}


const CanonicalCode Decompressor::FIXED_LITERAL_LENGTH_CODE(makeFixedLiteralLengthCode());

vector<int> Decompressor::makeFixedLiteralLengthCode() {
	vector<int> result;
	int i = 0;
	for (; i < 144; i++) result.push_back(8);
	for (; i < 256; i++) result.push_back(9);
	for (; i < 280; i++) result.push_back(7);
	for (; i < 288; i++) result.push_back(8);
	return result;
}


const CanonicalCode Decompressor::FIXED_DISTANCE_CODE(makeFixedDistanceCode());

vector<int> Decompressor::makeFixedDistanceCode() {
	return vector<int>(32, 5);
}


std::pair<CanonicalCode,std::optional<CanonicalCode>> Decompressor::decodeHuffmanCodes() {
	int numLitLenCodes = input.readUint(5) + 257;  // hlit + 257
	int numDistCodes = input.readUint(5) + 1;      // hdist + 1
	
	// Read the code length code lengths
	int numCodeLenCodes = input.readUint(4) + 4;   // hclen + 4
	vector<int> codeLenCodeLen(19, 0);  // This array is filled in a strange order
	codeLenCodeLen[16] = input.readUint(3);
	codeLenCodeLen[17] = input.readUint(3);
	codeLenCodeLen[18] = input.readUint(3);
	codeLenCodeLen[ 0] = input.readUint(3);
	for (int i = 0; i < numCodeLenCodes - 4; i++) {
		int j = (i % 2 == 0) ? (8 + i / 2) : (7 - i / 2);
		codeLenCodeLen[j] = input.readUint(3);
	}
	
	// Create the code length code
	CanonicalCode codeLenCode(codeLenCodeLen);
	
	// Read the main code lengths and handle runs
	vector<int> codeLens;
	while (codeLens.size() < static_cast<unsigned int>(numLitLenCodes + numDistCodes)) {
		int sym = codeLenCode.decodeNextSymbol(input);
		if (0 <= sym && sym <= 15)
			codeLens.push_back(sym);
		else {
			int runLen;
			int runVal = 0;
			if (sym == 16) {
				if (codeLens.empty())
					throw domain_error("No code length value to copy");
				runLen = input.readUint(2) + 3;
				runVal = codeLens.back();
			} else if (sym == 17)
				runLen = input.readUint(3) + 3;
			else if (sym == 18)
				runLen = input.readUint(7) + 11;
			else
				throw logic_error("Symbol out of range");
			for (int i = 0; i < runLen; i++)
				codeLens.push_back(runVal);
		}
	}
	if (codeLens.size() > static_cast<unsigned int>(numLitLenCodes + numDistCodes))
		throw domain_error("Run exceeds number of codes");
	
	// Create literal-length code tree
	CanonicalCode litLenCode(vector(codeLens.begin(), codeLens.begin() + numLitLenCodes));
	
	// Create distance code tree with some extra processing
	vector<int> distCodeLen(codeLens.begin() + numLitLenCodes, codeLens.end());
	std::optional<CanonicalCode> distCode;
	if (distCodeLen.size() == 1 && distCodeLen[0] == 0)
		distCode = std::nullopt;  // Empty distance code; the block shall be all literal symbols
	else {
		// Get statistics for upcoming logic
		size_t oneCount = 0;
		size_t otherPositiveCount = 0;
		for (int x : distCodeLen) {
			if (x == 1)
				oneCount++;
			else if (x > 1)
				otherPositiveCount++;
		}
		
		// Handle the case where only one distance code is defined
		if (oneCount == 1 && otherPositiveCount == 0) {
			// Add a dummy invalid code to make the Huffman tree complete
			while (distCodeLen.size() < 32)
				distCodeLen.push_back(0);
			distCodeLen[31] = 1;
		}
		distCode = std::optional<CanonicalCode>(distCodeLen);
	}
	
	return std::pair<CanonicalCode,std::optional<CanonicalCode>>(
		std::move(litLenCode), std::move(distCode));
}


void Decompressor::decompressUncompressedBlock() {
	// Discard bits to align to byte boundary
	while (input.getBitPosition() != 0)
		input.readUint(1);
	
	// Read length
	long  len = static_cast<long>(input.readUint(8)) << 8;   len |= input.readUint(8);
	long nlen = static_cast<long>(input.readUint(8)) << 8;  nlen |= input.readUint(8);
	if ((len ^ 0xFFFF) != nlen)
		throw domain_error("Invalid length in uncompressed block");
	
	// Copy bytes
	for (long i = 0; i < len; i++) {
		int b = input.readUint(8);  // Byte is aligned
		output.put(static_cast<char>(b));
		dictionary.append(b);
	}
}


void Decompressor::decompressHuffmanBlock(
		const CanonicalCode &litLenCode, const std::optional<CanonicalCode> &distCode) {
	
	while (true) {
		int sym = litLenCode.decodeNextSymbol(input);
		if (sym == 256)  // End of block
			break;
		
		if (sym < 256) {  // Literal byte
			uint8_t b = static_cast<uint8_t>(sym);
			output.put(static_cast<char>(b));
			dictionary.append(b);
		} else {  // Length and distance for copying
			int run = decodeRunLength(sym);
			if (run < 3 || run > 258)
				throw logic_error("Invalid run length");
			if (!distCode.has_value())
				throw domain_error("Length symbol encountered with empty distance code");
			int distSym = distCode->decodeNextSymbol(input);
			long dist = decodeDistance(distSym);
			if (dist < 1 || dist > 32768)
				throw logic_error("Invalid distance");
			dictionary.copy(dist, run, output);
		}
	}
}


int Decompressor::decodeRunLength(int sym) {
	// Symbols outside the range cannot occur in the bit stream;
	// they would indicate that the decompressor is buggy
	assert(257 <= sym && sym <= 287);
	
	if (sym <= 264)
		return sym - 254;
	else if (sym <= 284) {
		int numExtraBits = (sym - 261) / 4;
		return (((sym - 265) % 4 + 4) << numExtraBits) + 3 + input.readUint(numExtraBits);
	} else if (sym == 285)
		return 258;
	else  // sym is 286 or 287
		throw domain_error("Reserved length symbol");
}


long Decompressor::decodeDistance(int sym) {
	// Symbols outside the range cannot occur in the bit stream;
	// they would indicate that the decompressor is buggy
	assert(0 <= sym && sym <= 31);
	
	if (sym <= 3)
		return sym + 1;
	else if (sym <= 29) {
		int numExtraBits = sym / 2 - 1;
		return ((sym % 2 + 2L) << numExtraBits) + 1 + input.readUint(numExtraBits);
	} else  // sym is 30 or 31
		throw domain_error("Reserved distance symbol");
}
