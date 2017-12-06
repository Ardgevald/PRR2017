package ch.heigvd.globalvariableclient;

import ch.heigvd.interfacesrmi.IGlobalVariable;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class App {

	private IGlobalVariable server;

	public App(String site) throws MalformedURLException, RemoteException, NotBoundException {
		// Rechercher une reference au serveur
		try {
			server = (IGlobalVariable) Naming.lookup("//" + site + "/GlobalVariable");
		} catch (MalformedURLException | NotBoundException | RemoteException e) {
			System.err.println("Erreur avec la reference du serveur");
			throw e;
		}
	}

	public int getGlobalVariable() throws RemoteException {
		return server.getVariable();
	}

	public void setGlobalValue(int value) throws RemoteException {
		server.setVariable(value);
	}

	
	
	
	// ------------- ENTRY POINT -----------
	public static void main(String... args) {
		try {
			String host = args[0];
			String port = args[1];
			Integer value = null;
			if (args.length == 3) {
				value = Integer.parseInt(args[2]);
			}

			App application = new App(host + ":" + port);
			System.out.println("global value : " + application.getGlobalVariable());

			if (value != null) {
				application.setGlobalValue(value);
				System.out.println("global value after setting : " + application.getGlobalVariable());
			}
		} catch (IndexOutOfBoundsException e) {
			System.err.println("Usage: <hostName> <port> [<value to set>]");
			System.exit(1);
		} catch (RemoteException | MalformedURLException | NotBoundException ex) {
			Logger.getLogger(App.class
					.getName()).log(Level.SEVERE, null, ex);
		}

	}
}

