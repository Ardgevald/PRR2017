package util;

import java.nio.ByteBuffer;

/**
 * Classe utilitaire pour la conversion entre long et byte
 */
public class ByteLongConverter {

	/**
	 * Convertit un long en tableau de bytes
	 * @param x le long à convertir
	 * @return le tableau de bytes correspondant
	 */
	public static byte[] longToBytes(long x) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.putLong(x);
		return buffer.array();
	}

	/**
	 * Convertit un tableau de bytes en long
	 * @param bytes le tableau à convertir
	 * @return le long correspondant
	 */
	public static long bytesToLong(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.put(bytes);
		buffer.flip();//need flip 
		return buffer.getLong();
	}
}
