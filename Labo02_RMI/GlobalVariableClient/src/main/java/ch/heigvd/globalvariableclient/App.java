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

	public App(String site) {
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

		// Creating 5 app per server
		/*
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 5; j++) {
				final App application = new App("localhost:" + (2002 + i));

				new Thread(() -> {
					for (int x = 0; x < 60; x++) {
						try {
							application.setGlobalValue(x);
							System.out.println(application.getGlobalVariable());
						} catch (RemoteException ex) {
							Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
						}
					}
				}).start();

			}
		}//*/
		//*
		try {
			//System.setProperty("java.security.policy", "file:./ch/heigvd/globalvariableclient/client.policy");
			//App application = new App(args[0]);
			App application = new App("localhost:2002");
			System.out.println(application.getGlobalVariable());
			application.setGlobalValue(23);
			System.out.println(application.getGlobalVariable());
			
		
		} catch (IndexOutOfBoundsException e) {
			System.out.println("Usage: " + App.class.getSimpleName() + " [site_serveur]");
			System.exit(1);
		} catch (RemoteException ex) {
			Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
		}*/
	}
}
