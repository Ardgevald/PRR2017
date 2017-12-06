package ch.heigvd.lamportmanager;

import ch.heigvd.interfacesrmi.IGlobalVariable;
import ch.heigvd.interfacesrmi.ILamportAlgorithm;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
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

   private final Object lock = new Object();

   String[][] remotes;

   private static class Message {

      public static enum MESSAGE_TYPE {
         REQUEST, RESPONSE, LIBERATE
      };
      private final MESSAGE_TYPE messageType;
      private final long time;

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

   public static void main(String... args) throws RemoteException, IOException {
      // Creating hosts
      LamportManager[] lamportManagers = {
         new LamportManager(0), new LamportManager(1), new LamportManager(2)
      };

      // Connecting hosts
      Arrays.stream(lamportManagers).forEach(LamportManager::connectToRemotes);
   }

   public LamportManager(int hostIndex) throws IOException {
      this.hostIndex = hostIndex;

      globalVariable = 0;

      // Retreiving the other hosts
      remotes = Files.readAllLines(Paths.get("hosts.txt")).stream()
              .map((s) -> s.split(" "))
              .toArray(String[][]::new);
      this.nbSites = remotes.length;

      this.lamportServers = new ILamportAlgorithm[nbSites];

      // On initialise les messages reçu à un temps 0 avec des liberates
      this.messagesArray = new Message[nbSites];
      for (int i = 0; i < messagesArray.length; i++) {
         this.messagesArray[i] = new Message(Message.MESSAGE_TYPE.LIBERATE, 0);
      }

      try {

         // Retreiving our port address
         int portUsed = Integer.parseInt(remotes[hostIndex][1]);

         // Creating local server
         Registry registry = LocateRegistry.createRegistry(portUsed);
         
         // On lie dans le registry
         IGlobalVariable globalVariableServer = new GlobalVariableServer();
         registry.rebind(VARIABLE_SERVER_NAME, globalVariableServer);

         ILamportAlgorithm lamportAlgorithmServer = new LamportAlgorithmServer();
         registry.rebind(LAMPORT_SERVER_NAME, lamportAlgorithmServer);

         System.out.println("RMI registry on " + portUsed + " with bindings:");
         Arrays.stream(registry.list()).forEach(System.out::println);

      } catch (RemoteException ex) {
         Logger.getLogger(LamportManager.class.getName()).log(Level.SEVERE, null, ex);
      }
   }

   public void connectToRemotes() {
      // Connecting to other hosts
      for (int i = 0; i < remotes.length; i++) {
         String[] currentHost = remotes[i];
         try {
            ILamportAlgorithm remoteServer = (ILamportAlgorithm) Naming.lookup("//" + currentHost[0] + ":" + currentHost[1] + "/" + LAMPORT_SERVER_NAME);
            lamportServers[i] = remoteServer;
         } catch (NotBoundException | MalformedURLException | RemoteException ex) {
            Logger.getLogger(LamportManager.class.getName()).log(Level.SEVERE, null, ex);
         }
      }

      System.out.println("Remotes connected !");
   }

   private void sendRequestsAndProcessResponse(final long localTimeStamp) throws InterruptedException {
      // On set notre message courant
      messagesArray[hostIndex] = new Message(Message.MESSAGE_TYPE.REQUEST, localTimeStamp);

      Thread[] senderThreads = new Thread[lamportServers.length];

      // Création des threads d'envoi des requêtes
      for (int i = 0; i < senderThreads.length; i++) {
         final int index = i;
         senderThreads[i] = new Thread(() -> {
            try {
               // On envoie à tout le monde
               /**
                * NOTE IMPORTANTE : on envoie aussi à soit même, ce qui a pour effet
                * d'y ajouter notre requête dans la liste local
                */
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
      public synchronized int getVariable() throws RemoteException {
         return globalVariable;
      }

      @Override
      public synchronized void setVariable(int value) throws RemoteException {
         System.out.println(hostIndex + " : WaitForCS");
         // Demande de section critique
         waitForCS();
         System.out.println(hostIndex + " : InCS - ");
         // On est ici en section critique
         globalVariable = value;

         // Relachement de la section critique				
         releaseCS();
         System.out.println(hostIndex + " : ReleaseCS - ");
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
      public void free(long remoteTimeStamp, int value, int hostIndex) throws RemoteException {
         globalVariable = value;

         // On met à jour le temps local
         increaseTime(remoteTimeStamp);

         synchronized (lock) {
            // On met à jour les messages reçu
            handleMessageReceived(hostIndex, new Message(Message.MESSAGE_TYPE.LIBERATE, remoteTimeStamp));

            // On notifie si on souhaitait, par hasard, entrer en section critique
            if (canEnterCS()) {
               lock.notify();
            }
         }
      }

   }

   private void waitForCS() {
      try {
         sendRequestsAndProcessResponse(localTimeStamp);
         /*
         * On calcule si on peut entrer en SC
         * On reste bloqué tant qu'on peut pas entrer en SC
          */
         synchronized (lock) {
            if (!canEnterCS()) {
               // Attendre jusqu'à ce qu'on soit notifié lors de l'arrivée d'un message
               lock.wait();
            }
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
      System.out.print(hostIndex);
      for (Message message : messagesArray) {
         if (message.time < minTime) {
            minTime = message.time;
         }
         
         System.out.print("[" + message.messageType + "-" + message.time + "]");
      }

         System.out.println("/\n");
      /**
       * "Un processus Pi se donne le droit d'entrer en section critique lorsque
       * file(i).msgType = REQUETE et que son estampille est la plus ancienne des
       * messages contenus dans file(i)."
       */
      return messagesArray[hostIndex].messageType == Message.MESSAGE_TYPE.REQUEST
              && messagesArray[hostIndex].time == minTime;
   }

}
