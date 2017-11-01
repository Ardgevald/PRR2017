package PTP;

/**
 * Classe de paramètres du slave et master
 */
public class Protocol {

	// addresse multicast et ports utilisés
	public static final String GROUP_ADDRESS = "234.56.78.9";
	public static final int SYNC_PORT = 1234;
	public static final int DELAY_PORT = 1235;
	
	// période d'attente pour l'envoi d'un paquet
	public static final long SYNC_PERIOD = 400;

	/**
	 * enum décrivant les quatre types de messages que l'on peut avoir
	 */
	public enum MessageType {
		SYNC,
		FOLLOW_UP,
		DELAY_REQUEST,
		DELAY_RESPONSE;
		
		public byte asByte() {
			return (byte) this.ordinal();
		}
	}
	
	/**
	 * enum décrivant la structure d'un message
	 * Les deux premiers bytes de nos messages sont le type de message
	 * et un identifiant pour savoir si les messages vont par pair
	 * (Sync avec Follow-Up et les deux Delay_*)
	 */
	public enum MessageStruct {
		TYPE,
		ID
	}
}
