package PTP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import util.ByteLongConverter;

import static util.Protocol.*;
import static util.Protocol.MessageStruct.*;
import static util.Protocol.MessageType.*;

/**
 * Représente un esclave PTP, dont l'heure est synchronisée sur un maitre sur le
 * réseau
 * @author Miguel Pombo Dias
 * @author Rémi Jacquemard
 */
public class SlavePTP {

	// Ecart de temps entre le maitre et l'esclave, sans prendre en compte
	// le délai de transfert
	// Note : on utilise des Atomic* afin de palier aux problèmes de concurence
	// qui pourrait arriver (lecture en cours d'écriture)
	private AtomicLong gap = new AtomicLong();
	// Délai de transfert d'un message entre le maitre et l'esclave
	private AtomicLong delay = new AtomicLong();

	// Utilisé afin de pouvoir arrêter les threads courant
	private boolean stop;

	// L'id envoyé dans les paquet DELAY_REQUEST
	// Incrémenté après chaque REQUEST-RESPONSE
	private byte delayId = 0;

	// Stocke l'adresse du maitre. Celle-ci est récupérée lors de la réception
	// d'un sync
	private InetAddress masterAddress;
	// Socket d'envoi des DELAY. Le port de sortie n'est pas indiqué car il est tous
	// simplement récupéré du maitre.
	private DatagramSocket unicastSocket = new DatagramSocket();

	// Socket de réception des messages SYNC et FOLLOW_UP
	private MulticastSocket socket;

	/**
	 * Crée un slavePTP qui se synchronisera sur le maitre
	 *
	 * @throws IOException si il y eu une erreur lors d'un transfert
	 */
	public SlavePTP() throws IOException {
		//Diffusion des messages sync et followup
		startThreads();
	}

	/**
	 * Timer utilisé afin d'émettre et de recevoir des DELAY_REQUEST-RESPONSE
	 */
	private final Timer delayTimer = new Timer();

	private class TaskSchedule extends TimerTask {

		@Override
		public void run() {

			try {

				// ---------------- DELAY_REQUEST - envoi - {DELAY_REQ, id}
				System.out.println("sending unicast DELAY_REQUEST");

				// Création du paquet
				byte[] buffer = {DELAY_REQUEST.asByte(), ++delayId};
				DatagramPacket paquet = new DatagramPacket(buffer, buffer.length, masterAddress, DELAY_PORT);
				// On sauvegarde le temps d'envoi du message
				long slaveTime = System.currentTimeMillis();
				System.out.println("sent at time : " + slaveTime);
				// Envoi du paquet
				unicastSocket.send(paquet);

				// ---------------- DELAY_RESPONSE - réception - {DELAY_REQ, id}
				System.out.println("receiving unicast DELAY_RESPONSE");

				// Création du paquet
				buffer = new byte[2 + Long.BYTES];
				paquet = new DatagramPacket(buffer, buffer.length);

				// Réception du paquet
				unicastSocket.receive(paquet);
				MessageType type = MessageType.values()[paquet.getData()[TYPE.ordinal()]];

				// En cas d'erreur de protocole, on ignore simplement ce sync-followUp
				if (type == DELAY_REQUEST && paquet.getData()[ID.ordinal()] == delayId) {
					// On récupère le temps du maitre à partir du paquet
					long masterTime = ByteLongConverter.bytesToLong(Arrays.copyOfRange(paquet.getData(), 2 * Byte.BYTES, paquet.getLength()));
					System.out.println("delay : " + masterTime + " - " + slaveTime);
					// De là, on calcule le délai qu'on a avec le maitre
					delay.set((masterTime - slaveTime) / 2);
				}

				System.out.println("response received");

			} catch (SocketException ex) {
				Logger.getLogger(SlavePTP.class.getName()).log(Level.SEVERE, null, ex);
			} catch (IOException ex) {
				Logger.getLogger(SlavePTP.class.getName()).log(Level.SEVERE, null, ex);
			}

			System.out.println(String.format("DELAY_REQUEST_RESPONSE : gap: %d  delay: %d", gap.get(), delay.get()));

			// On planifie à nouveau cette manipulation pour dans [4k, 60k] secondes
			// Nous aurions pu, plutôt que de relancer cette manipulation après
			// n seconde à l'aide d'un timer, utiliser le fait que le sync est envoyé
			// tous les 'k' temps, et ainsi lancer, cette manipulation
			// après 4 <= n <= 60  SYNC reçu. Nous avons cependant préféré ne pas
			// dépendre de la réception de ces messages, qui pourraient, par exemple,
			// se perdre
			delayTimer.schedule(new TaskSchedule(), (4 + new Random().nextInt(57)) * SYNC_PERIOD);
		}
	};

	/**
	 * Ce thread est utilisé pour recevoir les sync et les follow_up
	 */
	private final Thread sync = new Thread(() -> {
		try {
			waitSync();

			// start delay management
			delayTimer.schedule(new TaskSchedule(), 0);

			// continuer waitSync en continu
			while (!stop) {
				waitSync();
			}

		} catch (IOException ex) {
			Logger.getLogger(SlavePTP.class.getName()).log(Level.SEVERE, null, ex);
		}
	});

	/**
	 * Cette méthode attends sur un sync suivi d'un follow_up, et synchronise le
	 * temps courant avec le maitre
	 *
	 * @throws IOException si il y a eu une erreur d'entrée/sortie
	 */
	private void waitSync() throws IOException {

		// Socket s'abonnant au groupe multicast qui réceptionnera
		// les messages SYNC et FOLLOW_UP
		socket = new MulticastSocket(SYNC_PORT);
		InetAddress group = InetAddress.getByName(GROUP_ADDRESS);
		socket.joinGroup(group);

		// ---------------- SYNC - réception - {SYNC, id}
		// Création du paquet de réception
		byte[] buffer = new byte[2];
		Byte id = null;
		DatagramPacket paquet = new DatagramPacket(buffer, buffer.length);

		do { // Attente d'un paquet SYNC
			socket.receive(paquet);
			MessageType type = MessageType.values()[paquet.getData()[TYPE.ordinal()]];
			if (paquet.getLength() == 2 && type == SYNC) { // Vérification d'un paquet sync
				// On récupère les infos du paquets pour connaitre l'adresse du maitre
				id = paquet.getData()[ID.ordinal()];
				masterAddress = paquet.getAddress();
			}
		} while (id == null);

		// ---------------- FOLLOW_UP - réception - {FOLLOW_UP, id, time }
		// Création du paquet de réception
		buffer = new byte[Long.BYTES + 2 * Byte.BYTES];
		paquet = new DatagramPacket(buffer, buffer.length);
		socket.receive(paquet);
		MessageType type = MessageType.values()[paquet.getData()[TYPE.ordinal()]];
		// On vérifie le paquet
		if (paquet.getLength() == Long.BYTES + 2 * Byte.BYTES
				&& type == FOLLOW_UP
				&& paquet.getData()[ID.ordinal()] == id) { // Doit aussi être le même id

			// On peut calculer en conséquence l'écart à partir du temps du maitre
			byte[] masterTime = Arrays.copyOfRange(paquet.getData(), 2 * Byte.BYTES, paquet.getLength());
			gap.set(ByteLongConverter.bytesToLong(masterTime) - System.currentTimeMillis());
		}

		System.out.println(String.format("SYNC : gap: %d  delay: %d", gap.get(), delay.get()));
	}

	/**
	 * Permet de starter les threads
	 */
	private void startThreads() {
		sync.start();
	}

	/**
	 * @return un long indiquant le temps courant synchronisé avec le maitre
	 */
	public long getTimeSynced() {
		return System.currentTimeMillis() + delay.get() + gap.get();
	}

	/**
	 * Permet d'arrêter les thread courant, si possible
	 */
	public synchronized void close() {
		stop = true;
	}
}
