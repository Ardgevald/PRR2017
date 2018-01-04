/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
import util.ByteIntConverter;

/**
 *
 * @author Remi
 */
public class ElectionManager implements Closeable {

	private LinkedList<Site> ring;
	private Site[] hosts;
	private final Site localSite;
	private final int localHostIndex;
	private Site neighbor;
	private Site elu = null;
	

	private DatagramSocket socket;
	
	/**
	 * Un message de type annonce est formé de d'abord le type de message, puis
	 * des aptitudes (int) de chacun :
	 * |TYPE|APT1|APT1|APT1|APT1|APT2|APT2|APT2|APT2|...
	 */
	private int getAnnounceSize(){
		return 1 + Integer.BYTES * hosts.length;
	}

	private boolean isAnnouncing = false;

	private static enum MessageType {
		ANNOUNCE, RESULTS;

		public byte getByte() {
			return (byte) this.ordinal();
		}

		public static MessageType getMessageType(byte b) {
			return MessageType.values()[b];
		}
	}

	private Thread electionListener = new Thread(() -> {
		while (true) {
			try {
				DatagramPacket packet = new DatagramPacket(new byte[getAnnounceSize()], getAnnounceSize());

				// We are waiting for an income packet
				socket.receive(packet);

				MessageType messageType = MessageType.getMessageType(packet.getData()[0]);

				switch (messageType) {
					case ANNOUNCE:
						break;
					case RESULTS:
						break;
				}

			} catch (IOException e) {

			}
		}
	});

	private int evaluateAptitude() {
		return socket.getLocalAddress().getAddress()[3] + socket.getLocalPort();
	}

	/**
	 * Main constructor
	 * @param hosts
	 * @param hostIndex
	 * @throws SocketException 
	 */
	public ElectionManager(Site[] hosts, int hostIndex) throws SocketException {
		this.hosts = hosts;
		this.localSite = hosts[hostIndex];
		this.localHostIndex = hostIndex;

		// Getting the neighbor
		neighbor = hosts[hostIndex + 1 % hosts.length];
		
		// Creating the main socket
		socket = new DatagramSocket(localSite.socketAddress);

		// Launching the listening thread
		electionListener.start();

	}

	public ElectionManager(String[][] hosts, int hostIndex) throws SocketException {
		this(Arrays.stream(hosts)
			.map((s) -> new Site(s[0], Integer.parseInt(s[1])))
			.toArray(Site[]::new), hostIndex);
	}

	public ElectionManager(int hostIndex) throws IOException {
		// Retreiving the other hosts from the hosts.txt file;
		this(Files.readAllLines(Paths.get("hosts.txt")).stream()
			.map((s) -> s.split(" "))
			.toArray(String[][]::new), hostIndex);
	}

	public void startElection() throws IOException {		
		//-- Preparing the message 
		byte[] message = new byte[getAnnounceSize()];		
		
		message[0] = MessageType.ANNOUNCE.getByte();
		addAptitudeToMessage(message);
		
		// Sending it
		DatagramPacket packet = new DatagramPacket(message, message.length, neighbor.socketAddress);
		socket.send(packet);
		
		isAnnouncing = true;
	}

	private void addAptitudeToMessage(byte[] message) {
		// Setting aptitude
		byte[] aptitude =  ByteIntConverter.intToByte(evaluateAptitude());
		for(int i = 0; i < aptitude.length; i++){
			message[1 + localHostIndex * (aptitude.length)] = aptitude[i];
		}
	}

	@Override
	public void close() throws IOException {
		socket.close();
		// TODO arrêter la boucle
	}

	public static class Site {

		private String ip;
		private SocketAddress socketAddress;

		public Site(String ip, int port) {
			socketAddress = new InetSocketAddress(ip, port);
		}

	}
}
