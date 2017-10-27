package PTP.messages;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import util.ByteLongConverter;

/**
 *
 * @author Remi
 */
public abstract class Message {

	private final byte id;
	
	private static final int BUFFER_SIZE = 20;

	private final MessageType messageType;

	protected final ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);

	public byte[] getBytes() {
		return byteBuffer.array();
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

		byteBuffer.put(messageType.getByteValue());
		byteBuffer.put(id);
	}

	public static Message parse(byte[] array) throws Exception {
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

		throw new Exception("Unable to parse, bytes: " + Arrays.toString(array));
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

			byteBuffer.putLong(time);
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
			byteBuffer.putLong(time);
		}

		public long getTime() {
			return time;
		}
		
		

	}

}
