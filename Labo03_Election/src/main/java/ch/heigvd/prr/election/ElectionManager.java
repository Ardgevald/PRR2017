/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.heigvd.prr.election;

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

/**
 *
 * @author Remi
 */
public class ElectionManager {

	private LinkedList<Site> ring;
	private Site[] hosts;
	private final Site localSite;
	private Site elu = null;

	private DatagramSocket socket;
	
	private boolean isAnnouncing = false;
	private static enum MessageType{
		ANNOUNCE, RESULTS;
		
		public byte getByte(){
			return (byte)this.ordinal();
		}
		
		public static MessageType getMessageType(byte b){
			return MessageType.values()[b];
		}
	}

	private Thread electionListener = new Thread(() -> {
		while (true) {
			try {
				DatagramPacket packet = new DatagramPacket(new byte[4], 4);
				
				// We are waiting for an income packet
				socket.receive(packet);
				
				MessageType messageType = MessageType.getMessageType(packet.getData()[0]);
				
				switch(messageType){
					case ANNOUNCE:
						break;
					case RESULTS:
						break;
				}
								
				
			} catch (IOException e) {

			}
		}
	});

	private int evaluateAptitude(){
		return socket.getLocalAddress().getAddress()[3] + socket.getLocalPort();
	}
	
	public ElectionManager(Site[] hosts, int hostIndex) throws SocketException {
		this.hosts = hosts;
		this.localSite = hosts[hostIndex];
		
		// Creating the main socket
		socket = new DatagramSocket(new InetSocketAddress(localSite.ip, localSite.port));

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

	public void startElection() {

	}

	public static class Site {

		private String ip;
		private int port;
		private int aptitude;

		public Site(String ip, int port) {
			this.ip = ip;
			this.port = port;
		}

	}
}
