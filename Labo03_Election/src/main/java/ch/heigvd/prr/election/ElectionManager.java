package ch.heigvd.prr.election;

import ch.heigvd.prr.election.Message.AnnounceMessage;
import ch.heigvd.prr.election.Message.MessageType;
import ch.heigvd.prr.election.Message.QuittanceMessage;
import ch.heigvd.prr.election.Message.ResultsMessage;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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

	private static final int QUITTANCE_TIMEOUT = 1000;

	private LinkedList<Site> ring;
	private Site[] hosts;
	private final Site localSite;

	// hostIndex en byte, vu qu'il n'y en a maximum que 4
	private final byte localHostIndex;
	private Site neighbor;
	private Site elected = null;

	private DatagramSocket socket;

	private Object locker = new Object();

	private void log(String s) {
		System.out.println(String.format("%s (%s:%d): %s",
			"ElectionManager",
			localSite.getSocketAddress().getAddress().getHostAddress(),
			localSite.getSocketAddress().getPort(),
			s));
	}

	/**
	 * Main constructor
	 *
	 * @param hosts
	 * @param hostIndex
	 * @throws SocketException
	 */
	public ElectionManager(Site[] hosts, byte hostIndex) throws SocketException, IOException {
		this.hosts = hosts;
		this.localSite = hosts[hostIndex];
		this.localHostIndex = hostIndex;

		log("Starting Election Manager");

		log("Creating DatagramSocket");
		// Creating the main socket
		socket = new DatagramSocket(localSite.getSocketAddress());

		this.electionListener = new Thread(() -> {
			while (true) {
				try {
					DatagramPacket packet = new DatagramPacket(new byte[getAnnounceSize()], getAnnounceSize());

					// We are waiting for an income packet
					log("Waiting for entering packet");

					Message message = receiveMessage();
					MessageType messageType = message.getMessageType();

					// On transmet la quittance
					QuittanceMessage quittanceMessage = new QuittanceMessage();
					sendMessage(quittanceMessage, packet.getSocketAddress());
					
					// On gère le message reçu
					synchronized (locker) {
						switch (messageType) {
							case ANNOUNCE:
								log("ANNOUNCE received");
								AnnounceMessage announceMessage = (AnnounceMessage) message;
								if (isAnnouncing) {
									log("2nd time I received this message");
									// On recoit pour la deuxième fois l'annonce, on doit chercher l'élu

									for (int i = 0; i < hosts.length; i++) {
										hosts[i].setApptitude(announceMessage.getApptitude(i));
									}

									// On utilise la comparaison native des sites
									elected = Arrays.stream(hosts)
										.sorted()
										.findFirst()
										.get();

									// Si on est l'élu, on doit envoyer le résultat
									if (elected == localSite) {
										log("I am the one, sending results");

										ResultsMessage result = new ResultsMessage(getSiteIndex(elected));
										sendQuittancedMessageToNext(result);

										locker.notifyAll();
									} else { // On doit retransmettre le message
										log("We are not the one, retransmitting message");
										sendQuittancedMessageToNext(message);
									}
								} else { // On recoit pour la première fois le message
									log("Updating apptitude and transmitting further");
									// On met à jour la liste
									announceMessage.setApptitude(localHostIndex, computeLocalApptitude());

									// On le retransmet
									sendQuittancedMessageToNext(announceMessage);

									isAnnouncing = true;
								}
								break;
							case RESULTS:
								log("RESULTS received");
								ResultsMessage resultsMessage = (ResultsMessage)message;
								elected = hosts[resultsMessage.getElectedIndex()];
								isAnnouncing = false;

								// On arrête le message quand on est l'élu (qu'on a donc
								// envoyé le message result en premier) et qu'on le reçoit
								// Sinon, on le retransmet
								if (elected != localSite) {
									log("I am not the one, retransmitting");
									sendQuittancedMessageToNext(resultsMessage);
									locker.notifyAll();
								} else {
									log("I am the one, stopping message propagation");
								}
								break;
							case ECHO:
								log("ECHO RECEIVED");
								// On ne fait rien, la quittance a déjà été envoyée
								break;
						}
					}

				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		});

		// Launching the listening thread
		electionListener.start();

		// Starting the election
		this.startElection();
	}

	public ElectionManager(String[][] hosts, byte hostIndex) throws SocketException, IOException {
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

	/*
	private synchronized void sendResult(Site to) throws IOException {
		byte[] data = new byte[2];

		data[0] = MessageType.RESULTS.getByte();
		data[1] = getSiteIndex(elected);

		DatagramPacket packet = new DatagramPacket(data, data.length, to.socketAddress);
		socket.send(packet);
		log("Results sent");
	}//*/
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

	private int computeLocalApptitude() {
		return socket.getLocalAddress().getAddress()[3] + socket.getLocalPort();
	}

	/*
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
	}//*/
	public void startElection() throws IOException {
		synchronized (locker) {
			if (isAnnouncing) {
				log("Starting an election, but an election is already running");
				return;
			}

			//Thread t = new Thread(() -> {
			try {
				log("Starting an election");
				// Setting basic values
				isAnnouncing = true;

				// Getting the neighbor
				neighbor = hosts[(getSiteIndex(localSite) + 1) % hosts.length];

				//-- Preparing the message
				AnnounceMessage announceMessage = new AnnounceMessage(hosts.length);
				announceMessage.setApptitude(localHostIndex, computeLocalApptitude());

				sendQuittancedMessageToNext(announceMessage);
			} catch (IOException ex) {
				Logger.getLogger(ElectionManager.class.getName()).log(Level.SEVERE, null, ex);
			}

			//});
			//t.start();
			log("Announced message sent");
		}
	}

	private void addAptitudeToMessage(byte[] message) {
		// Setting aptitude
		byte[] aptitude = ByteIntConverter.intToByte(computeLocalApptitude());
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

	public Site getElected() throws InterruptedException {
		log("Someone want to get the chosen one");
		// Waiting if there is currently an election to get the new site
		synchronized (locker) {
			if (isAnnouncing) {
				log("Waiting for the elected site");
				locker.wait();
				log("Locker released to get the elected site");
			}
		}

		return elected;
	}

	private synchronized void sendQuittancedMessageToNext(Message message) throws IOException {
		boolean unreachable = false;
		do {
			try {
				sendQuittancedMessage(message, neighbor);
			} catch (UnreachableRemoteException ex) {
				unreachable = true;
			}
		} while (unreachable);

	}

	private synchronized void sendMessage(Message message, SocketAddress socketAddress) throws IOException {
		DatagramPacket packet = new DatagramPacket(message.toByteArray(), message.toByteArray().length, socketAddress);
		socket.send(packet);
	}

	private synchronized void sendMessage(Message message, Site site) throws IOException {
		sendMessage(message, site.getSocketAddress());
	}

	private synchronized void sendQuittancedMessage(Message message, Site site) throws IOException, UnreachableRemoteException {
		sendMessage(message, site);

		try {
			Message m = receiveTimeoutMessage();
			if (m.getMessageType() == Message.MessageType.QUITTANCE) {
				return;
			} else {
				throw new UnreachableRemoteException();
			}
		} catch (SocketTimeoutException e) {
			// Si on atteint pas le site
			throw new UnreachableRemoteException(e);
		}

	}

	private synchronized Message receiveMessage() throws IOException {
		int maxSize = Message.getMaxMessageSize(hosts.length);
		DatagramPacket packet = new DatagramPacket(new byte[maxSize], maxSize);
		socket.receive(packet);

		return Message.parse(packet.getData());
	}

	private synchronized Message receiveTimeoutMessage() throws IOException, SocketTimeoutException {
		socket.setSoTimeout(QUITTANCE_TIMEOUT);
		Message m = receiveMessage();
		socket.setSoTimeout(0);
		return m;
	}

	private static class UnreachableRemoteException extends Exception {

		public UnreachableRemoteException() {
			super();
		}

		public UnreachableRemoteException(Exception e) {
			super(e);
		}
	}

}
