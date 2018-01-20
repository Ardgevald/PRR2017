package ch.heigvd.prr.termination;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import ch.heigvd.prr.termination.util.ByteIntConverter;

/**
 * Cette classe représente un message envoyé sur le réseau. Cette classe est
 * abstraite, il est donc nécessaire de les étendres pour chaque type de
 * message.
 *
 * Plus de détail dans chacune des sous-classe
 * 
 * Remarquons que cette classe est un peu overkill pour son utilisation dans
 * le contexte du labo. Cependant, elle est facilement adaptable à d'autre 
 * messages qui pourraient être voulu dans un autre contexte
 *
 * @author Miguel Pombo Dias
 * @author Rémi Jacquemard
 */
public abstract class Message {

	/**
	 * Permet de récupérer la taille maximal possible d'un message à partir du
	 * nombre de site. Dans notre cas d'école, de simple type de message sont
	 * envoyé: un byte suffit donc
	 *
	 * @param numberOfHost le nombre de sites
	 * @return la taille du message si appelé avec la méthode toByteArray(), en
	 * byte
	 */
	public static int getMaxMessageSize(int numberOfHost) {
		return MessageType.BYTES;
	}

	/**
	 * Représente les différents types de message transitant sur le réseau
	 */
	public static enum MessageType {
		START_TASK, END_TOKEN;

		/**
		 * Permet de récupérer le numéro de type en byte de ce message. Utilisé
		 * afin d'envoyer un unique byte sur le réseau
		 *
		 * @return le byte correspondant au type courant
		 */
		public byte getByte() {
			return (byte) this.ordinal();
		}

		/**
		 * Permet de récupérer un type de message à partir de son numéro (byte)
		 *
		 * @param b le byte à parser
		 *
		 * @return Le type de message associer
		 */
		public static MessageType getMessageType(byte b) {
			return MessageType.values()[b];
		}

		/**
		 * Nombre de bytes utilisé afin de stocker cet enum dans un tableau de
		 * byte (envoyé par la suite sur le réseau)
		 */
		public static final int BYTES = Byte.BYTES;

	}

	/**
	 * Méthode à redéfinir dans les sous-classes afin de définir le type de
	 * message
	 *
	 * @return le type de message associé à la sous-classe
	 */
	protected abstract MessageType getMessageType();

	/**
	 * Permet de sérialiser le message courant dans un tableau de byte retourné.
	 *
	 * @return le tableau de byte
	 */
	public final byte[] toByteArray() {
		List<Byte> bytes = this.toByteList();
		Iterator<Byte> it = bytes.iterator();
		byte[] data = new byte[bytes.size()];
		for (int i = 0; i < data.length; i++) {
			data[i] = it.next();
		}

		return data;
	}

	/**
	 * Permet de récupérer une liste des bytes formant ce message Cette méthode
	 * doit idéalement être redéfinie par les sous classe si celle-ci y ajoute
	 * des informations. Par exemple, une annonce doit y ajouter les bytes
	 * formant la liste des sites avec aptitudes.
	 *
	 * @return la liste de byte associée.
	 */
	public List<Byte> toByteList() {
		LinkedList<Byte> bytes = new LinkedList<>();
		bytes.add(getMessageType().getByte());
		return bytes;
	}

	/**
	 * Permet, à partir d'un tableau de byte d'une certaine taille, d'en
	 * récupérer une instance d'un message.
	 *
	 * Afin de détecter par la suite le type de message reçu, on peut utiliser la
	 * méthode Message.getMessageType().
	 *
	 * Cette méthode est utilisée afin de parser un packet udp reçu depuis le
	 * réseau et d'en faire un message.
	 *
	 * On doit aussi passer la taille du message car le tableau passer en
	 * paramètre peut être plus grand que le message réel (data[] est enfaite un
	 * buffer souvant non remplis complètement)
	 *
	 * @param data Un tableau de byte contenant les infos du message
	 * @param size la taille du message
	 * @return Le message parsé
	 */
	public static Message parse(byte[] data, int size) {
		MessageType type = MessageType.getMessageType(data[0]);

		switch (type) {
			case END_TOKEN:
				return new EndTokenMessage();
			case START_TASK:
				return new StartTaskMessage();
			default:
				return null;
		}
	}

	// ------------- SUBCLASSES ---------------- //

	/**
	 * Indique à un site le démarrage d'une tâche. Dans un contexte réel, 
	 * nn trouverai dans ce message la tâche à effectuer (par exemple un calcul)
	 */
	public static class StartTaskMessage extends Message{

		@Override
		protected MessageType getMessageType() {
			return MessageType.END_TOKEN;
		}
		
	}
	
	/**
	 * Représente le jeton de terminaison. 
	 */
	public static class EndTokenMessage extends Message {

		/**
		 * Permet d'indiquer la terminaison
		 *
		 * @return un type de message END
		 */
		@Override
		protected MessageType getMessageType() {
			return MessageType.END_TOKEN;
		}
	}

}
