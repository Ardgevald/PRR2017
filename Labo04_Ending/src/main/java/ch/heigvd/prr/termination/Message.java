package ch.heigvd.prr.termination;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import ch.heigvd.prr.termination.util.ByteIntConverter;

/**
 * Cette classe représente un message envoyé sur le réseau Cette classe est
 * abstraite, il est donc nécessaire de les étendres pour chaque type de
 * message.
 *
 * Plus de détail dans chacune des sous-classe
 *
 *
 * @author Miguel Pombo Dias
 * @author Rémi Jacquemard
 */
public abstract class Message {

	/**
	 * Permet de récupérer la taille maximal possible d'un message à partir du
	 * nombre de site. C'est enfaite la taille maximal que peut avoir un message
	 * de type Annonce, soit le plus grand des messages
	 *
	 * @param numberOfHost le nombre de sites
	 * @return la taille du message si appelé avec la méthode toByteArray(), en
	 * byte
	 */
	public static int getMaxMessageSize(int numberOfHost) {
		return numberOfHost * (Integer.BYTES + MessageType.BYTES) + MessageType.BYTES;
	}

	/**
	 * Représente les différents types de message transitant sur le réseau
	 */
	public static enum MessageType {
		/**
		 * Une annonce est utilisée lorsqu'on souhaite annoncer son aptitude aux
		 * autres. C'est la première étape d'une élection
		 *
		 * Lorsqu'une élection est créée, le site initiateur crée un message de ce
		 * type et y ajoute son aptitude. Ce message est envoyé ensuite au suivant
		 * dans l'anneau. Le suivant reçoit le message, y ajoute son aptitude,
		 * puis le transfère lui aussi à son voisin.
		 *
		 * Un message de type annonce est formé de d'abord le type de message,
		 * puis des aptitudes (int) de chacun :
		 * |TYPE|index1|apt|apt|apt|apt|index2|apt|apt|apt|apt|...
		 */
		ANNOUNCE,
		/**
		 * Un RESULT est utilisé à la fin de l'élection, lorsque l'élu a été
		 * choisis.
		 *
		 * Lorsqu'un site détecte la fin de la phase de votation à la réception
		 * d'un message de type ANNONCE, et donc que l'élu peut être désigné
		 * unanimement, le site arrête la diffusion des messages annonces et envoi
		 * un message de type RESULTS au suivant. Ce message contenant l'élu, tous
		 * les sites obtiendront le même élu.
		 *
		 * Chacun des sites prennent tour à tour compte de ce message, en s'y
		 * ajoutant en tant que site l'ayant vu.
		 *
		 * En tant normal, le site ayant initié la propagation de ce message est
		 * le premier a re-recevoir ce message. Il peut le détecter car il est
		 * lui-même dans la liste des sites qui ont vu ce message. Vu qu'il le
		 * reçoit pour la deuxième fois, tous les sites ont vu ce message: Il peut
		 * donc stopper la propagation de ce message, et obtenir l'élu
		 *
		 * Un message de type results est formé de d'abord le type de message,
		 * puis de l'index de l'hôte élu (byte), puis des sites participant
		 * |type|electedHostIndex|index3|index4|...
		 */
		RESULTS,
		/**
		 * Un message de type echo permet de vérifier si l'élu est toujours up.
		 *
		 * Lorsque l'ElectionManager reçoit un END, il le quittance. Le client
 ayant voulu vérifier l'état du site reçoit donc un message de type
 QUITTANCE Il est formé de la sorte: |type|
		 */
		END,
		/**
		 * Message "répondu" à la réception de message. Il permet de confirmer la
		 * bonne réception d'un message d'un destinataire. Lorsqu'on envoi un
		 * message à un destinataire, on peut donc : 1) L'envoyer 2) Attendre la
		 * réception d'une quittance 2.1) Si pas de quittance au bout de 's'
		 * secondes, le destinataire n'est donc pas atteignable.
		 *
		 * Dans notre application, ceci est utile afin de vérifier si notre voisin
		 * est up. Dans le cas contraire, on essaie en envoyant au voisin de notre
		 * voisin, etc.
		 */
		QUITTANCE;

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
			case ANNOUNCE:
				return new AnnounceMessage(data, size);
			case END:
				return new EndMessage();
			case QUITTANCE:
				return new QuittanceMessage();
			case RESULTS:
				return new ResultsMessage(data, size);
			default:
				return null;
		}
	}

	// ------------- SUBCLASSES ---------------- //
	/**
	 * Une annonce est utilisée lorsqu'on souhaite annoncer son aptitude aux
	 * autres. C'est la première étape d'une élection
	 *
	 * Lorsqu'une élection est créée, le site initiateur crée un message de ce
	 * type et y ajoute son aptitude. Ce message est envoyé ensuite au suivant
	 * dans l'anneau. Le suivant reçoit le message, y ajoute son aptitude, puis
	 * le transfère lui aussi à son voisin.
	 *
	 * Un message de type annonce est formé de d'abord le type de message, puis
	 * des aptitudes (int) de chacun :
	 * |TYPE|index1|apt|apt|apt|apt|index2|apt|apt|apt|apt|...
	 */
	public static class AnnounceMessage extends Message {

		// Map contenant, pour chaque site (clé), son aptitude (valeur)
		private Map<Byte, Integer> aptitudes;

		/**
		 * Permet de créer un nouveau message à partir d'une map La clé de la map
		 * correspond à l'indice du site, la valeur à son aptitude
		 *
		 * @param aptitudes les aptitudes de chacun des sites
		 */
		public AnnounceMessage(Map<Byte, Integer> aptitudes) {
			this.aptitudes = aptitudes;
		}

		/**
		 * Permet de créer un nouveau message de type annonce vide
		 */
		public AnnounceMessage() {
			this.aptitudes = new HashMap<>();
		}

		/**
		 * Permet de créer un message de type annonce à partir d'un tableau de
		 * byte. Dans notre application, ce tableau est reçu depuis le réseau.
		 * Cette méthode se charge de parser le tableau data et d'en faire un
		 * message de type annonce
		 *
		 * On doit aussi passer la taille du message car le tableau passer en
		 * paramètre peut être plus grand que le message réel (data[] est enfaite
		 * un buffer souvant non remplis complètement)
		 *
		 * @param data le tableau de byte contenant un AnnounceMessage sérialisé
		 * @param size la taille effective du tableau
		 */
		public AnnounceMessage(byte[] data, int size) {
			this();

			// On cherche le nombre de site
			int rowSize = MessageType.BYTES + Integer.BYTES;
			int nbSite = (size - MessageType.BYTES) / rowSize;

			for (int i = 0; i < nbSite; i++) { // Pour chaque site
				// On récupère l'index du site à l'endroit courant de la liste
				byte hostIndex = data[MessageType.BYTES + i * rowSize];

				// On récupère les bytes contenant l'aptitude du dit site
				byte[] aptBytes = new byte[Integer.BYTES];
				for (int j = 0; j < aptBytes.length; j++) {
					aptBytes[j] = data[MessageType.BYTES + i * rowSize + 1 + j];
				}

				// On convertit ces bytes en integer et on l'ajoute à la liste
				// courante des aptitudes
				int curApptitude = ByteIntConverter.bytesToInt(aptBytes);
				aptitudes.put(hostIndex, curApptitude);
			}
		}

		/**
		 * Permet d'ajouter ou de modifier l'aptitude associée à un site dans ce
		 * message
		 *
		 * @param hostIndex l'index du site
		 * @param aptitude l'aptitude associée
		 */
		public void setAptitude(byte hostIndex, int aptitude) {
			this.aptitudes.put(hostIndex, aptitude);
		}

		/**
		 * Permet de récupérer l'aptitude d'un site stocké dans ce message dont le
		 * numéro est passé en paramètre
		 *
		 * @param hostIndex le numéro du site dont on souhaite récupérer
		 * l'aptitude
		 * @return l'aptitude associée, ou null si l'aptitude pour le site demandé
		 * est introuvable dans ce message
		 */
		public Integer getAptitude(byte hostIndex) {
			return this.aptitudes.get(hostIndex);
		}

		/**
		 * Permet de récupérer la map de toutes les aptitudes stockées dans ce
		 * message. Map contenant, pour chaque index de site (clé), son aptitude
		 * (valeur)
		 *
		 * @return la map des aptitudes.
		 */
		public Map<Byte, Integer> getAptitudes() {
			return aptitudes;
		}

		/**
		 * Permet de connaitre le type du message courant
		 *
		 * @return un type de message ANNOUNCE
		 */
		@Override
		protected MessageType getMessageType() {
			return MessageType.ANNOUNCE;
		}

		/**
		 * Permet de récupérer la liste de tous les bytes formant ce message
		 * (sérialisation)
		 *
		 * @return la liste de tous les bytes
		 */
		@Override
		public List<Byte> toByteList() {
			// On appelle la méthode toByteList parente. Celle-ci nous retourne
			// une liste dont le premier byte est dors-et-déjà le type du message
			List<Byte> bytes = super.toByteList();

			// Pour chaque aptitudes de ce message (siteIndex:aptitude)
			aptitudes.entrySet().stream().map((entry) -> {
				// On ajoute l'index de l'hôte à la liste des bytes
				Byte hostIndex = entry.getKey();
				bytes.add(hostIndex);
				// On transmet l'aptitude plus loin
				Integer aptitude = entry.getValue();
				return aptitude;
				// On convertit l'aptitude en tableau de byte
			}).map((aptitude) -> ByteIntConverter.intToByte(aptitude))
				// On ajoute chacun des bytes à la liste
				.forEachOrdered((app) -> {
					for (byte b : app) {
						bytes.add(b);
					}
				});

			return bytes;
		}

	}

	/**
	 * Un RESULT est utilisé à la fin de l'élection, lorsque l'élu a été choisis.
	 *
	 * Lorsqu'un site détecte la fin de la phase de votation à la réception d'un
	 * message de type ANNONCE, et donc que l'élu peut être désigné unanimement,
	 * le site arrête la diffusion des messages annonces et envoi un message de
	 * type RESULTS au suivant. Ce message contenant l'élu, tous les sites
	 * obtiendront le même élu.
	 *
	 * Chacun des sites prennent tour à tour compte de ce message, en s'y
	 * ajoutant en tant que site l'ayant vu.
	 *
	 * En tant normal, le site ayant initié la propagation de ce message est le
	 * premier a re-recevoir ce message. Il peut le détecter car il est lui-même
	 * dans la liste des sites qui ont vu ce message. Vu qu'il le reçoit pour la
	 * deuxième fois, tous les sites ont vu ce message: Il peut donc stopper la
	 * propagation de ce message, et obtenir l'élu
	 *
	 * Un message de type results est formé de d'abord le type de message, puis
	 * de l'index de l'hôte élu (byte), puis des sites participant
	 * |type|electedHostIndex|index3|index4|...
	 */
	public static class ResultsMessage extends Message {

		// Le numéro de site de l'élu
		private final byte electedIndex;

		// La liste des sites ayant vu ce message. Utile lors d'élection multiple
		private final LinkedList<Byte> seenSites;

		/**
		 * Permet d'instancier un nouveau message de type RESULTS, où l'élu est
		 * passé en paramètre
		 *
		 * @param electedIndex le numéro de site de l'élu
		 */
		public ResultsMessage(byte electedIndex) {
			this.electedIndex = electedIndex;
			this.seenSites = new LinkedList<>();
		}

		/**
		 * Permet d'instancier un nouveau message de type RESULTS à partir d'un
		 * tableau de byte. Dans notre application, ce tableau est reçu depuis le
		 * réseau. Cette méthode se charge de parser le tableau data et d'en faire
		 * un message de type RESULTS
		 *
		 * On doit aussi passer la taille du message car le tableau passer en
		 * paramètre peut être plus grand que le message réel (data[] est enfaite
		 * un buffer souvant non remplis complètement)
		 *
		 * @param data
		 * @param size
		 */
		public ResultsMessage(byte[] data, int size) {
			// Le numéro de l'élu est au 2ème byte, le premier étant le type du
			// message
			this.electedIndex = data[1];

			// Liste des sites ayant vu ce message
			this.seenSites = new LinkedList<>();
			for (int i = 2; i < size; i++) {
				seenSites.add(data[i]);
			}
		}

		/**
		 * Ajoute un site à la liste des sites ayant vu passer ce message. Utile
		 * si on souhaite détecter des erreurs (élections multiples, pannes de
		 * sites, etc..)
		 *
		 * @param hostIndex
		 */
		public void addSeenSite(byte hostIndex) {
			this.seenSites.add(hostIndex);
		}

		/**
		 * Permet de connaitre le type du message courant
		 *
		 * @return un type de message RESULTS
		 */
		@Override
		protected MessageType getMessageType() {
			return MessageType.RESULTS;
		}

		/**
		 * Permet de récupérer la liste de tous les bytes formant ce message
		 * (sérialisation)
		 *
		 * @return la liste de tous les bytes
		 */
		@Override
		public List<Byte> toByteList() {
			// On appelle la méthode toByteList parente. Celle-ci nous retourne
			// une liste dont le premier byte est dors-et-déjà le type du message
			List<Byte> bytes = super.toByteList();
			
			// On y ajoute l'élu
			bytes.add(electedIndex);
			
			// On y ajoute les sites vu
			seenSites.forEach((s) -> bytes.add(s));
			
			return bytes;
		}

		/**
		 * @return le numéro de site élu
		 */
		public byte getElectedIndex() {
			return electedIndex;
		}

		/**
		 * La liste des sites (byte) ayant reçu et vu ce message
		 * @return la liste des sites
		 */
		public LinkedList<Byte> getSeenSites() {
			return seenSites;
		}

	}

	/**
	 * Message "répondu" à la réception de message. Il permet de confirmer la
	 * bonne réception d'un message d'un destinataire. Lorsqu'on envoi un message
	 * à un destinataire, on peut donc : 1) L'envoyer 2) Attendre la réception
	 * d'une quittance 2.1) Si pas de quittance au bout de 's' secondes, le
	 * destinataire n'est donc pas atteignable.
	 *
	 * Dans notre application, ceci est utile afin de vérifier si notre voisin
	 * est up. Dans le cas contraire, on essaie en envoyant au voisin de notre
	 * voisin, etc.
	 *
	 * Un message de type quittance n'a aucune donnée spécifique. Pas besoin de
	 * stocker le numéro du site l'envoyant (cf méthode sendQuittancedMessage()
	 * de la classe ElectionManager)
	 *
	 * Ce message est formé de la sorte: |type|
	 */
	public static class QuittanceMessage extends Message {

		/**
		 * Permet de connaitre le type du message courant
		 *
		 * @return un type de message QUITTANCE
		 */
		@Override
		protected MessageType getMessageType() {
			return MessageType.QUITTANCE;
		}
	}

	/**
	 * Un message de type echo permet de vérifier si l'élu est toujours up.
	 *
	 * Lorsque l'ElectionManager reçoit un END, il le quittance. Le client ayant
 voulu vérifier l'état du site reçoit donc un message de type QUITTANCE Il
 Ce message est formé de la sorte: |type|
	 */
	public static class EndMessage extends Message {

		/**
		 * Permet d'indiquer la terminaison
		 *
		 * @return un type de message END
		 */
		@Override
		protected MessageType getMessageType() {
			return MessageType.END;
		}
	}

}
