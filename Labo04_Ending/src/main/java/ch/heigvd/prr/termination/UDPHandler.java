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
 *
 * @author Remi
 */
public class UDPHandler implements Closeable {

	private DatagramSocket socket;
	private boolean closed = false;

	private UDPHandler(DatagramSocket socket, int maxMessageSize) {
		this.socket = socket;
		this.listenerThread = new Thread(new UDPReceiver(maxMessageSize));
		this.listenerThread.start();
	}

	/**
	 * Lie sur le port/adress précisé en paramètre
	 *
	 * @param address
	 * @throws SocketException
	 */
	public UDPHandler(InetSocketAddress address, int maxMessageSize) throws SocketException {
		this(new DatagramSocket(address), maxMessageSize);
	}

	/**
	 * Lie sur un port libre
	 *
	 * @throws SocketException
	 */
	public UDPHandler(int maxMessageSize) throws SocketException {
		this(new DatagramSocket(), maxMessageSize);
	}

	@Override
	public synchronized void close() throws IOException {
		if (!this.closed) {
			this.closed = true;

			this.socket.close();
		}
	}

	// ------------- LISTENER
	private Thread listenerThread;

	private class UDPReceiver implements Runnable {

		private final int maxMessageSize;

		public UDPReceiver(int maxMessageSize) {
			this.maxMessageSize = maxMessageSize;
		}

		@Override
		public void run() {
			DatagramPacket packet = new DatagramPacket(new byte[maxMessageSize], maxMessageSize);

			do {
				try {
					socket.receive(packet);

					// Emmiting an event
					listeners.forEach(l -> l.dataReceived(packet.getData(), packet.getLength()));
				} catch (SocketException e) {
					// Doing nothing, socket can be closed by others
				} catch (IOException ex) {
					Logger.getLogger(UDPHandler.class.getName()).log(Level.SEVERE, null, ex);
				}

			} while (!closed);
		}

	}

	private List<UDPListener> listeners = new ArrayList<>();

	public interface UDPListener {

		public void dataReceived(byte[] data, int length);
	}

	public void addListener(UDPListener listener) {
		this.listeners.add(listener);
	}

	// ------------- SENDING MESSAGE
	public void sendTo(byte[] data, InetSocketAddress address) throws IOException {
		DatagramPacket packet = new DatagramPacket(data, data.length, address);
		this.socket.send(packet);
	}

}
