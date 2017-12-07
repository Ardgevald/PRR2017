package ch.heigvd.lamportmanager;

import ch.heigvd.interfacesrmi.IGlobalVariable;
import ch.heigvd.interfacesrmi.ILamportAlgorithm;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe gérant un serveur de variable global entre plusieurs serveurs
 * différent, synchronisant le tout grâce à Lamport et RMI. Le protocole de
 * Lamport est fortement inspiré de la specs vue en classe, adapté en java.
 *
 *
 * 2 méthodes peuvent être utilisées pour définir les autres serveur:
 * 
 * 1:	insérer un fichier hosts.txt à la racine du projet, de la forme:
 *    10.0.0.5 2002
 *    10.1.0.5 2002
 *    10.2.0.7 2003
 * 
 * Correspondant à :
 *    "ip" "port"
 * 
 * Ceci est la méthode préconisée si on souhaite un serveur lancé en
 * standalone. Ce fichier pourrait être fourni et commun à tous les serveurs de 
 * variable globale de tous les sites.
 * Seul le numéro de site, créé à l'instanciation, diffère entre
 * les sites.
 * 
 * 2:	utiliser un String[][] hosts, de la forme
 *    {{"10.0.0.5", "2002"}, {"10.0.1.3", "2003"}}.
 * 
 * Ceci est la méthode préconisée si on souhaite
 * tester, ou si l'utilisation d'un fichier hosts.txt n'est pas appropriée par
 * exemple dans le cadre d'utilisation de cette classe en tant que librairie
 *
 * On part du principe que les différents serveurs sont lancés et prêts à
 * fonctionner avant que les clients ne commencent à faire des requêtes sur les
 * serveurs. 
 * 
 * Notes concernant RMI : depuis quelques années, il est possible de créer un
 * registre RMI directement en java. Ainsi, plus aucune commande n'est à taper
 * dans le terminal afin d'exécuter ce serveur. Cette classe prend compte de
 * cela, ce qui est la méthode maintenant préconisée. L'instanciation de cette
 * classe créera donc un registre RMI, à l'adresse et au port indiqué dans le
 * fichier hosts, ou en paramètre.
 *
 * @author Miguel Pombo Dias
 * @author Rémi Jacquemard
 */
public class LamportManager {

	// --------------- VARIABLES ----------------
	/**
	 * Stock la variable globale
	 */
	private int globalVariable;

	/**
	 * Le nombre de site total, soit le nombre de serveur Lamport tel que
	 * celui-ci
	 */
	private final int nbSites;

	/**
	 * L'index de l'hôte courant, parmis le 'nbSites'
	 */
	private final int hostIndex;

	/**
	 * Stock l'estampille courant du serveur. On utilise le long plutôt qu'un
	 * int, afin d'éviter les problèmes d'overflow
	 */
	private long localTimeStamp = 0;

	/**
	 * La liste des serveurs Lamport RMI distant. C'est à ceux-ci qu'on se
	 * connecte lorsqu'on souhaite demander une SC lors de la modification de la
	 * variable globale
	 */
	private ILamportAlgorithm[] lamportServers;

	/**
	 * La liste de tous les sites, sous la forme {{"10.0.0.5", "2002"},
	 * {"10.0.1.3", "2003"}}, soit {ip, port}
	 */
	private String[][] remotes;

	/**
	 * Cet objet sert pour lock les procédures lors d'attente au SC. On fait
	 * attendre les demandes de modifications de variables globale si on a pas
	 * la section critique en faisant un lock.wait(). On libère ce lock si la
	 * section critique peut être acquise.
	 */
	private final Object lock = new Object();

	/**
	 * Classe représentant les messages reçus par un site Lamport distant. Elle
	 * est principalement utilisées afin de stocker les messages reçu dans la
	 * "file", telle que décrite dans la spécification
	 */
	private static class Message {

		/**
		 * Les 3 types de messages possible de Lamport: Requête, Quittance,
		 * Libération
		 */
		public static enum MESSAGE_TYPE {
			REQUEST, RESPONSE, LIBERATE
		};

		private final MESSAGE_TYPE messageType;

		// Le temps est toujours envoyé / reçu de tous le monde
		private final long time;

		public Message(MESSAGE_TYPE message_type, long time) {
			this.messageType = message_type;
			this.time = time;
		}

		// -------- GETTERS --------
		public MESSAGE_TYPE getMessageType() {
			return messageType;
		}

		public long getTime() {
			return time;
		}

	}

	/**
	 * On y stocke les messages reçu des autres sites et du sien. Utile afin de
	 * déterminer si on a le droit ou non d'entrer en section critique, tel que
	 * l'algorithme de Lamport le défini
	 */
	private Message[] messagesArray;

	// --------------------- CONSTRUCTEURS ---------------------
	/**
	 * Permet d'instancier un serveur RMI Lamport gérant une variable globale
	 * commune à tous les serveurs RMI.
	 *
	 * Les hosts sont sous la forme :
    *    {{"10.0.0.5", "2002"}, {"10.0.1.3", "2003"}}
	 *
	 * Le serveur instancié créera un registre RMI à l'adresse et au port
	 * hosts[hostsIndex]
	 *
	 * Notons que l'instanciation de cet objet ne se connecte pas effectivement
	 * aux autres hôtes
	 *
	 * @param hosts La liste des serveurs RMI Lamport disponibles
	 * @param hostIndex L'index, à partir de 0, de l'hôte courant
	 */
	public LamportManager(String[][] hosts, int hostIndex) {
		this.hostIndex = hostIndex;

		this.globalVariable = 0;

		this.remotes = hosts;
		this.nbSites = remotes.length;

		this.lamportServers = new ILamportAlgorithm[nbSites];

		// On initialise les messages reçu à un temps 0 avec des liberates
		this.messagesArray = new Message[nbSites];
		for (int i = 0; i < messagesArray.length; i++) {
			this.messagesArray[i] = new Message(Message.MESSAGE_TYPE.LIBERATE, 0);
		}

		try {

			// Retreiving our port address
			int portUsed = Integer.parseInt(remotes[hostIndex][1]);

			// Creating local RMI servers
			Registry registry = LocateRegistry.createRegistry(portUsed);

			// On crées les serveurs RMI écoutant et on les lies au registre courant
			IGlobalVariable globalVariableServer = new GlobalVariableServer();
			registry.rebind(IGlobalVariable.RMI_NAME, globalVariableServer);

			ILamportAlgorithm lamportAlgorithmServer = new LamportAlgorithmServer();
			registry.rebind(ILamportAlgorithm.RMI_NAME, lamportAlgorithmServer);

			System.out.println("RMI registry on " + portUsed + " with bindings:");
			Arrays.stream(registry.list()).forEach(System.out::println);

		} catch (RemoteException ex) {
			Logger.getLogger(LamportManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	/**
	 * Permet d'instancier un serveur RMI Lamport gérant une variable globale
	 * commune à tous les serveurs RMI. Lorsque ce constructeur est utilisé, un
	 * fichier hosts.txt doit être présent. Il est de la forme :
    *    10.0.0.5 2002
	 *    10.1.0.5 2002
    *    10.2.0.7 2003
	 *
	 * On définit l'hôte courant parmis les hôtes définis dans le hosts.txt par
	 * le "hostIndex" passé en paramètre
	 *
	 * Notons que l'instanciation de cet objet ne se connecte pas effectivement
	 * aux autres hôtes
	 *
	 * @param hostIndex le numéro d'hôte courant, à partir de 0
	 * @throws IOException Si le fichier hosts.txt n'est pas trouvé
	 */
	public LamportManager(int hostIndex) throws IOException {
      
		// Retreiving the other hosts from the hosts.txt file;
		this(Files.readAllLines(Paths.get("hosts.txt")).stream()
				.map((s) -> s.split(" "))
				.toArray(String[][]::new), hostIndex);

	}

	// --------------- METHODES PUBLIQUESS -------------
	/**
	 * Permet de se connecter effectivement aux autres hôtes en RMI Cette
	 * méthode est différée par rapport à l'instanciation de cette classe. En
	 * effet, il est fort probable qu'à la création de cette objet, pas tous les
	 * autres serveur Lamport n'aient été déjà lancés. Ainsi, il est possible de
	 * procéder en 2 étapes:
    * 
    * 1:	Création de tous les serveurs Lamport et ouverture des accès RMI
    * 2:	Connection effective à tous les autres serveurs Lamport
	 *
	 * @throws NotBoundException S'il y a eu un problème lors de la connexion
	 * aux hôtes
	 * @throws MalformedURLException Si un nom d'hôte est mal formé
	 * @throws RemoteException Si il y a eu un problème du côté d'un hôte
	 * distant
	 * @throws ConnectException Si l'hôte distant est introuvable. Peut être
	 * n'est-il pas lancé ?
	 */
	public void connectToRemotes() throws NotBoundException, MalformedURLException, RemoteException, ConnectException {
		// Connecting to other hosts
		for (int i = 0; i < remotes.length; i++) {
			String[] currentHost = remotes[i];

			ILamportAlgorithm remoteServer = (ILamportAlgorithm) Naming.lookup("//" + currentHost[0] + ":" + currentHost[1] + "/" + ILamportAlgorithm.RMI_NAME);
			lamportServers[i] = remoteServer;
		}

		System.out.println("Remotes connected !");
	}

	
	
	
	// -------------------------- SERVEURS RMI --------------------------
	private class LamportAlgorithmServer extends UnicastRemoteObject implements ILamportAlgorithm {

		public LamportAlgorithmServer() throws RemoteException {
			super();
		}

		@Override
		public synchronized long request(long remoteTimeStamp, int hostIndex) throws RemoteException {
			increaseTime(remoteTimeStamp);

			handleMessageReceived(hostIndex, new Message(Message.MESSAGE_TYPE.REQUEST, remoteTimeStamp));

			// On quittance en envoyant le temps courant
			return localTimeStamp;
		}

		@Override
		public void free(long remoteTimeStamp, int value, int hostIndex) throws RemoteException {
			globalVariable = value;

			// On met à jour le temps local
			increaseTime(remoteTimeStamp);

			// On met à jour les messages reçu
			handleMessageReceived(hostIndex, new Message(Message.MESSAGE_TYPE.LIBERATE, remoteTimeStamp));

			synchronized (lock) {
            if(canEnterCS())
				// On notifie si on souhaitait, par hasard, entrer en section critique
				lock.notify();

			}
		}

	}

	private class GlobalVariableServer extends UnicastRemoteObject implements IGlobalVariable {

		public GlobalVariableServer() throws RemoteException {
			super();
		}

		@Override
		public synchronized int getVariable() throws RemoteException {
			return globalVariable;
		}

		@Override
		public synchronized void setVariable(int value) throws RemoteException {
			System.out.println(hostIndex + " : WaitForCS");
			// Demande de section critique
			waitForCS();
			System.out.println(hostIndex + " : InCS - ");
			// On est ici en section critique
			globalVariable = value;

			// Relachement de la section critique				
			releaseCS();
			System.out.println(hostIndex + " : ReleaseCS - ");
		}

	}

	// ------------ METHODES UTILITAIRES PRIVEES ------------
   /**
    * Permet d'envoyer à tous les serveurs sur les sites distants une requête
    * pour l'accès à une section critique. Des threads sont créés pour chaque
    * envoi afin de ne pas attendre sur un éventuel retard de message avant
    * d'envoyer les autres messages. On effectue ensuite une jointure sur
    * tous les threads pour les terminer proprement.
    * 
    * @param localTimeStamp         Le temps logique à envoyer
    * @throws InterruptedException  En cas d'interruption de l'un des threads
    */
	private void sendRequestsAndProcessResponse(final long localTimeStamp) throws InterruptedException {
		// On set notre message courant
		messagesArray[hostIndex] = new Message(Message.MESSAGE_TYPE.REQUEST, localTimeStamp);

		Thread[] senderThreads = new Thread[lamportServers.length];

		// Création des threads d'envoi des requêtes
		for (int i = 0; i < senderThreads.length; i++) {
			if (i == hostIndex) { // On n'envoie pas à nous même
				continue;
			}

			final int index = i;
			senderThreads[i] = new Thread(() -> {
				try {
					// On envoie à tout le monde sauf à nous
					long remoteTime = lamportServers[index].request(localTimeStamp, hostIndex);

					// On set le message reçu
					handleMessageReceived(index, new Message(Message.MESSAGE_TYPE.RESPONSE, remoteTime));
				} catch (RemoteException ex) {
					Logger.getLogger(LamportManager.class.getName()).log(Level.SEVERE, null, ex);
				}
			});
		}

		// Exécution des threads
		for (Thread senderThread : senderThreads) {
			if (senderThread != null) {
				senderThread.start();
			}
		}

		// Attente sur les threads (bloquant)
		for (Thread senderThread : senderThreads) {
			if (senderThread != null) {
				senderThread.join();
			}
		}

	}

   /**
    * On envoie à tous les serveurs des sites distants un message de libération
    * de la section critique précédement occupée par l'appelant.
    * @param localTimeStamp   Le temps logique de l'appelant
    * @param value            La nouvelle valeur de la variable partagée
    * @throws RemoteException En cas d'erreut de communication distante
    */
	private synchronized void sendLiberates(long localTimeStamp, int value) throws RemoteException {
		// la méthode free n'est pas bloquante
		for (ILamportAlgorithm lamportServer : lamportServers) {
			lamportServer.free(localTimeStamp, value, hostIndex);
		}
	}

   /**
    * Méthode permettant l'incrémentation du temps logique local selon
    * l'algorithme de Lamport qui est le maximum entre le temps local et
    * le temps distant + 1
    * @param remoteTimeStamp temps logique du site distant
    */
	private void increaseTime(long remoteTimeStamp) {
		localTimeStamp = Math.max(localTimeStamp, remoteTimeStamp) + 1;
	}

   /**
    * Permet le traitement des messages que l'on souhaite ajouter à la file
    * des messages. On ne remplace pas les messages de type requêtes par une
    * quittance.
    * 
    * @param hostIndex  site du message à ajouter
    * @param message    contient le type de message ainsi que le temps
    * logique associé au message.
    */
	private void handleMessageReceived(int hostIndex, Message message) {
		System.out.println(this.hostIndex + " reveived : " + hostIndex + " " + message.messageType);
		// Si on reçoit une quittance et qu'il y a une requête dans la file, on
      // ne remplace pas le message
		if (message.messageType != Message.MESSAGE_TYPE.RESPONSE || messagesArray[hostIndex].messageType != Message.MESSAGE_TYPE.REQUEST) {
			messagesArray[hostIndex] = message;
		}
	}

   /**
    * méthode permettant d'attendre l'accès à la section critique en vue de 
    * modifier la variable partagée
    */
	private void waitForCS() {
		try {
         // D'abord, on envoie une requête aux autres sites
			sendRequestsAndProcessResponse(localTimeStamp);
         
			/*
			 * On calcule si on peut entrer en SC
			 * On reste bloqué tant qu'on peut pas entrer en SC
			 */
			if (!canEnterCS()) {
				synchronized (lock) {
					// Attendre jusqu'à ce qu'on soit notifié
               // lors de l'arrivée d'un message
					lock.wait();
				}
			}

		} catch (InterruptedException ex) {
			Logger.getLogger(LamportManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

   /**
    * permet de relâcher la section critique en envoyant des messages de
    * libération
    */
	private synchronized void releaseCS() {
		try {
			sendLiberates(localTimeStamp, globalVariable);
		} catch (RemoteException ex) {
			Logger.getLogger(LamportManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

   /**
    * méthode permettant de vérifier si l'accès à la section critique est
    * permis selon l'algorithme de Lamport.
    * 
    * @return un booléen indiquant si l'accès est permis
    */
	private boolean canEnterCS() {
		long minTime = Long.MAX_VALUE;
		System.out.print(hostIndex);
		for (Message message : messagesArray) {
			if (message.time < minTime) {
				minTime = message.time;
			}

			System.out.print("[" + message.messageType + "-" + message.time + "]");
		}

		System.out.println("/\n");

		/**
		 * "Un processus Pi se donne le droit d'entrer en section critique
		 * lorsque file(i).msgType = REQUETE et que son estampille est la plus
		 * ancienne des messages contenus dans file(i)."
		 */
		boolean ok = true;
		for (int j = 0; j < nbSites; j++) {
			if (j != hostIndex) {
				ok = ok && (messagesArray[hostIndex].time < messagesArray[j].time
						|| (messagesArray[hostIndex].time == messagesArray[hostIndex].time && hostIndex < j));
			}
		}

		return ok;
	}

	// ---------------- ENTRY POINT --------------------
   // TODO: ajouter un nombre d'essais limité ?
	public static void main(String... args) throws IOException {
		if (args.length != 1) {
			System.err.println("Usage: <index, starting at 0>. A hosts.txt file should be "
					+ "in the same folder as this one");
			System.exit(1);
		}
		int hostIndex = Integer.parseInt(args[0]);

		// Creating 1 host and connecting to the others		
		LamportManager lamportManager = new LamportManager(hostIndex);

		// On essaie de se connecter non-stop, laisse le temps d'allumer les autres hosts
		boolean connected = false;
		while (!connected) {
			connected = true;
			try {
				// Connecting to remotes
				lamportManager.connectToRemotes();
			} catch (NotBoundException | MalformedURLException | RemoteException e) {
				connected = false;
				System.err.println("Error connecting to hosts, retrying...");
			}
		}
	}
}
