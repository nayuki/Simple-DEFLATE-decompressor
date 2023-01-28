/* 
 * Simple DEFLATE decompressor (C++)
 * 
 * Copyright (c) Project Nayuki
 * MIT License. See readme file.
 * https://www.nayuki.io/page/simple-deflate-decompressor
 */

#include <bitset>
#include <cstdlib>
#include <exception>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <istream>
#include <string>
#include <vector>
#include "DeflateDecompress.hpp"

using std::uint8_t;
using std::uint16_t;
using std::uint32_t;
using std::string;


class DataInput final {
	
	private: std::istream &input;
	
	
	public: DataInput(std::istream &in) :
		input(in) {}
	
	
	public: uint8_t readUint8() {
		int b = input.get();
		if (b == std::char_traits<char>::eof())
			throw std::runtime_error("Unexpected end of stream");
		return static_cast<uint8_t>(b);
	}
	
	
	public: uint16_t readLittleEndianUint16() {
		uint16_t result = 0;
		for (int i = 0; i < 2; i++)
			result |= static_cast<uint16_t>(readUint8()) << (i * 8);
		return result;
	}
	
	
	public: uint32_t readLittleEndianUint32() {
		uint32_t result = 0;
		for (int i = 0; i < 4; i++)
			result |= static_cast<uint32_t>(readUint8()) << (i * 8);
		return result;
	}
	
	
	public: string readNullTerminatedString() {
		string result;
		while (true) {
			int b = input.get();
			if (b == std::char_traits<char>::eof())
				throw std::runtime_error("Unexpected end of stream");
			else if (b == '\0')
				break;
			else
				result.push_back(static_cast<char>(b));
		}
		return result;
	}
	
};


static uint32_t getCrc32(const std::vector<uint8_t> &data) {
	uint32_t crc = ~UINT32_C(0);
	for (uint8_t b : data) {
		crc ^= b;
		for (int i = 0; i < 8; i++)
			crc = (crc >> 1) ^ ((crc & 1) * UINT32_C(0xEDB88320));
	}
	return ~crc;
}


static string toHex(uint32_t val, int digits) {
	std::ostringstream s;
	s << std::hex << std::setw(digits) << std::setfill('0') << val;
	return s.str();
}


static string submain(int argc, char *argv[]) {
	// Handle command line arguments
	if (argc != 3)
		return string("Usage: ") + argv[0] + " GzipDecompress InputFile.gz OutputFile";
	const char *inFile = argv[1];
	if (!std::filesystem::exists(inFile))
		return string("Input file does not exist: ") + inFile;
	if (std::filesystem::is_directory(inFile))
		return string("Input file is a directory: ") + inFile;
	const char *outFile = argv[2];
	
	try {
		std::vector<uint8_t> decomp;
		uint32_t crc, size;
		
		// Start reading
		{
			std::ifstream in0(inFile);
			DataInput in1(in0);
			
			// Header
			std::bitset<8> flags;
			{
				if (in1.readLittleEndianUint16() != 0x8B1F)
					return "Invalid GZIP magic number";
				int compMeth = in1.readUint8();
				if (compMeth != 8)
					return string("Unsupported compression method: ") + std::to_string(compMeth);
				flags = in1.readUint8();
				
				// Reserved flags
				if (flags[5] || flags[6] || flags[7])
					return "Reserved flags are set";
				
				// Modification time
				uint32_t mtime = in1.readLittleEndianUint32();
				if (mtime != 0)
					std::cout << "Last modified: " << mtime << " (Unix time)" << std::endl;
				else
					std::cout << "Last modified: N/A";
				
				// Extra flags
				std::cout << "Extra flags: ";
				int extraFlags = in1.readUint8();
				switch (extraFlags) {
					case 2:   std::cout << "Maximum compression";  break;
					case 4:   std::cout << "Fastest compression";  break;
					default:  std::cout << "Unknown (" << extraFlags << ")";  break;
				}
				std::cout << std::endl;
				
				// Operating system
				int operatingSystem = in1.readUint8();
				string os;
				switch (operatingSystem) {
					case   0:  os = "FAT";           break;
					case   1:  os = "Amiga";         break;
					case   2:  os = "VMS";           break;
					case   3:  os = "Unix";          break;
					case   4:  os = "VM/CMS";        break;
					case   5:  os = "Atari TOS";     break;
					case   6:  os = "HPFS";          break;
					case   7:  os = "Macintosh";     break;
					case   8:  os = "Z-System";      break;
					case   9:  os = "CP/M";          break;
					case  10:  os = "TOPS-20";       break;
					case  11:  os = "NTFS";          break;
					case  12:  os = "QDOS";          break;
					case  13:  os = "Acorn RISCOS";  break;
					case 255:  os = "Unknown";       break;
					default :  os = string("Really unknown (") + std::to_string(operatingSystem) + ")";  break;
				}
				std::cout << "Operating system: " << os << std::endl;
			}
			
			// Handle assorted flags
			if (flags[0])
				std::cout << "Flag: Text" << std::endl;
			if (flags[2]) {
				std::cout << "Flag: Extra" << std::endl;
				long len = in1.readLittleEndianUint16();
				for (long i = 0; i < len; i++)  // Skip extra data
					in1.readUint8();
			}
			if (flags[3])
				std::cout << "File name: " + in1.readNullTerminatedString() << std::endl;
			if (flags[1])
				std::cout << "Header CRC-16: " << toHex(in1.readLittleEndianUint16(), 4) << std::endl;
			if (flags[4])
				std::cout << "Comment: " + in1.readNullTerminatedString() << std::endl;
			
			// Decompress
			try {
				BitInputStream in2(in0);
				decomp = Decompressor::decompress(in2);
			} catch (std::exception &e) {
				return string("Invalid or corrupt compressed data: ") + e.what();
			}
			
			// Footer
			crc  = in1.readLittleEndianUint32();
			size = in1.readLittleEndianUint32();
		}
		
		// Check decompressed data's length and CRC
		if (size != static_cast<uint32_t>(decomp.size()))
			return string("Size mismatch: expected=") + std::to_string(size) + ", actual=" + std::to_string(decomp.size());
		if (crc != getCrc32(decomp))
			return string("CRC-32 mismatch: expected=") + toHex(crc, 8) + ", actual=" + toHex(getCrc32(decomp), 8);
		
		// Write decompressed data to output file
		std::ofstream out(outFile);
		for (uint8_t b : decomp)
			out.put(b);
		
		// Success, no error message
		return "";
		
	} catch (std::exception &e) {
		return string("I/O exception: ") + e.what();
	}
}


/* 
 * Decompression application for the gzip file format.
 * Usage: GzipDecompress InputFile.gz OutputFile
 * This decompresses a single gzip input file into a single output file. The program also prints
 * some information to standard output, and error messages if the file is invalid/corrupt.
 */
int main(int argc, char *argv[]) {
	string msg = submain(argc, argv);
	if (msg.length() == 0)
		return EXIT_SUCCESS;
	else {
		std::cerr << msg << std::endl;
		return EXIT_FAILURE;
	}
}
