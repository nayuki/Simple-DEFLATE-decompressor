/* 
 * Simple DEFLATE decompressor (Java)
 * 
 * Copyright (c) Project Nayuki
 * MIT License. See readme file.
 * https://www.nayuki.io/page/simple-deflate-decompressor
 */

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.BitSet;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;


/**
 * Decompression application for the gzip file format.
 * <p>Usage: java GzipDecompress InputFile.gz OutputFile</p>
 * <p>This decompresses a single gzip input file into a single output file. The program also prints
 * some information to standard output, and error messages if the file is invalid/corrupt.</p>
 */
public final class GzipDecompress {
	
	public static void main(String[] args) {
		String msg = submain(args);
		if (msg != null) {
			System.err.println(msg);
			System.exit(1);
		}
	}
	
	
	// Returns null if successful, otherwise returns an error message string.
	private static String submain(String[] args) {
		// Handle command line arguments
		if (args.length != 2)
			return "Usage: java GzipDecompress InputFile.gz OutputFile";
		File inFile = new File(args[0]);
		if (!inFile.exists())
			return "Input file does not exist: " + inFile;
		if (inFile.isDirectory())
			return "Input file is a directory: " + inFile;
		Path outFile = Paths.get(args[1]);
		
		try {
			byte[] decomp;
			int crc, size;
			// Start reading
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(inFile), 16 * 1024))) {
				// Header
				BitSet flags;
				{
					if (in.readUnsignedShort() != 0x1F8B)
						return "Invalid GZIP magic number";
					int compMeth = in.readUnsignedByte();
					if (compMeth != 8)
						return "Unsupported compression method: " + compMeth;
					var flagByte = new byte[1];
					in.readFully(flagByte);
					flags = BitSet.valueOf(flagByte);
					
					// Reserved flags
					if (flags.get(5) || flags.get(6) || flags.get(7))
						return "Reserved flags are set";
					
					// Modification time
					int mtime = readLittleEndianInt32(in);
					if (mtime != 0)
						System.out.println("Last modified: " + Instant.EPOCH.plusSeconds(mtime));
					else
						System.out.println("Last modified: N/A");
					
					// Extra flags
					int extraFlags = in.readUnsignedByte();
					System.out.println("Extra flags: " + switch (extraFlags) {
						case 2  -> "Maximum compression";
						case 4  -> "Fastest compression";
						default -> "Unknown (" + extraFlags + ")";
					});
					
					// Operating system
					int operatingSystem = in.readUnsignedByte();
					String os = switch (operatingSystem) {
						case   0 -> "FAT";
						case   1 -> "Amiga";
						case   2 -> "VMS";
						case   3 -> "Unix";
						case   4 -> "VM/CMS";
						case   5 -> "Atari TOS";
						case   6 -> "HPFS";
						case   7 -> "Macintosh";
						case   8 -> "Z-System";
						case   9 -> "CP/M";
						case  10 -> "TOPS-20";
						case  11 -> "NTFS";
						case  12 -> "QDOS";
						case  13 -> "Acorn RISCOS";
						case 255 -> "Unknown";
						default  -> "Really unknown (" + operatingSystem + ")";
					};
					System.out.println("Operating system: " + os);
				}
				
				// Handle assorted flags
				if (flags.get(0))
					System.out.println("Flag: Text");
				if (flags.get(2)) {
					System.out.println("Flag: Extra");
					in.skipNBytes(readLittleEndianUint16(in));
				}
				if (flags.get(3))
					System.out.println("File name: " + readNullTerminatedString(in));
				if (flags.get(4))
					System.out.println("Comment: " + readNullTerminatedString(in));
				if (flags.get(1))
					System.out.printf("Header CRC-16: %04X%n", readLittleEndianUint16(in));
				
				// Decompress
				try {
					decomp = Decompressor.decompress(new ByteBitInputStream(in));
				} catch (DataFormatException e) {
					return "Invalid or corrupt compressed data: " + e.getMessage();
				}
				
				// Footer
				crc  = readLittleEndianInt32(in);
				size = readLittleEndianInt32(in);
			}
			
			// Check decompressed data's length and CRC
			if (size != decomp.length)
				return String.format("Size mismatch: expected=%d, actual=%d", size, decomp.length);
			if (crc != getCrc32(decomp))
				return String.format("CRC-32 mismatch: expected=%08X, actual=%08X", crc, getCrc32(decomp));
			
			// Write decompressed data to output file
			Files.write(outFile, decomp);
			
		} catch (IOException e) {
			return "I/O exception: " + e.getMessage();
		}
		return null;  // Success, no error message
	}
	
	
	/*---- Helper methods ----*/
	
	private static String readNullTerminatedString(DataInput in) throws IOException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		while (true) {
			byte b = in.readByte();
			if (b == 0)
				break;
			bout.write(b);
		}
		return new String(bout.toByteArray(), StandardCharsets.UTF_8);
	}
	
	
	private static int getCrc32(byte[] data) {
		CRC32 crc = new CRC32();
		crc.update(data);
		return (int)crc.getValue();
	}
	
	
	private static int readLittleEndianUint16(DataInput in) throws IOException {
		return Integer.reverseBytes(in.readUnsignedShort()) >>> 16;
	}
	
	
	private static int readLittleEndianInt32(DataInput in) throws IOException {
		return Integer.reverseBytes(in.readInt());
	}
	
}
