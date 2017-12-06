package ch.heigvd.test;

import ch.heigvd.globalvariableclient.Client;
import ch.heigvd.lamportmanager.LamportManager;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Test;


public class TestRMI {

	public TestRMI() {
	}


	@Before
	public void setUp() throws IOException {
		// Creating 3 hosts locally
		String[][] hosts = {
			{"localhost", "2002"},
			{"localhost", "2003"},
			{"localhost", "2004"}
		};
		
		LamportManager[] lamportManagers = new LamportManager[hosts.length];
		for(int i = 0; i < lamportManagers.length; i++){
			lamportManagers[i] = new LamportManager(hosts, i);
		}
		

		// Connecting hosts
		Arrays.stream(lamportManagers).forEach((lamportManager) -> {
			try {
				lamportManager.connectToRemotes();
			} catch (NotBoundException | MalformedURLException | RemoteException ex) {
				Logger.getLogger(TestRMI.class.getName()).log(Level.SEVERE, null, ex);
			}
		});
	}

	@Test
	public void test() throws MalformedURLException, RemoteException, NotBoundException{
		
		// Testing procedure
		// Creating 5 app per server
		//*
		ArrayList<Thread> threads = new ArrayList<>();
		
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 5; j++) {
				final Client application = new Client("localhost:" + (2002 + i));
				final String name = i + " - " + j + " : ";
				final int index = i;
				Thread t = new Thread(() -> {

					for (int x = 0; x < 10000; x++) {
						try {
							int val = x + index * 1000;
							System.out.println(name + "setting value " + val);
							application.setGlobalValue(val);
							System.out.println(name + "getting value ");
							System.out.println(name + application.getGlobalVariable());
						} catch (RemoteException ex) { // The server could be momentaly full
							Logger.getLogger(TestRMI.class.getName()).log(Level.SEVERE, null, ex);
						}
					}
				});
				t.start();
				threads.add(t);
			}

		}
		
		// Waiting fot the threads to finish
		threads.stream().forEach((thread) -> {
			try {
				thread.join();
			} catch (InterruptedException ex) {
				Logger.getLogger(TestRMI.class.getName()).log(Level.SEVERE, null, ex);
			}
		});
	}
}
