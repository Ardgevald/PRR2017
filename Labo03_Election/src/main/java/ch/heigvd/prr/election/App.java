package ch.heigvd.prr.election;

import ch.heigvd.prr.election.Message.EchoMessage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Remi
 */
public class App {

	private static final int ECHO_TIMEOUT = 2000;
	private static final int TIMER_MAX = 10000;

	private ElectionManager electionManager;

	private DatagramSocket socket = new DatagramSocket();

	private Thread sendEchosThread;

	public App(byte hostIndex) throws IOException {
		socket.setSoTimeout(ECHO_TIMEOUT);
		log("Creating electionManager");
		electionManager = new ElectionManager(hostIndex);
		log("ElectionManager created");

		Random random = new Random();
		// De temps en temps, on lance un echo
		sendEchosThread = new Thread(() -> {
			while (true) {
				try {
					synchronized (this) {
						this.wait(random.nextInt(TIMER_MAX));
					}
					sendEcho();
				} catch (InterruptedException ex) {
					Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		});
		sendEchosThread.start();

	}

	public void sendEcho() {
		try {
			EchoMessage message = new EchoMessage();
			byte[] data = message.toByteArray();
			DatagramPacket packet = new DatagramPacket(data, data.length, electionManager.getElected().getSocketAddress());

			log("Sending echo to " + electionManager.getElected().getSocketAddress().getPort());
			socket.send(packet);
			try {
				socket.receive(packet);
				log("Echo succesfuly received");
			} catch (SocketTimeoutException e) {
				// Si on atteint pas le site
				log("Site actially down, starting another election");
				electionManager.startElection();
			}

		} catch (SocketException ex) {
			Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
		} catch (IOException | InterruptedException ex) {
			Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private void log(String s) {
		System.out.println(String.format("%s (%s:%d): %s",
			"App",
			socket.getLocalAddress().getHostAddress(),
			socket.getPort(),
			s));
	}

	public static void main(String... args) throws IOException {
		// TODO UTiliser les tryWithRessource ici

		App[] apps = new App[4];
		for (int i = 0; i < apps.length; i++) {
			apps[i] = new App((byte) i);
		}

	}
}
