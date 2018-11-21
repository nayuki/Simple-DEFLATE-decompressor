# 
# Simple DEFLATE decompressor
# Copyright (c) Project Nayuki
# 
# https://www.nayuki.io/page/simple-deflate-decompressor
# https://github.com/nayuki/Simple-DEFLATE-decompressor
# 

import datetime, os, sys, zlib
import deflatedecompress
python3 = sys.version_info.major >= 3


def main(argv):
	# Handle command line arguments
	if len(argv) != 3:
		return "Usage: python {} InputFile.gz OutputFile".format(argv[0])
	infile = argv[1]
	if not os.path.exists(infile):
		return "Input file does not exist: " + infile
	if os.path.isdir(infile):
		return "Input file is a directory: " + infile
	outfile = argv[2]
	
	try:
		# Start reading
		with open(infile, "rb") as inp:
			
			# Define helper read functions based on 'inp'
			
			def read_byte():
				b = inp.read(1)
				if len(b) == 0:
					raise EOFError()
				return b[0] if python3 else ord(b)
			
			def read_little_int16():
				temp = read_byte()
				return temp | read_byte() << 8
			
			def read_little_int32():
				temp = read_little_int16()
				return temp | read_little_int16() << 16
			
			def read_null_terminated_string():
				temp = b""
				while True:
					b = read_byte()
					if b == 0:
						break
					temp += bytes((b,)) if python3 else chr(b)
				return temp.decode("UTF-8")
			
			
			# Header
			if read_byte() != 0x1F:
				return "Invalid GZIP magic number"
			if read_byte() != 0x8B:
				return "Invalid GZIP magic number"
			compmeth = read_byte()
			if compmeth != 8:
				return "Unsupported compression method: " + str(compmeth)
			flags = read_byte()
			
			# Reserved flags
			if flags & 0xE0 != 0:
				return "Reserved flags are set"
			
			# Modification time
			mtime = read_little_int32()
			if mtime != 0:
				dt = datetime.datetime(1970, 1, 1) + datetime.timedelta(seconds=mtime)
				print("Last modified: " + str(dt))
			else:
				print("Last modified: N/A")
			
			# Extra flags
			extraflags = read_byte()
			if extraflags == 2:
				print("Extra flags: Maximum compression")
			elif extraflags == 4:
				print("Extra flags: Fastest compression")
			else:
				print("Extra flags: Unknown ({})".format(extraflags))
			
			# Operating system
			OPERATING_SYSTEMS = {
				  0: "FAT",
				  1: "Amiga",
				  2: "VMS",
				  3: "Unix",
				  4: "VM/CMS",
				  5: "Atari TOS",
				  6: "HPFS",
				  7: "Macintosh",
				  8: "Z-System",
				  9: "CP/M",
				 10: "TOPS-20",
				 11: "NTFS",
				 12: "QDOS",
				 13: "Acorn RISCOS",
				255: "Unknown",
			}
			osbyte = read_byte()
			osstr = OPERATING_SYSTEMS.get(osbyte, "Really unknown")
			print("Operating system: " + osstr)
			
			# Handle assorted flags
			if flags & 0x01 != 0:
				print("Flag: Text")
			if flags & 0x04 != 0:
				print("Flag: Extra")
				count = read_little_int16()
				while count > 0:  # Skip extra data
					n = inp.read(count)
					if n == 0:
						raise EOFError()
					count -= n
			if flags & 0x08 != 0:
				print("File name: " + read_null_terminated_string())
			if flags & 0x02 != 0:
				print("Header CRC-16: {:04X}".format(read_little_int16()))
			if flags & 0x10 != 0:
				print("Comment: " + read_null_terminated_string())
			
			# Decompress
			try:
				bitin = deflatedecompress.BitInputStream(inp)
				decomp = deflatedecompress.Decompressor.decompress_to_bytes(bitin)
			except ValueError as e:
				return "Invalid or corrupt compressed data: " + str(e)
			
			# Footer
			crc  = read_little_int32()
			size = read_little_int32()
		
		# Check decompressed data's length and CRC
		if size != len(decomp):
			return "Size mismatch: expected={}, actual={}".format(size, len(decomp.length))
		actualcrc = zlib.crc32(decomp) & 0xFFFFFFFF
		if crc != actualcrc:
			return "CRC-32 mismatch: expected={:08X}, actual={:08X}".format(crc, actualcrc)
		
		# Write decompressed data to output file
		with open(outfile, "wb") as out:
			out.write(decomp)
		
	except IOError as e:
		return "I/O exception: " + str(e)
	return None  # Success, no error message

	
if __name__ == "__main__":
	errmsg = main(sys.argv)
	if errmsg is not None:
		sys.exit(errmsg)
