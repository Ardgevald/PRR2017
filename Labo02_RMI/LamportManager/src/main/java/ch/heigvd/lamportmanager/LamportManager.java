package ch.heigvd.lamportmanager;

import ch.heigvd.interfacesrmi.IGlobalVariable;
import ch.heigvd.interfacesrmi.ILamportAlgorithm;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class LamportManager {

	private static final Logger LOG = Logger.getLogger(LamportManager.class.getName());

	private final static String VARIABLE_SERVER_NAME = "GlobalVariable";
	private final static String LAMPORT_SERVER_NAME = "Lamport";

	private int globalVariable;
	private final int nbSites;
	private final int hostIndex;

	private long localTimeStamp = 0;

	private ILamportAlgorithm[] lamportServers;

	private static class Message {

		public static enum MESSAGE_TYPE {
			REQUEST, RESPONSE, LIBERATE
		};
		private MESSAGE_TYPE messageType;
		private long time;

		public Message(MESSAGE_TYPE message_type, long time) {
			this.messageType = message_type;
			this.time = time;
		}

		public MESSAGE_TYPE getMessageType() {
			return messageType;
		}

		public long getTime() {
			return time;
		}

	}

	private Message[] messagesArray;

	public static void main(String... args) throws RemoteException {

		//LocateRegistry.createRegistry(1099);
		/*
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}//*/
		//System.setProperty("java.security.policy", "file:./ch/heigvd/lamportmanager/server.policy");
		LamportManager lamportManager = new LamportManager(2, 1);
	}

	public LamportManager(int nbSites, int hostIndex) {

		this.nbSites = nbSites;
		this.hostIndex = hostIndex;
		globalVariable = 57;
		// TODO : supprimer ce set
		this.lamportServers = new ILamportAlgorithm[nbSites];
		this.messagesArray = new Message[nbSites];

		try {

			// TODO : créer les serveurs distant
			IGlobalVariable globalVariableServer = new GlobalVariableServer();
			// On lie dans le registry
			Registry registry = LocateRegistry.createRegistry(2002);
			registry.rebind(VARIABLE_SERVER_NAME, globalVariableServer);

			System.out.println("Serveur " + VARIABLE_SERVER_NAME + " pret");

			ILamportAlgorithm lamportAlgorithmServer = new LamportAlgorithmServer();
			registry.rebind(LAMPORT_SERVER_NAME, lamportAlgorithmServer);
			System.out.println("Serveur " + LAMPORT_SERVER_NAME + " pret");
			
		} catch (RemoteException ex) {
			Logger.getLogger(LamportManager.class.getName()).log(Level.SEVERE, null, ex);
		}

		/*

			LamportAlgorithmServer lamportAlgorithmServer = new LamportAlgorithmServer();
			Naming.rebind(LAMPORT_SERVER_NAME, lamportAlgorithmServer);
			System.out.println("Serveur " + LAMPORT_SERVER_NAME + " pret");//*/
	}

	private synchronized void sendRequestsAndProcessResponse(final long localTimeStamp) throws InterruptedException {
		// On set notre message courant
		messagesArray[hostIndex] = new Message(Message.MESSAGE_TYPE.REQUEST, localTimeStamp);

		Thread[] senderThreads = new Thread[lamportServers.length];

		// Création des threads d'envoi des requêtes
		for (int i = 0; i < senderThreads.length; i++) {
			final int index = i;
			senderThreads[i] = new Thread(() -> {
				try {
					// On envoie à tout le monde, dont soit même
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
			senderThread.start();
		}

		// Attente sur les threads (bloquant)
		for (Thread senderThread : senderThreads) {
			senderThread.join();
		}

	}

	private synchronized void sendLiberates(long localTimeStamp, int value) throws RemoteException {
		// la méthode free n'est pas bloquante
		for (ILamportAlgorithm lamportServer : lamportServers) {
			lamportServer.free(localTimeStamp, value, hostIndex);
		}
	}

	private void increaseTime(long remoteTimeStamp) {
		localTimeStamp = Math.max(localTimeStamp, remoteTimeStamp) + 1;
	}

	private class GlobalVariableServer extends UnicastRemoteObject implements IGlobalVariable {

		public GlobalVariableServer() throws RemoteException {
			super();
		}

		@Override
		public int getVariable() throws RemoteException {
			return globalVariable;
		}

		@Override
		public void setVariable(int value) throws RemoteException {
			// Demande de section critique
			waitForCS();
			// On est ici en section critique

			globalVariable = value;

			// Relachement de la section critique				
			releaseCS();

		}

	}

	private void handleMessageReceived(int hostIndex, Message message) {
		// On replace pas les messages de type requêtes par une quittance
		if (message.messageType != Message.MESSAGE_TYPE.RESPONSE || messagesArray[hostIndex].messageType != Message.MESSAGE_TYPE.REQUEST) {
			messagesArray[hostIndex] = message;
		}

	}

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
		public synchronized void free(long remoteTimeStamp, int value, int hostIndex) throws RemoteException {
			globalVariable = value;

			// On met à jour le temps local
			increaseTime(remoteTimeStamp);

			// On met à jour les messages reçu
			handleMessageReceived(hostIndex, new Message(Message.MESSAGE_TYPE.LIBERATE, remoteTimeStamp));

			// On notifie si on souhaitait, par hasard, entrer en section critique
			LamportManager.this.notify();
		}

	}

	private synchronized void waitForCS() {
		try {
			sendRequestsAndProcessResponse(localTimeStamp);

			/*
			* On calcule si on peut entrer en SC
			* On reste bloqué tant qu'on peut pas entrer en SC
			 */
			while (!canEnterCS()) {
				// Attendre jusqu'à ce qu'on soit notifié lors de l'arrivée d'un message
				this.wait();
			}

		} catch (InterruptedException ex) {
			Logger.getLogger(LamportManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private synchronized void releaseCS() {
		try {
			sendLiberates(localTimeStamp, globalVariable);
		} catch (RemoteException ex) {
			Logger.getLogger(LamportManager.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private boolean canEnterCS() {
		long minTime = Long.MAX_VALUE;
		for (Message message : messagesArray) {
			if (message.time < minTime) {
				minTime = message.time;
			}
		}

		/**
		 * "Un processus Pi se donne le droit d'entrer en section critique
		 * lorsque file(i).msgType = REQUETE et que son estampille est la plus
		 * ancienne des messages contenus dans file(i)."
		 */
		return messagesArray[hostIndex].messageType == Message.MESSAGE_TYPE.REQUEST
				&& messagesArray[hostIndex].time == minTime;
	}

}
