package util;

import java.nio.ByteBuffer;

/**
 * Classe utilitaire pour la conversion entre int et byte
 */
public class ByteIntConverter {

	/**
	 * Convertit un int en tableau de bytes
	 * @param x le int à convertir
	 * @return le tableau de bytes correspondant
	 */
	public static byte[] intToByte(int x) {
		ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
		buffer.putInt(x);
		return buffer.array();
	}

	/**
	 * Convertit un tableau de bytes en int
	 * @param bytes le tableau à convertir
	 * @return le int correspondant
	 */
	public static int bytesToInt(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
		buffer.put(bytes);
		buffer.flip();//need flip 
		return buffer.getInt();
	}
}
