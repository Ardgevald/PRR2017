package ch.heigvd.globalvariableclient;

import ch.heigvd.interfacesrmi.IGlobalVariable;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class App {

	IGlobalVariable server;

	public App(String site) {

		// Installer le gestionnaire de securite
		/*
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}//*/

		// Rechercher une reference au serveur
		try {
			server = (IGlobalVariable) Naming.lookup("//" + site + "/GlobalVariable");
		} catch (MalformedURLException | NotBoundException | RemoteException e) {
			System.out.println("Erreur avec la reference du serveur");
			e.printStackTrace();
			System.exit(1);
		}
	}

	public int getGlobalVariable() throws RemoteException {
		return server.getVariable();
	}

	public void setGlobalValue(int value) throws RemoteException {
		server.setVariable(value);
	}

	public static void main(String... args) {
		try {
			System.setProperty("java.security.policy", "file:./ch/heigvd/globalvariableclient/client.policy");
			//App application = new App(args[0]);
			App application = new App("10.0.0.8:2001");
			System.out.println(application.getGlobalVariable());
		} catch (IndexOutOfBoundsException e) {
			System.out.println("Usage: " + App.class.getSimpleName() + " [site_serveur]");
			System.exit(1);
		} catch (RemoteException ex) {
			Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
}
