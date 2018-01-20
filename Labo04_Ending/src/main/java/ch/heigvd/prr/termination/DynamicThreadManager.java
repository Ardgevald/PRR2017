package ch.heigvd.prr.termination;

import ch.heigvd.prr.termination.UDPMessageHandler.UDPMessageListener;
import java.io.Closeable;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class DynamicThreadManager implements UDPMessageListener, Closeable {

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
		if (newThreadForbidden) {
			log("Not accepting any more task");
			return;
		}

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

	private class Task implements Runnable {

		@Override
		public void run() {

			log("Starting task");
			try {
				Thread.sleep(ThreadLocalRandom.current().nextInt(MINIMUM_WAIT, MAXIMUM_WAIT));
			} catch (InterruptedException ex) {
				Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
				return;
			}
			log("Task completed");

			boolean beginNewTask = ThreadLocalRandom.current().nextInt(100) < P;

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

	@Override
	public void close() throws IOException {
		this.initiateTerminaison();
		this.udpMessageHandler.close();
	}

	private void log(String message) {
		System.out.println(localHostIndex + ": " + message);
	}

	// --- APP interface ---
	public void initiateTerminaison() {
		this.forbidNewThreads();
		this.waitForTasks();

		try {
			Message.EndTokenMessage m = new Message.EndTokenMessage();
			m.incrementCounter(); // We have seen it
			this.udpMessageHandler.sendTo(new Message.EndTokenMessage(), getNextSite());
		} catch (IOException ex) {
			Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	public void initiateTask() {
		this.newTask();
	}

	public static void main(String... args) {
		/*
		DynamicThreadManager[] managers = new DynamicThreadManager[4];
		for (int i = 0; i < 4; i++) {
			try {
				managers[i] = new DynamicThreadManager((byte) i);
				managers[i].initiateTask();
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
		 */

		// Récupération du numéro de site
		Scanner scanner = new Scanner(System.in);
		boolean ok;
		int host = 0;		
		do {
			ok = true;
			System.out.print("No de site courant: ");
			try {
				host = scanner.nextInt();

				if (host > 4 || host < 1) {
					throw new IndexOutOfBoundsException();
				}
			} catch (Exception e) {
				System.out.println("No de site incorrecte, réessayer");
				ok = false;
			}
		} while (!ok);

		// Lancement du site
		DynamicThreadManager manager = null;
		try {
			manager = new DynamicThreadManager((byte)(host -1));
		} catch (IOException ex) {
			System.err.println("Problème lors de la création du manager");
			ex.printStackTrace();
			System.exit(-1);
		}
		
		// Menu
		boolean exit = false;
		do {
			System.out.println("\t1. Lancer une tâche");
			System.out.println("\t2. Initier la terminaison");
			System.out.println("\t3. Terminer le programme");
			System.out.println("Entrer le numéro correspondant à l'action voulue: ");
			
			try {
				int num = scanner.nextInt();

				if (num > 3 || num < 1) {
					throw new IndexOutOfBoundsException();
				}
				
				// Handling menu action
				switch(num){
					case 1:
						System.out.println("Lancement d'une nouvelle tâche");
						manager.initiateTask();
						break;
					case 2:
						System.out.println("Demande de terminaison");
						manager.initiateTerminaison();
						break;
					case 3:
						exit = true;
						break;
				}
				
			} catch (Exception e) {
				System.out.println("No incorrecte, réessayer");
			}
		} while (!exit);
		
		try {
			// On quitte, on ferme les connexion
			manager.close();
			scanner.close();
		} catch (IOException ex) {
			Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
		}
		

	}

}
