package util;

import java.nio.ByteBuffer;

/**
 *
 */
public class ByteLongConverter {

	public static byte[] longToBytes(long x) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.putLong(x);
		return buffer.array();
	}

	public static long bytesToLong(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.put(bytes);
		buffer.flip();//need flip 
		return buffer.getLong();
	}
}
