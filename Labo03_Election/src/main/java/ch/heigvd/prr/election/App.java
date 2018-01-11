package ch.heigvd.prr.election;

import ch.heigvd.prr.election.Message.EchoMessage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cette classe est l'applicatif de notre programme. Elle permet avant tous
 * d'envoyer des echos à l'élu, et de vérifier si celui-ci est bien up. S'il est
 * down, on doit relancer une élection.
 *
 * Remarquons que cet applicatif est un peu "artificiel". En effet, dans un vrai
 * application, on n'est pas obligé d'interroger le site élu toutes les 'n'
 * secondes. Il suffit que lorsqu'on a besoin de l'élu, on l'interroge. Si
 * celui-ci est down, on lance une nouvelle élection.
 *
 *
 *
 * @author Miguel Pombo Dias
 * @author Rémi Jacquemard
 */
public class App {

	// Timeout maximal d'un ECHO avant de décider que l'élu est down
	private static final int ECHO_TIMEOUT = 2000;

	// Utilisé afin de lancer toutes les TIMER_MAX secondes un echo à l'élu
	private static final int TIMER_MAX = 10000;

	// L'élection manager associé à cet applicatif
	private final ElectionManager electionManager;

	// Le socket utilisé afin d'envoyer les echos
	private final DatagramSocket socket;

	// Le threads lancant les echos toutes les secondes
	private final Thread sendEchosThread;

	/**
	 * Permet d'instancier une nouvelle application
	 *
	 * @param hostIndex l'index du site courant
	 * @throws IOException si il y a eu une erreur lors de l'echo autre que le
	 * fait que le site distant a mis trop de temps à répondre
	 */
	public App(byte hostIndex) throws IOException {
		this.socket = new DatagramSocket();
		socket.setSoTimeout(ECHO_TIMEOUT);
		log("Creating electionManager");

		electionManager = new ElectionManager(hostIndex);
		log("ElectionManager created");

		// On lance une élection
		electionManager.startElection();

		Random random = new Random();
		// De temps en temps, on lance un echo
		sendEchosThread = new Thread(() -> {
			try {
				while (true) {
					synchronized (this) {
						this.wait(random.nextInt(TIMER_MAX));
					}
					sendEcho();
				}
			} catch (InterruptedException ex) {
				Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
			}
		});
	}

	/**
	 * Permet de lancer l'application
	 */
	public void start() {
		sendEchosThread.start();
	}

	/**
	 * Methode permettant d'envoyer des echos à l'élu
	 */
	public void sendEcho() {
		try {
			EchoMessage message = new EchoMessage();
			byte[] data = message.toByteArray();
			Site elected = electionManager.getElected();
			DatagramPacket packet = new DatagramPacket(data, data.length, elected.getSocketAddress());

			log("Sending echo to " + elected.getSocketAddress().getPort());
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

	/**
	 * Méthode utilise permettant de logger un message
	 * @param s le message à logger
	 */
	private void log(String s) {
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

		Date resultdate = new Date(System.currentTimeMillis());
		String time = sdf.format(resultdate);

		System.out.println(String.format("%s - %s (%s:%d): %s",
			time,
			"App",
			socket.getLocalAddress().getHostAddress(),
			socket.getPort(),
			s));
	}

	/**
	 * Un fichier host doit être disponible
	 * @param args args[0] doit être le numéro de site
	 * 
	 * @throws IOException 
	 */
	public static void main(String... args) throws IOException {

		if (args.length < 1) {
			System.err.println("Il manque le numero de site en argument");
		} else {
			new App(Byte.valueOf(args[0])).start();
		}
	}
}
