package ch.heigvd.prr.election;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import util.ByteIntConverter;

/**
 *
 */
public abstract class Message {

	public static int getMaxMessageSize(int numberOfHost) {
		return numberOfHost * Integer.BYTES + 1;
	}

	public static enum MessageType {
		/**
		 * Un message de type annonce est formé de d'abord le type de message,
		 * puis des aptitudes (int) de chacun :
		 * |TYPE|APT1|APT1|APT1|APT1|APT2|APT2|APT2|APT2|...
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

	public static Message parse(byte[] data) {
		MessageType type = MessageType.getMessageType(data[0]);

		switch (type) {
			case ANNOUNCE:
				return new AnnounceMessage(data);
			case ECHO:
				return new EchoMessage();
			case QUITTANCE:
				return new QuittanceMessage();
			case RESULTS:
				return new ResultsMessage(data);
			default:
				return null;
		}
	}

	// ------------------
	public static class AnnounceMessage extends Message {

		private int[] apptitudes;

		public AnnounceMessage(int[] apptitudes) {
			this.apptitudes = apptitudes;
		}

		public AnnounceMessage(int numberOfHost) {
			apptitudes = new int[numberOfHost];
		}

		public AnnounceMessage(byte[] data) {
			this((data.length - 1) / Integer.BYTES);
			
			for (int i = 0; i < apptitudes.length; i++) { // Pour chaque site
				byte[] aptBytes = new byte[Integer.BYTES];
				for (int j = 0; j < aptBytes.length; j++) {
					aptBytes[j] = data[1 + i * (aptBytes.length)];
				}

				int curApptitude = ByteIntConverter.bytesToInt(aptBytes);
				apptitudes[i] = curApptitude;
			}
		}

		public void setApptitude(int hostIndex, int apptitude){
			this.apptitudes[hostIndex] = apptitude;
		}
		
		public int getApptitude(int hostIndex){
			return this.apptitudes[hostIndex];
		}
		
		/*
		public AnnounceMessage(List<Site> sites, byte[] data) {
			this(sites);

			Iterator<Site> it = sites.iterator();

			for (int i = 0; i < sites.size(); i++) { // Pour chaque site
				Site curSite = it.next();

				// On récupère l'aptitude
				byte[] aptBytes = new byte[Integer.BYTES];
				for (int j = 0; j < aptBytes.length; j++) {
					aptBytes[j] = data[1 + i * (aptBytes.length)];
				}

				int curApptitude = ByteIntConverter.bytesToInt(aptBytes);
				curSite.setApptitude(curApptitude);
			}
		}//*/
		@Override
		protected MessageType getMessageType() {
			return MessageType.ANNOUNCE;
		}

		@Override
		public List<Byte> toByteList() {
			List<Byte> bytes = super.toByteList();

			for (Integer apptitude : apptitudes) {
				byte[] app = ByteIntConverter.intToByte(apptitude);
				for (byte b : app) {
					bytes.add(b);
				}
			}

			return bytes;
		}

	}

	public static class ResultsMessage extends Message {

		private byte electedIndex;
		private LinkedList<Byte> seenSites;

		public ResultsMessage(byte electedIndex) {
			this.electedIndex = electedIndex;
			this.seenSites = seenSites;
		}

		public ResultsMessage(byte[] data) {
			this.electedIndex = data[1];
			this.seenSites = new LinkedList<>();
			
			for(int i = 1; i < data.length; i++){
				seenSites.add(data[i]);
			}
		}
		
		public void addSeenSite(byte hostIndex){
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
