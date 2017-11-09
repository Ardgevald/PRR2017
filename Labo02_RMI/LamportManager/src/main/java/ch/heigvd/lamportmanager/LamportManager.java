package ch.heigvd.lamportmanager;

import ch.heigvd.interfacesrmi.IGlobalVariable;
import ch.heigvd.interfacesrmi.ILamportAlgorithm;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
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

	private long timeStamp = 0;

	private ILamportAlgorithm[] lamportServers;

	public static void main(String... args) {
		LamportManager lamportManager = new LamportManager(2);
	}

	public LamportManager(int nbSites) {
		this.nbSites = nbSites;
		globalVariable = 0;

		this.lamportServers = new ILamportAlgorithm[nbSites];

		// TODO : créer les serveurs distant
		try {
			GlobalVariableServer globalVariableServer = new GlobalVariableServer();
			Naming.rebind(VARIABLE_SERVER_NAME, globalVariableServer);
			System.out.println("Serveur " + VARIABLE_SERVER_NAME + " pret");

			LamportAlgorithmServer lamportAlgorithmServer = new LamportAlgorithmServer();
			Naming.rebind(LAMPORT_SERVER_NAME, lamportAlgorithmServer);
			System.out.println("Serveur " + LAMPORT_SERVER_NAME + " pret");
		} catch (MalformedURLException | RemoteException e) {
			LOG.log(Level.SEVERE, null, e);
		}
	}

	private synchronized void sendRequests(final long localTimeStamp) throws InterruptedException {
		Thread[] senderThreads = new Thread[lamportServers.length];

		// Création des threads d'envoi des requêtes
		for (int i = 0; i < senderThreads.length; i++) {
			final int index = i;
			senderThreads[i] = new Thread(() -> {
				try {
					lamportServers[index].request(localTimeStamp);
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

		// On est ici en section critique
	}

	private synchronized void sendLiberates(long localTimeStamp, int value) throws RemoteException {
		// la méthode free n'est pas bloquante
		for(ILamportAlgorithm lamportServer : lamportServers){
			lamportServer.free(localTimeStamp, value);
		}
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
			try {
				// Demande de section critique
				sendRequests(timeStamp);
				// On est ici en section critique
				
				globalVariable = value;
				
				// Relachement de la section critique				
				sendLiberates(timeStamp, value);
			} catch (InterruptedException ex) {
				Logger.getLogger(LamportManager.class.getName()).log(Level.SEVERE, null, ex);
			}
		}

	}

	private class LamportAlgorithmServer implements ILamportAlgorithm {

		@Override
		public void request(long remoteTimeStamp) throws RemoteException {

			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void free(long remoteTimeStamp, int value) throws RemoteException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

	}

}
