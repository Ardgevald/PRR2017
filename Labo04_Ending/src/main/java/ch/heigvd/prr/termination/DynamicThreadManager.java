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
import ch.heigvd.prr.termination.Message.*;

/**
 *
 */
public class DynamicThreadManager implements UDPMessageListener, Closeable {

   private final int P = 49; // P entre 0 et 100
   private final int MINIMUM_WAIT = 5000;
   private final int MAXIMUM_WAIT = 8000;

   private final byte localHostIndex;

   private boolean isInitiator = false;
   private boolean endingPhase = false;
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
   public DynamicThreadManager(Site[] hosts, byte localHostIndex)
      throws SocketException, IOException {

      this.hosts = hosts;
      this.localHostIndex = localHostIndex;
      this.localHost = hosts[localHostIndex];

      this.udpMessageHandler = new UDPMessageHandler(localHost.getSocketAddress());
      this.udpMessageHandler.addListener(this);
   }

   /**
    * constructeur utilisant des tableaux décrivant des hôtes en paramètre
    *
    * @param hosts Les tableaux d'hôtes
    * @param hostIndex Le numéro du site courant
    * @throws SocketException Problème avec la création du socker
    * @throws IOException Problème de transfert de message
    */
   public DynamicThreadManager(String[][] hosts, byte hostIndex)
      throws SocketException, IOException {
      this(Arrays.stream(hosts)
         .map((s) -> new Site(s[0], Integer.parseInt(s[1])))
         .toArray(Site[]::new), hostIndex);

   }

   /**
    * constructeur utilisant le fichier hosts.txt pour la desciption des sites
    *
    * @param hostIndex Le numéro du site courant
    * @throws SocketException Problème avec la création du socker
    * @throws IOException Problème de transfert de message
    */
   public DynamicThreadManager(byte hostIndex) throws IOException {
      // Retreiving the other hosts from the hosts.txt file;
      this(Files.readAllLines(Paths.get("hosts.txt")).stream()
         .map((s) -> s.split(" "))
         .toArray(String[][]::new), hostIndex);
   }

   /**
    * permet de créer une nouvelle tâche (le site devient actif si ce n'était pas le
    * cas)
    */
   private synchronized void newTask() {
      running = true;

      Task t = new Task();
      this.tasks.add(t);
      new Thread(t).start();
   }

   /**
    * Permet de gérer la réception d'un message appelé par UDPMessageHandler
    *
    * @param message le message reçu
    */
   @Override
   public void dataReceived(Message message) {
      switch (message.getMessageType()) {
         case START_TASK:
            StartTaskMessage startMessage = (StartTaskMessage) message;

            // on considère que le site est running si lui
            // ou un suivant effectue une tâche 
            running = true;

            if (startMessage.getSiteIndex() == localHostIndex) {
               log("START_TASK to handle receive");
               // La tache nous est destinée, on la traite
               this.newTask();
            } else {
               try {
                  log("START_TASK for " + (startMessage.getSiteIndex() + 1)
                     + " received, transmitting further");

                  // Sinon, on la transmet plus loin
                  this.udpMessageHandler.sendTo(startMessage, getNextSite());
               } catch (IOException ex) {
                  Logger.getLogger(DynamicThreadManager.class.getName())
                     .log(Level.SEVERE, null, ex);
               }
            }

            break;
         case TOKEN:
            // on passe en phase de terminaison (empêche le client
            // de créer de nouvelles tâches)
            endingPhase = true;

            TokenMessage tokenMessage = (TokenMessage) message;
            log("ENDING_TASK receive");

            if (isInitiator && tokenMessage.getInitiator() > localHostIndex) {
               /**
                * Si on est un site initiateur et que ce n'est pas notre index qui
                * est indiqué dans le jeton, cela signifie qu'un autre jeton est en
                * train de circuler. Dans le cas où ce jeton est d'un indice plus
                * élevé (pour départager celui qui doit être gardé), on ne transfère
                * pas le jeton plus loin et on attend le jeton que l'on a initié
                */

            } else if (isInitiator
               && tokenMessage.getInitiator() == localHostIndex && !running) {
               /**
                * Si on est un site initiateur et qu'il s'agit de notre jeton, tous
                * le monde a vu ce jeton et on peut peut-être arrêter la propagation
                * Si on est resté dans l'état inactif. Alors on termine l'application
                */
               log("App is now completed, sending END to everyone");

               try {
                  // On indique la terminaison de l'application au site suivant
                  EndMessage endMessage = new EndMessage(localHostIndex);
                  this.udpMessageHandler.sendTo(endMessage, getNextSite());
               } catch (IOException ex) {
                  Logger.getLogger(DynamicThreadManager.class.getName())
                     .log(Level.SEVERE, null, ex);
               }
            } else {
               // On attend la fin des tâches afin de commencer la terminaison
               this.waitForTaskEnds();

               // on transmet le jeton au site suivant
               try {
                  this.udpMessageHandler.sendTo(tokenMessage, getNextSite());
               } catch (IOException ex) {
                  Logger.getLogger(DynamicThreadManager.class.getName())
                     .log(Level.SEVERE, null, ex);
               }
            }

            break;
         case END:
            // L'application est terminée
            log("Application is terminated, shuting down");
            EndMessage endMessage = (EndMessage) message;

            if (endMessage.getInitiator() != localHostIndex) {
               // On doit retransmettre le message
               try {
                  this.udpMessageHandler.sendTo(endMessage, getNextSite());
               } catch (IOException ex) {
                  Logger.getLogger(DynamicThreadManager.class.getName())
                     .log(Level.SEVERE, null, ex);
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
               udpMessageHandler.sendTo(new StartTaskMessage(getRandomSiteTarget()), getNextSite());
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

   /**
    * On attend que toutes les tâches se terminent avant de poursuivre avec la
    * terminaison
    */
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

   /**
    * Permet d'obtenir un site cible parmi tous les hôtes sauf soi-même
    *
    * @return
    */
   private byte getRandomSiteTarget() {
      byte site = (byte) ThreadLocalRandom.current().nextInt(hosts.length - 1);

      return (byte) (site >= localHostIndex ? site + 1 : site);
   }

   /**
    * nous donne le site suivant sur l'anneau
    *
    * @return
    */
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
      // on ne lance pas de terminaison si une terminaison est en cours
      if (!endingPhase) {
         endingPhase = true;
         isInitiator = true;

         // on attend la fin des tâches en cours
         this.waitForTaskEnds();

         try {
            TokenMessage m = new TokenMessage(localHostIndex);
            this.udpMessageHandler.sendTo(m, getNextSite());
         } catch (IOException ex) {
            Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
         }
      }
   }

   public synchronized void initiateTask() {
      // on interdit la création de nouvelles tâches si une terminaison est en cours
      if (!endingPhase) {
         this.newTask();
      }
   }

   public static void main(String... args) {

      // Au lancement de l'application, on demande à l'utilisateur le
      // numéro du site courant
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

      // On lance le manager
      DynamicThreadManager manager = null;
      try {
         manager = new DynamicThreadManager((byte) (host - 1));
      } catch (IOException ex) {
         System.err.println("Problème lors de la création du manager");
         ex.printStackTrace();
         System.exit(-1);
      }

      // Saisie utilisateur pour le site courant
      boolean exit = false;
      do {
         System.out.println("\t1. Lancer une tâche");
         System.out.println("\t2. Initier la terminaison");
         System.out.println("Entrer le numéro correspondant à l'action voulue: ");

         try {
            int num = scanner.nextInt();

            if (num > 2 || num < 1) {
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
