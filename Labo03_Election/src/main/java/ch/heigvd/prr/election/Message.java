package ch.heigvd.prr.election;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import util.ByteIntConverter;

/**
 *
 */
public abstract class Message {

	public static int getMaxMessageSize(int numberOfHost) {
		return numberOfHost * (Integer.BYTES + MessageType.BYTES) + MessageType.BYTES;
	}

	public static enum MessageType {
		/**
		 * Un message de type annonce est formé de d'abord le type de message,
		 * puis des aptitudes (int) de chacun : 
       * |TYPE|index1|apt|apt|apt|apt|index2|apt|apt|apt|apt|...
		 */
		ANNOUNCE,
		/**
		 * Un message de type results est formé de d'abord le type de message,
		 * puis de l'index de l'hôte élu (byte), puis des sites participant
		 */
		RESULTS,
		/**
		 * Un message de type echo permet de vérifier si l'élu est toujours up
		 */
		ECHO,
		/**
		 * Message reçu après avoir envoyé un message à un voisin. Utile pour
		 * détecter les pannes de sites voisin.
		 */
		QUITTANCE;

		public byte getByte() {
			return (byte) this.ordinal();
		}

		public static MessageType getMessageType(byte b) {
			return MessageType.values()[b];
		}
      
      public static final int BYTES = Byte.BYTES;

	}

	protected abstract MessageType getMessageType();

	public byte[] toByteArray() {
		List<Byte> bytes = this.toByteList();
		Iterator<Byte> it = bytes.iterator();
		byte[] data = new byte[bytes.size()];
		for (int i = 0; i < data.length; i++) {
			data[i] = it.next();
		}

		return data;
	}

	public List<Byte> toByteList() {
		LinkedList<Byte> bytes = new LinkedList<>();
		bytes.add(getMessageType().getByte());
		return bytes;
	}

	public static Message parse(byte[] data, int size) {
		MessageType type = MessageType.getMessageType(data[0]);

		switch (type) {
			case ANNOUNCE:
				return new AnnounceMessage(data, size);
			case ECHO:
				return new EchoMessage();
			case QUITTANCE:
				return new QuittanceMessage();
			case RESULTS:
				return new ResultsMessage(data, size);
			default:
				return null;
		}
	}

	/**
    * 
    */
	public static class AnnounceMessage extends Message {

		private Map<Byte, Integer> apptitudes;

		public AnnounceMessage(Map<Byte, Integer> apptitudes) {
			this.apptitudes = apptitudes;
		}

		public AnnounceMessage() {
			this.apptitudes = new HashMap<>();
		}

		public AnnounceMessage(byte[] data, int size) {
			this();

			// On cherche le nombre de site
			int rowSize = MessageType.BYTES + Integer.BYTES;
			int nbSite = (size - MessageType.BYTES) / rowSize;

			for (int i = 0; i < nbSite; i++) { // Pour chaque site
				byte hostIndex = data[MessageType.BYTES + i * rowSize];

				byte[] aptBytes = new byte[Integer.BYTES];
				for (int j = 0; j < aptBytes.length; j++) {
					aptBytes[j] = data[MessageType.BYTES + i * rowSize + 1 + j];
				}

				int curApptitude = ByteIntConverter.bytesToInt(aptBytes);
				apptitudes.put(hostIndex, curApptitude);
			}
		}

		public void setApptitude(byte hostIndex, int apptitude) {
			this.apptitudes.put(hostIndex, apptitude);
		}

		public Integer getApptitude(byte hostIndex) {
			return this.apptitudes.get(hostIndex);
		}

		public Map<Byte, Integer> getApptitudes() {
			return apptitudes;
		}
		
      
		@Override
		protected MessageType getMessageType() {
			return MessageType.ANNOUNCE;
		}

		@Override
		public List<Byte> toByteList() {
			List<Byte> bytes = super.toByteList();

         apptitudes.entrySet().stream().map((entry) -> {
            Byte hostIndex = entry.getKey();
            bytes.add(hostIndex);
            Integer apptitude = entry.getValue();
            return apptitude;
         }).map((aptitude) -> ByteIntConverter.intToByte(aptitude)).forEachOrdered((app) -> {
            for (byte b : app) {
               bytes.add(b);
            }
         });

			return bytes;
		}

	}

	public static class ResultsMessage extends Message {

		private final byte electedIndex;
		private final LinkedList<Byte> seenSites;

		public ResultsMessage(byte electedIndex) {
			this.electedIndex = electedIndex;
			this.seenSites = new LinkedList<>();
		}

		public ResultsMessage(byte[] data, int size) {
			this.electedIndex = data[1];
			this.seenSites = new LinkedList<>();

			for (int i = 1; i < size; i++) {
				seenSites.add(data[i]);
			}
		}

		public void addSeenSite(byte hostIndex) {
			this.seenSites.add(hostIndex);
		}

		@Override
		protected MessageType getMessageType() {
			return MessageType.RESULTS;
		}

		@Override
		public List<Byte> toByteList() {
			List<Byte> bytes = super.toByteList();
			bytes.add(electedIndex);
			return bytes;
		}

		public byte getElectedIndex() {
			return electedIndex;
		}

		public LinkedList<Byte> getSeenSites() {
			return seenSites;
		}

	}

	public static class QuittanceMessage extends Message {

		@Override
		protected MessageType getMessageType() {
			return MessageType.QUITTANCE;
		}
	}

	public static class EchoMessage extends Message {

		@Override
		protected MessageType getMessageType() {
			return MessageType.ECHO;
		}
	}

}
