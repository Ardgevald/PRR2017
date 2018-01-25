package ch.heigvd.prr.termination;

import ch.heigvd.prr.termination.Message;
import ch.heigvd.prr.termination.UDPHandler;
import ch.heigvd.prr.termination.UDPHandler.UDPListener;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe permettant de gérer des entrées/sorties réseau UDP.
 *
 * Cette classe travaille au niveau de Message et Site. Ici, des messages
 * peuvent être envoyé à des sites, et on reçoit des messages à partir du réseau
 *
 */
public class UDPMessageHandler extends UDPHandler implements UDPListener {

	/**
	 * Constructeur permettant d'instancier cette classe. Lie sur le port/adress
	 * précisé en paramètre.
	 *
	 * @param address l'adresse et le port sur lequel écouter
	 * @throws SocketException si il y a une problème lors de la création du
	 * socket (l'adresse est-elle déjà utilisée ?)
	 */
	public UDPMessageHandler(InetSocketAddress address) throws SocketException {
		super(address, Message.getMaxMessageSize());
		super.addListener(this);
	}

	/**
	 * Constructeur permettant d'instancier cette classe. Lie sur un port libre
	 *
	 * @throws SocketException si il y a eu une erreur lors de la créaiton du
	 * socket associé à cette classe
	 */
	public UDPMessageHandler() throws SocketException {
		super(Message.getMaxMessageSize());
	}

	// ------ LISTENER ------------
	// On trouvera dans cette section le nécessaire à un gestionnaire d'évenement
	/**
	 * Liste des classes écoutant les paquets entrants de ce UDPHandler On
	 * notifiera chaque classe de cette liste lorsqu'un paquet a été reçu
	 */
	private final List<UDPMessageListener> listeners = new ArrayList<>();

	/**
	 * Permet d'ajouter un listener à cette classe. Ces listeners seront informé
	 * de chaque Message reçu
	 *
	 * @param listener le listener à ajouter
	 */
	public void addListener(UDPMessageListener listener) {
		this.listeners.add(listener);
	}

	/**
	 * Interface à implémenter par les classes souhaitant être informée de la
	 * venue de nouveau Message
	 */
	public interface UDPMessageListener {

		/**
		 * Méthode appelée lorsqu'un message a été reçu
		 *
		 * @param message le message reçu depuis le réseau
		 */
		public void dataReceived(Message message);
	}

	/**
	 * Méthode appelée par le UDPListener. On traite les DatagramPaquets reçu, et
	 * on en fait des messsages qu'on envoie à nos propre listeners
	 *
	 * @param data Les données du paquets
	 * @param length la longueur du paquet
	 */
	@Override
	public void dataReceived(byte[] data, int length) {
		Message m = Message.parse(data, length);
		// On informe tous les listeners du messages reçu
		listeners.forEach(l -> l.dataReceived(m));
	}

	// ----- SENDING
	/**
	 * Permet d'envoyer un nouveau message à un site distanmte
	 * @param message le message à envoyer
	 * @param address L'adresse (ip + port) distante du site à qui envoyer le
	 * message
	 * @throws IOException si il y a eu un problème lors de l'envoi du message
	 */
	public void sendTo(Message message, InetSocketAddress address)
      throws IOException {
		this.sendTo(message.toByteArray(), address);
	}

	/**
	 * Permet d'envoyer un nouveau message à un site distanmte
	 * @param message le message à envoyer
	 * @param site Le site auxquel envoyer le message
	 * @throws IOException si il y a eu un problème lors de l'envoi du message
	 */
	public void sendTo(Message message, Site site) throws IOException {
		this.sendTo(message, site.getSocketAddress());
	}

}
