# 
# Simple DEFLATE decompressor (Python)
# 
# Copyright (c) Project Nayuki
# MIT License. See readme file.
# https://www.nayuki.io/page/simple-deflate-decompressor
# 

import datetime, pathlib, sys, zlib
from typing import Dict, List, Optional
import deflatedecompress


def main(argv: List[str]) -> Optional[str]:
	# Handle command line arguments
	if len(argv) != 3:
		return f"Usage: python {argv[0]} InputFile.gz OutputFile"
	infile = pathlib.Path(argv[1])
	if not infile.exists():
		return f"Input file does not exist: {infile}"
	if infile.is_dir():
		return f"Input file is a directory: {infile}"
	outfile = pathlib.Path(argv[2])
	
	try:
		# Start reading
		with infile.open("rb") as inp:
			
			# Define helper read functions based on 'inp'
			
			def read_byte() -> int:
				b: bytes = inp.read(1)
				if len(b) == 0:
					raise EOFError()
				return b[0]
			
			def read_little_int16() -> int:
				temp: int = read_byte()
				return temp | read_byte() << 8
			
			def read_little_int32() -> int:
				temp: int = read_little_int16()
				return temp | read_little_int16() << 16
			
			def read_null_terminated_string() -> str:
				sb = bytearray()
				while True:
					b: int = read_byte()
					if b == 0:
						return sb.decode("UTF-8")
					sb.append(b)
			
			
			# Header
			if read_byte() != 0x1F:
				return "Invalid GZIP magic number"
			if read_byte() != 0x8B:
				return "Invalid GZIP magic number"
			compmeth: int = read_byte()
			if compmeth != 8:
				return f"Unsupported compression method: {str(compmeth)}"
			flags: int = read_byte()
			
			# Reserved flags
			if flags & 0xE0 != 0:
				return "Reserved flags are set"
			
			# Modification time
			mtime: int = read_little_int32()
			if mtime != 0:
				dt = datetime.datetime.fromtimestamp(mtime, datetime.timezone.utc)
				print(f"Last modified: {dt}")
			else:
				print("Last modified: N/A")
			
			# Extra flags
			extraflags: int = read_byte()
			if extraflags == 2:
				print("Extra flags: Maximum compression")
			elif extraflags == 4:
				print("Extra flags: Fastest compression")
			else:
				print(f"Extra flags: Unknown ({extraflags})")
			
			# Operating system
			OPERATING_SYSTEMS: Dict[int,str] = {
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
			osbyte: int = read_byte()
			osstr: str = OPERATING_SYSTEMS.get(osbyte, "Really unknown")
			print(f"Operating system: {osstr}")
			
			# Handle assorted flags
			if flags & 0x01 != 0:
				print("Flag: Text")
			if flags & 0x04 != 0:
				print("Flag: Extra")
				count: int = read_little_int16()
				while count > 0:  # Skip extra data
					n: int = len(inp.read(count))
					if n == 0:
						raise EOFError()
					count -= n
			if flags & 0x08 != 0:
				print(f"File name: {read_null_terminated_string()}")
			if flags & 0x02 != 0:
				print(f"Header CRC-16: {read_little_int16():04X}")
			if flags & 0x10 != 0:
				print(f"Comment: {read_null_terminated_string()}")
			
			# Decompress
			try:
				bitin = deflatedecompress.BitInputStream(inp)
				decomp: bytes = deflatedecompress.Decompressor.decompress_to_bytes(bitin)
			except ValueError as e:
				return f"Invalid or corrupt compressed data: {e}"
			
			# Footer
			crc : int = read_little_int32()
			size: int = read_little_int32()
		
		# Check decompressed data's length and CRC
		if size != len(decomp) % 2**32:
			return f"Size mismatch: expected={size}, actual={len(decomp)}"
		actualcrc = zlib.crc32(decomp) & 0xFFFFFFFF
		if crc != actualcrc:
			return f"CRC-32 mismatch: expected={crc:08X}, actual={actualcrc:08X}"
		
		# Write decompressed data to output file
		with outfile.open("wb") as out:
			out.write(decomp)
		
	except IOError as e:
		return f"I/O exception: {e}"
	return None  # Success, no error message

	
if __name__ == "__main__":
	errmsg = main(sys.argv)
	if errmsg is not None:
		sys.exit(errmsg)
