package nayuki.deflate;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import p79068.hash.Crc;
import p79068.util.DateTime;


public class GzipDecompress {
	
	public static void main(String[] args) throws IOException {
		BitInputStream in = new ByteBitInputStream(new FileInputStream(args[0]));
		byte[] b;
		
		// Header
		b = new byte[10];
		readFully(in, b);
		if (b[0] != 0x1F || b[1] != (byte)0x8B)
			throw new RuntimeException("Invalid GZIP magic number");
		if (b[2] != 8)
			throw new RuntimeException("Unsupported compression method: " + (b[2] & 0xFF));
		int flags = b[3] & 0xFF;
		
		// Reserved flags
		if ((flags & 0xE0) != 0)
			throw new RuntimeException("Reserved flags are set");
		
		// Modification time
		int mtime = (b[4] & 0xFF) | (b[5] & 0xFF) << 8 | (b[6] & 0xFF) << 16 | (b[7] & 0xFF) << 24;
		if (mtime != 0)
			System.out.println("Last modified: " + new DateTime(1970, 1, 1).add(mtime * 1000000L));
		else
			System.out.println("Last modified: N/A");
		
		// Extra flags
		switch (b[8] & 0xFF) {
			case 2:  System.out.println("Extra flags: Maximum compression");  break;
			case 4:  System.out.println("Extra flags: Fastest compression");  break;
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
		
		// Text flag
		if ((flags & 0x01) != 0)
			System.out.println("Flag: Text");
		
		// Extra flag
		if ((flags & 0x04) != 0) {
			System.out.println("Flag: Extra");
			b = new byte[2];
			readFully(in, b);
			int len = (b[0] & 0xFF) | (b[1] & 0xFF) << 8;
			readFully(in, new byte[len]);
		}
		
		// File name flag
		if ((flags & 0x08) != 0) {
			StringBuilder sb = new StringBuilder();
			while (true) {
				int temp = in.readByte();
				if (temp == -1)
					throw new EOFException();
				else if (temp == 0)
					break;
				else
					sb.append((char)temp);
			}
			System.out.println("File name: " + sb.toString());
		}
		
		// Header CRC flag
		if ((flags & 0x02) != 0) {
			b = new byte[2];
			readFully(in, b);
			System.out.printf("Header CRC-16: %04X%n", (b[0] & 0xFF) | (b[1] & 0xFF) << 8);
		}
		
		// Comment flag
		if ((flags & 0x10) != 0) {
			StringBuilder sb = new StringBuilder();
			while (true) {
				int temp = in.readByte();
				if (temp == -1)
					throw new EOFException();
				else if (temp == 0)
					break;
				else
					sb.append((char)temp);
			}
			System.out.println("Comment: " + sb.toString());
		}
		
		// Decompress
		byte[] decomp = Decompressor.decompress(in);
		
		// Footer
		b = new byte[4];
		readFully(in, b);
		int crc = (b[0] & 0xFF) | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16 | (b[3] & 0xFF) << 24;
		readFully(in, b);
		int size = (b[0] & 0xFF) | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16 | (b[3] & 0xFF) << 24;
		in.close();
		
		// Check
		if (size != decomp.length)
			throw new RuntimeException(String.format("Size mismatch: expected=%d, actual=%d", size, decomp.length));
		if (crc != getCrc32(decomp))
			throw new RuntimeException(String.format("CRC-32 mismatch: expected=%08X, actual=%08X", crc, getCrc32(decomp)));
		
		// Write decompressed data to output file
		OutputStream out = new FileOutputStream(args[1]);
		out.write(decomp);
		out.close();
	}
	
	
	private static void readFully(BitInputStream in, byte[] b) throws IOException {
		for (int i = 0; i < b.length; i++) {
			int temp = in.readByte();
			if (temp == -1)
				throw new EOFException();
			b[i] = (byte)temp;
		}
	}
	
	
	private static int getCrc32(byte[] decomp) {
		byte[] b = Crc.CRC32_FUNCTION.getHash(decomp).toBytes();
		return (b[0] & 0xFF) << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | (b[3] & 0xFF);
	}
	
}
