package ch.heigvd.prr.election;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import util.ByteIntConverter;

/**
 *
 * @author Remi
 */
public class ElectionManager implements Closeable {

	private LinkedList<Site> ring;
	private Site[] hosts;
	private final Site localSite;

	// hostIndex en byte, vu qu'il n'y en a maximum que 4
	private final byte localHostIndex;
	private Site neighbor;
	private Site elected = null;

	private DatagramSocket socket;

	private void log(String s) {
		System.out.println(String.format("%s (%s:%d): %s",
			"ElectionManager",
			localSite.socketAddress.getAddress().getHostAddress(),
			localSite.socketAddress.getPort(),
			s));
	}

	/**
	 * Main constructor
	 *
	 * @param hosts
	 * @param hostIndex
	 * @throws SocketException
	 */
	public ElectionManager(Site[] hosts, byte hostIndex) throws SocketException {
		this.hosts = hosts;
		this.localSite = hosts[hostIndex];
		this.localHostIndex = hostIndex;

		log("Starting Election Manager");

		// Getting the neighbor
		neighbor = hosts[(hostIndex + 1) % hosts.length];

		System.out.println(localSite.socketAddress.getAddress().getHostAddress());

		log("Creating DatagramSocket");
		// Creating the main socket
		socket = new DatagramSocket(localSite.socketAddress);

		this.electionListener = new Thread(() -> {
			while (true) {
				try {
					DatagramPacket packet = new DatagramPacket(new byte[getAnnounceSize()], getAnnounceSize());

					// We are waiting for an income packet
					log("Waiting for entering packet");
					socket.receive(packet);

					byte[] data = packet.getData();

					MessageType messageType = MessageType.getMessageType(data[0]);

					switch (messageType) {
						case ANNOUNCE:
							log("ANNOUNCE received");
							if (isAnnouncing) {
								log("2nd time I received this message");
								// On recoit pour la deuxième fois l'annonce, on doit chercher l'élu
								elected = getBestSite(data);

								// Si on est l'élu, on doit envoyer le résultat
								if (elected == localSite) {
									log("I am the one, sending results");
									sendResult(neighbor);
								} else { // On doit retransmettre le message
									log("We are not the one, retransmitting message");
									packet.setSocketAddress(neighbor.socketAddress);
									socket.send(packet);
								}
							} else { // On recoit pour la première fois le message
								log("Updating apptitude and transmitting further");
								// On met à jour la liste
								addAptitudeToMessage(data);

								// On le retransmet
								packet.setData(data);
								socket.send(packet);

								isAnnouncing = true;
							}
							break;
						case RESULTS:
							log("RESULTS received");
							elected = hosts[data[1]];
							isAnnouncing = false;

							// On arrête le message quand on est l'élu (qu'on a donc
							// envoyé le message result en premier) et qu'on le reçoit
							// Sinon, on le retransmet
							if (elected != localSite) {
								log("I am not the one, retransmitting");
								socket.send(packet);
							}else{
								log("I am the one, stopping message propagation");
							}
							break;
					}

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		// Launching the listening thread
		electionListener.start();

	}

	public ElectionManager(String[][] hosts, byte hostIndex) throws SocketException {
		this(Arrays.stream(hosts)
			.map((s) -> new Site(s[0], Integer.parseInt(s[1])))
			.toArray(Site[]::new), hostIndex);
	}

	public ElectionManager(byte hostIndex) throws IOException {
		// Retreiving the other hosts from the hosts.txt file;
		this(Files.readAllLines(Paths.get("hosts.txt")).stream()
			.map((s) -> s.split(" "))
			.toArray(String[][]::new), hostIndex);
	}

	/**
	 * Un message de type annonce est formé de d'abord le type de message, puis
	 * des aptitudes (int) de chacun :
	 * |TYPE|APT1|APT1|APT1|APT1|APT2|APT2|APT2|APT2|...
	 */
	private int getAnnounceSize() {
		return 1 + Integer.BYTES * hosts.length;
	}

	private boolean isAnnouncing = false;

	private void sendResult(Site to) throws IOException {
		byte[] data = new byte[2];

		data[0] = MessageType.RESULTS.getByte();
		data[1] = getSiteIndex(elected);

		DatagramPacket packet = new DatagramPacket(data, data.length, to.socketAddress);
		socket.send(packet);
		log("Results sent");
	}

	private static enum MessageType {
		/**
		 * Un message de type annonce est formé de d'abord le type de message,
		 * puis des aptitudes (int) de chacun :
		 * |TYPE|APT1|APT1|APT1|APT1|APT2|APT2|APT2|APT2|...
		 */
		ANNOUNCE,
		/**
		 * Un message de type results est formé de d'abord le type de message,
		 * puis de l'index de l'hôte élu (byte) : |TYPE|INDEX|...
		 */
		RESULTS;

		public byte getByte() {
			return (byte) this.ordinal();
		}

		public static MessageType getMessageType(byte b) {
			return MessageType.values()[b];
		}
	}

	private byte getSiteIndex(Site site) {
		for (int i = 0; i < hosts.length; i++) {
			if (hosts[i] == site) {
				return (byte) i;
			}
		}

		// Nothing has been found here
		throw new IllegalArgumentException("The site passed in parameters could not be found");
	}

	private Thread electionListener;

	private int evaluateAptitude() {
		return socket.getLocalAddress().getAddress()[3] + socket.getLocalPort();
	}

	private Site getBestSite(byte[] data) {
		log("Calculating who is the best Site");
		int bestApptitude = 0;
		Site bestSite = null;

		for (int i = 0; i < hosts.length; i++) { // For each site
			Site curSite = hosts[i];
			// Getting the aptitude
			byte[] aptBytes = new byte[Integer.BYTES];
			for (int j = 0; j < aptBytes.length; j++) {
				aptBytes[j] = data[1 + i * (aptBytes.length)];
			}

			int curApptitude = ByteIntConverter.bytesToInt(aptBytes);

			if (curApptitude >= bestApptitude) {
				if (bestSite == null) {
					bestApptitude = curApptitude;
					bestSite = curSite;
				} else {
					// Checking for equality
					byte[] bestSiteAddress = bestSite.socketAddress.getAddress().getAddress();
					byte[] curSiteAddress = curSite.socketAddress.getAddress().getAddress();

					for (int j = 0; j < bestSiteAddress.length; j++) {
						if (bestSiteAddress[j] < curSiteAddress[j]) {
							bestApptitude = curApptitude;
							bestSite = curSite;
							break;
						}
					}
				}

			}

		}

		return bestSite;
	}

	public void startElection() throws IOException {
		log("Starting an election");
		//-- Preparing the message 
		byte[] message = new byte[getAnnounceSize()];

		message[0] = MessageType.ANNOUNCE.getByte();
		addAptitudeToMessage(message);

		// Sending it
		DatagramPacket packet = new DatagramPacket(message, message.length, neighbor.socketAddress);
		socket.send(packet);

		isAnnouncing = true;
		
		log("Announced message sent");
	}

	private void addAptitudeToMessage(byte[] message) {
		// Setting aptitude
		byte[] aptitude = ByteIntConverter.intToByte(evaluateAptitude());
		for (int i = 0; i < aptitude.length; i++) {
			message[1 + localHostIndex * (aptitude.length)] = aptitude[i];
		}
	}

	@Override
	public void close() throws IOException {
		log("Closing connection");
		socket.close();
		// TODO arrêter la boucle

		log("Everything's closed");
	}

	public static class Site {

		private InetSocketAddress socketAddress;

		public Site(String ip, int port) {
			socketAddress = new InetSocketAddress(ip, port);
		}

	}
}
