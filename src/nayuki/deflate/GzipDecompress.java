package nayuki.deflate;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.zip.CRC32;


public class GzipDecompress {
	
	public static void main(String[] args) {
		String msg = submain(args);
		if (msg != null) {
			System.err.println(msg);
			System.exit(1);
		}
	}
	
	
	private static String submain(String[] args) {
		// Check arguments
		if (args.length != 2)
			return "Usage: java GzipDecompress InputFile OutputFile";
		
		File inFile = new File(args[0]);
		if (!inFile.exists())
			return "Input file does not exist: " + inFile;
		if (inFile.isDirectory())
			return "Input file is a directory: " + inFile;
		
		try {
			// Start reading
			DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(inFile), 16 * 1024));
			byte[] decomp;
			int crc, size;
			try {
				// Header
				int flags;
				{
					byte[] b = new byte[10];
					in.readFully(b);
					if (b[0] != 0x1F || b[1] != (byte)0x8B)
						return "Invalid GZIP magic number";
					if (b[2] != 8)
						return "Unsupported compression method: " + (b[2] & 0xFF);
					flags = b[3] & 0xFF;
					
					// Reserved flags
					if ((flags & 0xE0) != 0)
						return "Reserved flags are set";
					
					// Modification time
					int mtime = (b[4] & 0xFF) | (b[5] & 0xFF) << 8 | (b[6] & 0xFF) << 16 | b[7] << 24;
					if (mtime != 0)
						System.out.println("Last modified: " + new Date(mtime * 1000L));
					else
						System.out.println("Last modified: N/A");
					
					// Extra flags
					switch (b[8] & 0xFF) {
						case 2:   System.out.println("Extra flags: Maximum compression");  break;
						case 4:   System.out.println("Extra flags: Fastest compression");  break;
						default:  System.out.println("Extra flags: Unknown");  break;
					}
					
					// Operating system
					String os;
					switch (b[9] & 0xFF) {
						case   0:  os = "FAT";             break;
						case   1:  os = "Amiga";           break;
						case   2:  os = "VMS";             break;
						case   3:  os = "Unix";            break;
						case   4:  os = "VM/CMS";          break;
						case   5:  os = "Atari TOS";       break;
						case   6:  os = "HPFS";            break;
						case   7:  os = "Macintosh";       break;
						case   8:  os = "Z-System";        break;
						case   9:  os = "CP/M";            break;
						case  10:  os = "TOPS-20";         break;
						case  11:  os = "NTFS";            break;
						case  12:  os = "QDOS";            break;
						case  13:  os = "Acorn RISCOS";    break;
						case 255:  os = "Unknown";         break;
						default :  os = "Really unknown";  break;
					}
					System.out.println("Operating system: " + os);
				}
				
				// Handle assorted flags
				if ((flags & 0x01) != 0)
					System.out.println("Flag: Text");
				if ((flags & 0x04) != 0) {
					System.out.println("Flag: Extra");
					byte[] b = new byte[2];
					in.readFully(b);
					int len = (b[0] & 0xFF) | (b[1] & 0xFF) << 8;
					in.readFully(new byte[len]);  // Skip extra data
				}
				if ((flags & 0x08) != 0)
					System.out.println("File name: " + readNullTerminatedString(in));
				if ((flags & 0x02) != 0) {
					byte[] b = new byte[2];
					in.readFully(b);
					System.out.printf("Header CRC-16: %04X%n", (b[0] & 0xFF) | (b[1] & 0xFF) << 8);
				}
				if ((flags & 0x10) != 0)
					System.out.println("Comment: " + readNullTerminatedString(in));
				
				// Decompress
				try {
					decomp = Decompressor.decompress(new ByteBitInputStream(in));
				} catch (FormatException e) {
					return "Invalid or corrupt compressed data: " + e.getMessage();
				}
				
				// Footer
				{
					byte[] b = new byte[8];
					in.readFully(b);
					crc  = (b[0] & 0xFF) | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16 | b[3] << 24;
					size = (b[4] & 0xFF) | (b[5] & 0xFF) << 8 | (b[6] & 0xFF) << 16 | b[7] << 24;
				}
			} finally {
				in.close();
			}
			
			// Check
			if (size != decomp.length)
				return String.format("Size mismatch: expected=%d, actual=%d", size, decomp.length);
			if (crc != getCrc32(decomp))
				return String.format("CRC-32 mismatch: expected=%08X, actual=%08X", crc, getCrc32(decomp));
			
			// Write decompressed data to output file
			OutputStream out = new FileOutputStream(args[1]);
			try {
				out.write(decomp);
			} finally {
				out.close();
			}
		} catch (IOException e) {
			return "I/O exception: " + e.getMessage();
		}
		
		return null;
	}
	
	
	private static String readNullTerminatedString(DataInput in) throws IOException {
		StringBuilder sb = new StringBuilder();
		while (true) {
			byte c = in.readByte();
			if (c == 0)
				break;
			else
				sb.append((char)(c & 0xFF));
		}
		return sb.toString();
	}
	
	
	private static int getCrc32(byte[] data) {
		CRC32 crc = new CRC32();
		crc.update(data);
		return (int)crc.getValue();
	}
	
}
