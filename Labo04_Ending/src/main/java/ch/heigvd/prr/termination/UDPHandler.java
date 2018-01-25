package ch.heigvd.prr.termination;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe permettant de gérer les entrées/sorties réseau UDP
 *
 * Cette classe travaille au niveau bytes et DatagramPacket. Il n'y a donc pas
 * de notion de classe Message ici.
 *
 */
public class UDPHandler implements Closeable {

	// Socket permettant d'envoyer et de recevoir des packets UDP
	private DatagramSocket socket;

	/**
	 * Constructeur utilisé en interne permettant de créer un nouvel UDPHandler
	 *
	 * @param socket le socket à utiliser
	 * @param maxMessageSize la taille maximal des message
	 */
	private UDPHandler(DatagramSocket socket, int maxMessageSize) {
		this.socket = socket;
		this.listenerThread = new Thread(new UDPReceiver(maxMessageSize));
		this.listenerThread.start();
	}

	/**
	 * Constructeur permettant d'instancier cette classe. Lie sur le port/adress
	 * précisé en paramètre
	 *
	 * @param address l'adresse sur laquel on souhaite écouter les paquets
	 * entrant
	 * @param maxMessageSize la taille maximal des message à attendre
	 * @throws SocketException si il y a une problème lors de la création du
	 * socket (l'adresse est-elle déjà utilisée ?)
	 */
	public UDPHandler(InetSocketAddress address, int maxMessageSize)
      throws SocketException {
		this(new DatagramSocket(address), maxMessageSize);
	}

	/**
	 * Constructeur permettant d'instancier cette classe. Lie sur un port libre
	 *
	 * @param maxMessageSize la taille maximal des message à attendre
	 * @throws SocketException si il y a eu une erreur lors de la créaiton du
	 * socket associé à cette classe
	 */
	public UDPHandler(int maxMessageSize) throws SocketException {
		this(new DatagramSocket(), maxMessageSize);
	}

	/**
	 * Permet de fermer les ressources relatives à cette classe (fermeture des
	 * sockets, etc.)
	 *
	 * @throws IOException si il y eu un problème lors de la fermeture
	 */
	@Override
	public synchronized void close() throws IOException {
		if (!this.socket.isClosed()) {
			this.socket.close();
		}
	}

	// ------------- LISTENER----------
	// On trouvera dans cette section le nécessaire à un gestionnaire d'évenement
	 
	/**
	 * Liste des classes écoutant les paquets entrants de ce UDPHandler On
	 * notifiera chaque classe de cette liste lorsqu'un paquet a été reçu
	 */
	private final List<UDPListener> listeners = new ArrayList<>();

	/**
	 * Permet d'ajouter un listener à cette classe. Ces listeners seront informé
	 * de chaque paquets UDP reçu.
	 *
	 * @param listener le listener à ajouter
	 */
	public void addListener(UDPListener listener) {
		this.listeners.add(listener);
	}

	/**
	 * Interface à implémenter par les classes souhaitant être informée de la
	 * venue de nouveau packet UDP
	 */
	public interface UDPListener {

		/**
		 * Méthode appelée lorsqu'un paquet a été recu
		 *
		 * @param data les bytes reçu du paquet
		 * @param length la longueur en byte du paquet
		 */
		public void dataReceived(byte[] data, int length);
	}

	// Thread écoutant les paquets UDP entrant
	private Thread listenerThread;

	/**
	 * Classe utilisée en interne. Runnable, elle est utilisée afin d'écouter les
	 * paquets entrants dans un thread
	 */
	private class UDPReceiver implements Runnable {

		// La taille maximale des messages
		private final int maxMessageSize;

		/**
		 * Crée un nouvel UDPReceiver
		 *
		 * @param maxMessageSize la taille maximal des messages
		 */
		public UDPReceiver(int maxMessageSize) {
			this.maxMessageSize = maxMessageSize;
		}

		/**
		 * Boucle principale d'écoute des paquets entrants
		 */
		@Override
		public void run() {
			DatagramPacket packet = new DatagramPacket(
            new byte[maxMessageSize], maxMessageSize);

			do {
				try {
					// Attente d'un paquet entrant
					socket.receive(packet);

					// Emmiting an event
					listeners.forEach(
                  l -> l.dataReceived(packet.getData(),packet.getLength()));
				} catch (SocketException e) {
					// Doing nothing, socket can be closed by others
				} catch (IOException ex) {
					Logger.getLogger(UDPHandler.class.getName())
                  .log(Level.SEVERE, null, ex);
				}
				// On boucle tant que le socket n'est pas fermé.
            // C'est notre condition d'arrêt
			} while (!socket.isClosed());
		}

	}

	// ------------- SENDING MESSAGE
	/**
	 * Permet d'envoyer un nouveau paquet UDP à un site distant
	 *
	 * @param data Les données qu'on souhaite envoyer
	 * @param address L'adresse (ip + port) distante du site à qui envoyer le
	 * message
	 * @throws IOException si il y a eu un problème lors de l'envoi du message
	 */
	public void sendTo(byte[] data, InetSocketAddress address) throws IOException {
		DatagramPacket packet = new DatagramPacket(data, data.length, address);
		this.socket.send(packet);
	}

}
