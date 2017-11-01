package PTP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import static PTP.Protocol.*;
import static PTP.Protocol.MessageStruct.*;
import static PTP.Protocol.MessageType.*;

/**
 * Représente un maître PTP, dont l'heure est envoyée à des esclaves sur le réseau,
 * qui se synchroniseront
 *
 * @author Miguel Pombo Dias
 * @author Rémi Jacquemard
 */
public class MasterPTP {

	private boolean toContinue = true; // Afin de pouvoir arrêter les threads

	// Timer lancé tous les 'Protocol.SYNC_PERIOD' 
	// On y diffuse les sync et les follow up en multicast
	private final Timer syncTimer = new Timer();
	private TimerTask syncTask = new TimerTask() {

		// L'id en cours envoyé par paquet
		private byte id = 0;

		/* 
		 * On crée un multicastSocket. Le port de sortie ne nous importe pas, 
		 * ce socket n'étant utilisé que pour de l'envoi de packet
		 */
		private MulticastSocket broadcastSocket = new MulticastSocket();
		private InetAddress group = InetAddress.getByName(GROUP_ADDRESS);

		@Override
		public void run() {
			try {
				// ---------------- SYNC - envoi - {SYNC, id}
				System.out.println("Sending sync");
				byte[] syncData = {SYNC.asByte(), id};

				DatagramPacket packet = new DatagramPacket(syncData, syncData.length, group, SYNC_PORT);

				// Temps du système courant envoyé aux esclave, sous forme de long
				//*
				long time = System.currentTimeMillis();
				/*/ Afin de simuler un temps différent sur le master que sur le slave
				long time = System.currentTimeMillis() + 10000;
				//*/
				broadcastSocket.send(packet);

				System.out.println("Sync sent");

				// ---------------- FOLLOW_UP - envoi - {FOLLOW_UP, id, time }
				System.out.println("Sending follow_up");

				byte[] followUpData = ByteBuffer.allocate(2 + Long.BYTES).put(FOLLOW_UP.asByte()).put(id).putLong(time).array();
				packet = new DatagramPacket(followUpData, followUpData.length, group, SYNC_PORT);
				broadcastSocket.send(packet);
				System.out.println("Follow_up sent (" + id + ")");

				// On incrémente l'id pour le prochain sync-followUp
				id++;

			} catch (IOException ex) {
				Logger.getLogger(MasterPTP.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	};

	/**
	 * Thread s'occupant des requêtes entrante DELAY_REQUEST et répondant à l'esclave
	 * en point-à-point
	 */
	private final Thread delayRequestThread = new Thread(new Runnable() {

		/**
		 * On crée un datagramSocket qui écoutera les requêtes entrantes sur le port
		 * DELAY_PORT.
		 */
		DatagramSocket socket = new DatagramSocket(DELAY_PORT);

		@Override
		public void run() {
			try {
				do {
					// ---------------- DELAY_REQUEST - réception - {DELAY_REQ, id}

					// Création du paquet entrant
					byte[] delayRequestBuffer = new byte[2];
					DatagramPacket packet = new DatagramPacket(delayRequestBuffer, 2);

					// Attente du paquet
					System.out.println("Waiting for delay request...");
					socket.receive(packet); //Bloquant

					//Pour simuler un grand délai, décommenter:
					/*
					try {
						Thread.sleep(2000);
					} catch (InterruptedException ex) {
						Logger.getLogger(MasterPTP.class.getName()).log(Level.SEVERE, null, ex);
					}
					//*/
					// On lit le temps à la réception du message
					long time = System.currentTimeMillis();

					// ---------------- DELAY_RESPONSE - envoi - {DELAY_RES, id}
					// Parsing du packet DELAY_REQUEST 
					MessageType type = MessageType.values()[packet.getData()[TYPE.ordinal()]];

					// On ignore les paquets si erreur de protocol, et on recommence
					if (type == DELAY_REQUEST && packet.getLength() == 2) {
						System.out.println("Delay request received");
						InetAddress address = packet.getAddress();
						int port = packet.getPort();
						byte id = packet.getData()[ID.ordinal()];

						// --> Création du packet DELAY_RESPONSE
						byte[] delayResponseBuffer = ByteBuffer.allocate(2 + Long.BYTES)
								.put(DELAY_RESPONSE.asByte())
								.put(id)
								.putLong(time).array();
						packet = new DatagramPacket(delayResponseBuffer, delayResponseBuffer.length, address, port);
						socket.send(packet); //Envoi du paquet
						System.out.println("Delay response sent");
					}

				} while (toContinue);

			} catch (IOException ex) {
				Logger.getLogger(MasterPTP.class.getName()).log(Level.SEVERE, null, ex);
			}

		}
	});

	public MasterPTP() throws IOException {
		//Diffusion des messages sync et followup
		syncTimer.scheduleAtFixedRate(syncTask, 0, SYNC_PERIOD);

		//Attente des delay_request
		delayRequestThread.start();
	}

	/**
	 * Permet de fermer les connexions
	 */
	public synchronized void close() {
		toContinue = false;
	}

	public static void main(String... args) throws IOException {
		MasterPTP masterPTP = new MasterPTP();
	}

}
