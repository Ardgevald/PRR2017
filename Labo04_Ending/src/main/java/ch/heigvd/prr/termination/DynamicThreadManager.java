package ch.heigvd.prr.termination;

import ch.heigvd.prr.termination.UDPMessageHandler.UDPMessageListener;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class DynamicThreadManager implements UDPMessageListener {

	private final int P = 50; // P entre 0 et 100
	private final int MINIMUM_WAIT = 5000;
	private final int MAXIMUM_WAIT = 15000;

	private final byte localHostIndex;

	private boolean newThreadForbidden = false;

	private final Site[] hosts;
	private final Site localHost;

	private UDPMessageHandler udpMessageHandler;

	// On crée une liste synchronizée afin d'éviter les problèmes lors d'ajoute
	// et de suppression concurente. On aurait pu utiliser aussi une méthode
	// add() et delete() synchronisée privée
	public List<Runnable> tasks = Collections.synchronizedList(new ArrayList<>());

	// ------- Constructors ------
	// Main constructor
	public DynamicThreadManager(Site[] hosts, byte localHostIndex) throws SocketException, IOException {
		this.hosts = hosts;
		this.localHostIndex = localHostIndex;
		this.localHost = hosts[localHostIndex];

		this.udpMessageHandler = new UDPMessageHandler(localHost.getSocketAddress());
		this.udpMessageHandler.addListener(this);

		this.newTask();
	}

	public DynamicThreadManager(String[][] hosts, byte hostIndex) throws SocketException, IOException {
		this(Arrays.stream(hosts)
			.map((s) -> new Site(s[0], Integer.parseInt(s[1])))
			.toArray(Site[]::new), hostIndex);

	}

	public DynamicThreadManager(byte hostIndex) throws IOException {
		// Retreiving the other hosts from the hosts.txt file;
		this(Files.readAllLines(Paths.get("hosts.txt")).stream()
			.map((s) -> s.split(" "))
			.toArray(String[][]::new), hostIndex);
	}

	private synchronized void forbidNewThreads() {
		newThreadForbidden = true;
	}

	private synchronized void newTask() {
		Task t = new Task();
		this.tasks.add(t);
		new Thread(t).start();
	}

	@Override
	public void dataReceived(Message message) {
		switch (message.getMessageType()) {
			case START_TASK:
				log("START_TASK receive");
				this.newTask();
				break;
			case END_TOKEN:
				log("END_TASK receive");
				Message.EndTokenMessage endMessage = (Message.EndTokenMessage) message;

				// Si le compteur est égal au nombre de site, le jeton a fait le 
				// tour, tous le monde la vu et on peut arrêter la propagation
				if (endMessage.getCounter() < this.hosts.length) {
					this.forbidNewThreads();
					this.waitForTasks();
					endMessage.incrementCounter();
					try {
						this.udpMessageHandler.sendTo(endMessage, getNextSite());
					} catch (IOException ex) {
						Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
					}
				} else { // On a déjà vu ce message
					// Pas besoin d'interdire nes nouveau thread, vu qu'on a initié
					// la terminaison
					
					// On ne fait rien, on arrête simplement la propagation
					log("All of tasks are completed");
				}
				break;
		}
	}

	public void initiateTerminaison() {
		this.forbidNewThreads();
		this.waitForTasks();

		try {
			this.udpMessageHandler.sendTo(new Message.EndTokenMessage(), getNextSite());
		} catch (IOException ex) {
			Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private class Task implements Runnable {

		@Override
		public void run() {
			boolean beginNewTask;

			synchronized (DynamicThreadManager.this) {
				if (newThreadForbidden) {
					log("Not accepting any more task");
					return;
				}
			}
			
			log("Starting task");
			try {
				Thread.sleep(ThreadLocalRandom.current().nextInt(MINIMUM_WAIT, MAXIMUM_WAIT));
			} catch (InterruptedException ex) {
				Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
				return;
			}
			log("Task completed");

			beginNewTask = ThreadLocalRandom.current().nextInt(100) < P;

			if (beginNewTask) {
				try {
					log("Sending task to other");
					// démarre un nouveau thread sur un autre site
					udpMessageHandler.sendTo(new Message.StartTaskMessage(), getRandomSiteTarget());
				} catch (IOException ex) {
					Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

			synchronized (DynamicThreadManager.this) {
				tasks.remove(this);
				DynamicThreadManager.this.notify();
			}
		}
	}

	private synchronized void waitForTasks() {
		try {
			log("Terminating tasks");
			while (!tasks.isEmpty()) {
				this.wait();
			}
			log("Tasks terminated");
		} catch (InterruptedException ex) {
			Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private Site getRandomSiteTarget() {
		int site = ThreadLocalRandom.current().nextInt(hosts.length - 1);

		return hosts[(site >= localHostIndex ? site + 1 : site)];
	}

	private Site getNextSite() {
		return hosts[(localHostIndex + 1) % hosts.length];
	}

	private void log(String message) {
		System.out.println(localHostIndex + ": " + message);
	}
	
	// --- APP interface ---
	

	public static void main(String... args) {
		DynamicThreadManager[] managers = new DynamicThreadManager[4];
		for (int i = 0; i < 4; i++) {
			try {
				managers[i] = new DynamicThreadManager((byte) i);
			} catch (IOException ex) {
				Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
			}
		}

		System.out.println("Everyone has been created");
		try {
			Thread.sleep(20000);
		} catch (InterruptedException ex) {
			Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
		}

		managers[0].initiateTerminaison();

	}

}
