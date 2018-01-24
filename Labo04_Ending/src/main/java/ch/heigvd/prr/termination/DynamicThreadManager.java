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
import java.util.InputMismatchException;
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

   private boolean isInitiator = false;
   private boolean newThreadForbidden = false;
   private boolean running = false;

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

   private synchronized void newTask() {

      running = true;

      Task t = new Task();
      this.tasks.add(t);
      new Thread(t).start();
   }

   @Override
   public void dataReceived(Message message) {
      switch (message.getMessageType()) {
         case START_TASK:
            Message.StartTaskMessage startMessage = (Message.StartTaskMessage) message;

            // on considère que le site est running si lui ou un suivant effectue une tâche 
            running = true;

            if (startMessage.getSiteIndex() == localHostIndex) {
               log("START_TASK to handle receive");
               // La tache nous est destinée, on la traite
               this.newTask();
            } else {
               try {
                  log("START_TASK for " + (startMessage.getSiteIndex() + 1) + " received, transmitting further");
                  // Sinon, on la transmet plus loin
                  this.udpMessageHandler.sendTo(startMessage, getNextSite());
               } catch (IOException ex) {
                  Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
               }
            }

            break;
         case TOKEN:
            // on stope la création de nouveaux threads par l'utilisateur
            newThreadForbidden = true;

            Message.TokenMessage tokenMessage = (Message.TokenMessage) message;
            log("ENDING_TASK receive");

            if (isInitiator && tokenMessage.getInitiator() > localHostIndex) {
               /**
                * Si on est un site initiateur et que ce n'est pas notre index qui
                * est indiqué dans le jeton, cela signifie qu'un autre jeton est en
                * train de circuler. Dans le cas où ce jeton est d'un indice plus
                * élevé, on ne transfère pas le jeton plus loin et on attend le jeton
                * que l'on a initié
                */

            } else if (isInitiator && tokenMessage.getInitiator() == localHostIndex && !running) {
               /**
                * Si on est un site initiateur et qu'il s'agit de notre jeton, tous
                * le monde a vu ce jeton et on peut peut-être arrêter la propagation
                * Si on est resté dans l'état inactif. Alors on termine l'application
                */
               log("App is now completed, sending END to everyone");

               try {
                  // On indique la terminaison de l'application
                  Message.EndMessage endMessage = new Message.EndMessage(localHostIndex);
                  this.udpMessageHandler.sendTo(endMessage, getNextSite());
               } catch (IOException ex) {
                  Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
               }
            } else {
               // On termine nos taches
               this.waitForTaskEnds();

               try {
                  this.udpMessageHandler.sendTo(tokenMessage, getNextSite());
               } catch (IOException ex) {
                  Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
               }
            }

            break;
         case END:
            // L'application est terminée
            log("Application is terminated, shuting down");
            Message.EndMessage endMessage = (Message.EndMessage) message;

            if (endMessage.getInitiator() != localHostIndex) {
               // On doit retransmettre le message
               try {
                  this.udpMessageHandler.sendTo(endMessage, getNextSite());
               } catch (IOException ex) {
                  Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
               }
            } else {
               // On a déjà vu ce message (on est l'initiateur)
               // On arrête simplement la propagation, on ne fait rien
               // On est resté up jusque là pour ne pas laisser trainer des
               // message sur le réseau
            }

            System.exit(0);
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
               udpMessageHandler.sendTo(new Message.StartTaskMessage(getRandomSiteTarget()), getNextSite());
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

   private synchronized void waitForTaskEnds() {
      try {
         log("Terminating tasks");
         while (!tasks.isEmpty()) {
            this.wait();
         }
         log("Tasks terminated");
         running = false;
      } catch (InterruptedException ex) {
         Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
      }
   }

   private byte getRandomSiteTarget() {
      byte site = (byte) ThreadLocalRandom.current().nextInt(hosts.length - 1);

      return (byte) (site >= localHostIndex ? site + 1 : site);
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
   public synchronized void initiateTerminaison() {
      newThreadForbidden = true;
      isInitiator = true;

      this.waitForTaskEnds();

      try {
         Message.TokenMessage m = new Message.TokenMessage(localHostIndex);
         this.udpMessageHandler.sendTo(m, getNextSite());
      } catch (IOException ex) {
         Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
      }
   }

   public synchronized void initiateTask() {
      if (!newThreadForbidden) {
         this.newTask();
      }
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
      boolean ok = false;
      int host = 0;
      do {
         System.out.print("No de site courant: ");
         try {
            host = scanner.nextInt();

            if (host > 4 || host < 1) {
               throw new IndexOutOfBoundsException();
            }

            ok = true;
         } catch (IndexOutOfBoundsException e) {
            System.out.println("No de site incorrecte, réessayer");
         } catch (InputMismatchException e) {
            System.out.println("Veuillez saisir un nombre");
            scanner.nextLine();
         }
      } while (!ok);

      // Lancement du site
      DynamicThreadManager manager = null;
      try {
         manager = new DynamicThreadManager((byte) (host - 1));
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
            switch (num) {
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

         } catch (IndexOutOfBoundsException e) {
            System.out.println("No incorrecte, réessayer");
         } catch (InputMismatchException e) {
            System.out.println("Veuillez saisir un nombre");
            scanner.nextLine();
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
