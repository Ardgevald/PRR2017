package ch.heigvd.prr.election;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Remi
 */
public class App {

	private ElectionManager electionManager;

	private DatagramSocket socket = new DatagramSocket();

	public App(byte hostIndex) throws IOException {
		log("Creating electionManager");
		electionManager = new ElectionManager(hostIndex);
		log("ElectionManager created");
	}

	public void sendEcho() {
		try {
			DatagramPacket packet = new DatagramPacket(new byte[1], 1, electionManager.getElected().getSocketAddress());

			log("Sending echo");
			socket.send(packet);
			socket.receive(packet);
			log("Echo succesfuly received");
		} catch (SocketException ex) {
			Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
		} catch (IOException ex) {
			Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
		} catch (InterruptedException ex) {
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
		// UTiliser les tryWithRessource ici
		// TODO : Election automatique au démarrage
		
		App[] apps = {
			new App((byte)0), new App((byte)1)
		};
		
		apps[0].sendEcho();
		

	}
}
