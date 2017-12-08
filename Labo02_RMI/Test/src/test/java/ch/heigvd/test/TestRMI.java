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
      // on crée trois serveurs locaux afin de tester d'éventuels problèmes
      // de concurrence
      String[][] hosts = {
         {"localhost", "2002"},
         {"localhost", "2003"},
         {"localhost", "2004"}
      };

      LamportManager[] lamportManagers = new LamportManager[hosts.length];
      for (int i = 0; i < lamportManagers.length; i++) {
         lamportManagers[i] = new LamportManager(hosts, i);
      }

      // on connecte les sites entre eux avant de tester
      Arrays.stream(lamportManagers).forEach((lamportManager) -> {
         try {
            lamportManager.connectToRemotes();
         } catch (NotBoundException | MalformedURLException | RemoteException ex) {
            Logger.getLogger(TestRMI.class.getName()).log(Level.SEVERE, null, ex);
         }
      });
   }

   @Test
   public void test() throws MalformedURLException, RemoteException, NotBoundException {

		/**
       * Procédure de test
       * On crée 5 threads par serveur, et chacun va faire des lectures et écritures
       * sur un des trois serveurs en boucle.
       */
      ArrayList<Thread> threads = new ArrayList<>();

      for (int i = 0; i < 3; i++) {
         for (int j = 0; j < 5; j++) {
            final Client application = new Client("localhost:" + (2002 + i));
            final String name = i + " - " + j + " : ";
            final int index = i;
            Thread t = new Thread(() -> {

               for (int x = 0; x < 1000; x++) {
                  try {
                     int val = x + index * 1000;
                     System.out.println(name + "wants to set value " + val);
                     application.setGlobalValue(val);
                     System.out.println(name + "getting value " + application.getGlobalVariable());
                  } catch (RemoteException ex) {
                     Logger.getLogger(TestRMI.class.getName()).log(Level.SEVERE, null, ex);
                  }
               }
            });
            t.start();
            threads.add(t);
         }

      }

      // On attend la fin des threads ce qui confirme qu'il
      // n'y a pas eu de blocage.
      // les affichages des serveurs permettent de constater que
      // les accès à la variable partagée se font en section critique
      threads.stream().forEach((thread) -> {
         try {
            thread.join();
         } catch (InterruptedException ex) {
            Logger.getLogger(TestRMI.class.getName()).log(Level.SEVERE, null, ex);
         }
      });
   }
}
