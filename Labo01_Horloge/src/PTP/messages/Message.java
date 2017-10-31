package PTP.messages;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import util.ByteLongConverter;

/**
 *
 * @author Remi
 */
public abstract class Message {

	private final byte id;

	private final MessageType messageType;

	protected final LinkedList<Byte> byteList = new LinkedList<>();

	public byte[] getBytes() {
		byte[] bytes = new byte[byteList.size()];

		Iterator<Byte> it = byteList.iterator();

		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = it.next();
		}

		return bytes;
	}

	public enum MessageType {
		SyncMessage, FollowUpMessage, DelayRequest, DelayResponse;

		public byte getByteValue() {
			return (byte) this.ordinal();
		}
	}

	public Message(MessageType messageType, byte id) {
		this.messageType = messageType;
		this.id = id;

		byteList.add(messageType.getByteValue());
		byteList.add(id);
	}

	public static <T> T parse(byte[] array, Class<T> cl) throws BadMessageException, ClassCastException {
		Message m = parse(array);

		return cl.cast(m);
	}

	public static Message parse(byte[] array) throws BadMessageException {
		MessageType type = MessageType.values()[array[0]];

		byte id = array[1];

		long time;

		switch (type) {
			case SyncMessage:
				return new SyncMessage(id);
			case FollowUpMessage:
				time = ByteLongConverter.bytesToLong(Arrays.copyOfRange(array, 2, array.length));
				return new FollowUpMessage(id, time);
			case DelayRequest:
				return new DelayRequestMessage(id);
			case DelayResponse:
				time = ByteLongConverter.bytesToLong(Arrays.copyOfRange(array, 2, array.length));
				return new DelayResponseMessage(id, time);
		}

		throw new BadMessageException("The paquet received is not a message of this");
	}

	public static class BadMessageException extends Exception {

		public BadMessageException(String string) {
			super(string);
		}

	}

	public byte getId() {
		return id;
	}

	public MessageType getMessageType() {
		return messageType;
	}

	// CHILD MESSAGES -------
	public static class SyncMessage extends Message {

		public SyncMessage(byte id) {
			super(MessageType.SyncMessage, id);
		}

	}

	public static class FollowUpMessage extends Message {

		private final long time;

		public FollowUpMessage(byte id, long time) {
			super(MessageType.FollowUpMessage, id);
			this.time = time;

			for (byte b : ByteLongConverter.longToBytes(time)) {
				byteList.add(b);
			}

		}

		public long getTime() {
			return time;
		}

	}

	public static class DelayRequestMessage extends Message {

		public DelayRequestMessage(byte id) {
			super(MessageType.FollowUpMessage, id);
		}
	}

	public static class DelayResponseMessage extends Message {

		private final long time;

		public DelayResponseMessage(byte id, long time) {
			super(MessageType.DelayResponse, id);

			this.time = time;

			for (byte b : ByteLongConverter.longToBytes(time)) {
				byteList.add(b);
			}
		}

		public long getTime() {
			return time;
		}

	}

}
