package ch.heigvd.globalvariableclient;

import ch.heigvd.interfacesrmi.IGlobalVariable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cette classe représente un client de variable grobale, qui se connectera à un
 * serveur RMI Lamport passé en paramètre Cette classe peut être incluse dans
 * une application en tant que librairie, ce qui permettrait facilement d'avoir
 * une variable globale commune à plusieurs application
 *
 * @author Miguel Pombo Dias
 * @author Rémi Jacquemard
 */
public class Client {

	/**
	 * Le serveur distant RMI
	 */
	private IGlobalVariable server;

	/**
	 * Permet de créer un client qui se connecte à un serveur gérant des
	 * variables gobales en RMI passé en paramètre
	 *
	 * @param site le site sur lequel se connecter, de la forme "10.2.3.4:2002"
	 * @throws MalformedURLException Si l'url n'est pas bon
	 * @throws RemoteException Si il y a un problème avec la connexion du site
	 * @throws NotBoundException si il y a un problème avec le binding
	 */
	public Client(String site) throws MalformedURLException, RemoteException, NotBoundException {
		// Rechercher une reference au serveur
		try {
			server = (IGlobalVariable) Naming.lookup("//" + site + "/" + IGlobalVariable.RMI_NAME);
		} catch (MalformedURLException | NotBoundException | RemoteException e) {
			System.err.println("Erreur avec la reference du serveur");
			throw e;
		}
	}

	/**
	 * Permet de récupérer la variable global à partir du serveur en RMI
	 *
	 * @return la variable gobal
	 * @throws RemoteException s'il y a eu une erreur lors de la récupèration de
	 * la variable
	 */
	public int getGlobalVariable() throws RemoteException {
		return server.getVariable();
	}

	/**
	 * Permet de modifier la variable global stockée sur le serveur RMI ainsi
	 * que sur tous les serveurs RMI distant. Est bloquant (attente de la SC)
	 *
	 * @param value la nouvelle valeur de la variable
	 * @throws RemoteException s'il y a eu une erreur lors de la modification de
	 * la variable
	 */
	public void setGlobalValue(int value) throws RemoteException {
		server.setVariable(value);
	}

	// ------------- ENTRY POINT -----------
	/**
	 * Permet de lancer un client temporaire d'un serveur de variable global en
	 * standalone Il doit y avoir au minimum 2 arguments:
    * 1: le host (adresse IP ou hostName), sous la forme "10.0.0.5"
    * 2: le port RMI utilisé, tel que 2000
	 *
    * il est ensuite possible en console de lire ou écrire la variable
    * partagée en suivant les instructions affichées.
    * 
	 * @param args les paramètres du client
	 */
	public static void main(String... args) {
		try {
			String host = args[0];
			String port = args[1];

			Client application = new Client(host + ":" + port);
			System.out.println("Connected to " + host + ":" + port);
			
			BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
			boolean cont = true;
			do {
				System.out.println("1 : show current value");
				System.out.println("2 : set current value");
				System.out.println("q : quit");
				System.out.print("> ");
				
				switch(r.readLine()){
					case "1":
						System.out.println("current value is : " + application.getGlobalVariable());
						break;
					case "2":
						System.out.print("Enter new value : ");
						int value = Integer.parseInt(r.readLine());
						System.out.println("waiting access to critical section for new value");
						application.setGlobalValue(value);
						System.out.println("new value is : " + application.getGlobalVariable());
						break;
					case "q":
						cont = false;
                  break;
					default:
						System.out.println("Bad input, please retry...");
						break;
				}
				
				
			} while (cont);
         
		} catch (IndexOutOfBoundsException e) {
			System.err.println("Usage: <hostName> <port> [<value to set>]");
         System.exit(1);
		} catch (NotBoundException | IOException ex) {
			Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
         System.exit(1);
		}
	}
}
