package util;

/**
 *
 */
public class Protocol {

	public static final String GROUP_ADDRESS = "234.56.78.9";
	public static final int SYNC_PORT = 1234;
	public static final int DELAY_PORT = 1235;
	public static final long SYNC_PERIOD = 400;

	public enum MessageType {
		SYNC,
		FOLLOW_UP,
		DELAY_REQUEST,
		DELAY_RESPONSE;
		
		public byte asByte() {
			return (byte) this.ordinal();
		}
	}
	
	public enum MessageStruct {
		TYPE,
		ID
	}
}
