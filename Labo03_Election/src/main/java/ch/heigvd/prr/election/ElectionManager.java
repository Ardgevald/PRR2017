package ch.heigvd.prr.election;

import ch.heigvd.prr.election.Message.AnnounceMessage;
import ch.heigvd.prr.election.Message.MessageType;
import ch.heigvd.prr.election.Message.QuittanceMessage;
import ch.heigvd.prr.election.Message.ResultsMessage;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Remi
 */
public class ElectionManager implements Closeable {

   private static final int QUITTANCE_TIMEOUT = 1000;
   private static final double ELECTION_TIMEOUT_FACTOR = 1.5;
   private final int electionTimeout;

   private Site[] hosts;
   private final Site localSite;

   // hostIndex en byte, vu qu'il n'y en a maximum que 4
   private final byte localHostIndex;
   private Site neighbor;
   private Site elected = null;

   private DatagramSocket serverSocket;
   private DatagramSocket timedoutSocket;

   private final Object locker = new Object();

   private enum Phase {
      ANNOUNCE, RESULT
   };
   private Phase currentPhase = null;

   private Thread electionListener;

   private void log(String s) {

      SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

      Date resultdate = new Date(System.currentTimeMillis());
      String time = sdf.format(resultdate);

      System.out.println(String.format("%s - %s (%d): %s",
         time ,
         "ElectionManager",
         localSite.getSocketAddress().getPort(),
         s));
   }

   /**
    * Main constructor
    *
    * @param hosts
    * @param hostIndex
    * @throws SocketException
    */
   public ElectionManager(Site[] hosts, byte hostIndex) throws SocketException, IOException {
      this.hosts = hosts;
      this.localSite = hosts[hostIndex];
      this.localHostIndex = hostIndex;

      electionTimeout = (int) (ELECTION_TIMEOUT_FACTOR * hosts.length * QUITTANCE_TIMEOUT);

      log("Starting Election Manager");

      log("Creating DatagramSocket");
      // Creating the main socket
      serverSocket = new DatagramSocket(localSite.getSocketAddress());
      timedoutSocket = new DatagramSocket();
      timedoutSocket.setSoTimeout(QUITTANCE_TIMEOUT);

      this.electionListener = new Thread(() -> {
         try {
            while (true) {
               // We are waiting for an income packet
               Message message = receiveAndQuittanceMessage();
               MessageType messageType = message.getMessageType();

               // On gère le message reçu
               switch (messageType) {
                  case ANNOUNCE:
                     log("ANNOUNCE received");
                     AnnounceMessage announceMessage = (AnnounceMessage) message;

                     // On vérifie l'annonce
                     if (announceMessage.getApptitude(localHostIndex) != null) {
                        // Ici, on a déjà écrit notre aptitude dans ce message
                        // On recoit pour la deuxième fois l'annonce, on doit chercher l'élu
                        log("2nd time I received this message - Passing to get RESULTS");

                        currentPhase = Phase.RESULT;

                        announceMessage.getApptitudes().entrySet().forEach((entry) -> {
                           Byte index = entry.getKey();
                           Integer apptitude = entry.getValue();

                           hosts[index].setApptitude(apptitude);
                        });

                        // On utilise la comparaison native des sites
                        elected = Arrays.stream(hosts)
                           .sorted()
                           .findFirst()
                           .get();

                        // On envoie les résultats
                        // Dans un nouveau thread afin de se débloquer d'ici
                        // dans le cas où on s'envoit à soit même le résultat
                        ResultsMessage result = new ResultsMessage(getSiteIndex(elected));
                        result.addSeenSite(localHostIndex);

                        new Thread(() -> {
                           try {
                              sendQuittancedMessageToNext(result);
                           } catch (IOException ex) {
                              Logger.getLogger(ElectionManager.class.getName()).log(Level.SEVERE, null, ex);
                           }
                        }).start();

                        synchronized (locker) {
                           locker.notifyAll();
                        }

                     } else { // On recoit pour la première fois le message
                        log("Updating apptitude and transmitting further");
                        currentPhase = Phase.ANNOUNCE;
                        // On met à jour la liste
                        announceMessage.setApptitude(localHostIndex, computeLocalApptitude());

                        // On le retransmet
                        sendQuittancedMessageToNext(announceMessage);

                     }
                     break;
                  case RESULTS:
                     log("RESULTS received");
                     ResultsMessage resultsMessage = (ResultsMessage) message;

                     // Si j'ai déjà vu ce message, alors on arrête la propagation
                     if (resultsMessage.getSeenSites().contains(localHostIndex)) {
                        // On ne fait qu'arrêter la propagation, rien d'autre
                        log("############## ELECTIONS ENDED ############");
                        currentPhase = null;
                     } else if (currentPhase == Phase.RESULT && getSiteIndex(elected) != resultsMessage.getElectedIndex()) {
                        // Ici, c'est un résultat qu'on a pas vu et qui n'était pas attendu
                        // On relance une élection
                        log("Incoherent result : starting new election");
                        startElectionLocal();

                     } else if (currentPhase == Phase.ANNOUNCE) {
                        
                        currentPhase = null;
                        
                        // On peut traiter normalement le résultat ici
                        log("Receiving first result, getting elected site and transmitting further");
                        elected = hosts[resultsMessage.getElectedIndex()];

                        
                        synchronized (locker) {
                           locker.notifyAll();
                        }

                        // On s'ajoute à la liste des gens qui ont vu ce message
                        resultsMessage.addSeenSite(localHostIndex);
                        sendQuittancedMessageToNext(resultsMessage);
                        
                     }

                     break;
                  case ECHO:
                     log("ECHO RECEIVED");
                     // On ne fait rien, la quittance a déjà été envoyée
                     break;
               }

            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }

      });
   }

   public ElectionManager(String[][] hosts, byte hostIndex) throws SocketException, IOException {
      this(Arrays.stream(hosts)
         .map((s) -> new Site(s[0], Integer.parseInt(s[1])))
         .toArray(Site[]::new), hostIndex);
   }

   public ElectionManager(byte hostIndex) throws IOException {
      // Retreiving the other hosts from the hosts.txt file;
      this(Files.readAllLines(Paths.get("hosts.txt")).stream()
         .map((s) -> s.split(" "))
         .toArray(String[][]::new), hostIndex);
   }

   private byte getSiteIndex(Site site) {
      for (int i = 0; i < hosts.length; i++) {
         if (hosts[i] == site) {
            return (byte) i;
         }
      }

      // Nothing has been found here
      throw new IllegalArgumentException("The site passed in parameters could not be found");
   }

   private int computeLocalApptitude() {
      return serverSocket.getLocalAddress().getAddress()[3] + serverSocket.getLocalPort();
   }

   public void startElection() throws IOException {

      if (!electionListener.isAlive()) {
         // Launching the listening thread
         electionListener.start();
      }

      synchronized (locker) {
         if (currentPhase != null) {
            log("Starting an election, but an election is already running");
            return;
         }

         startElectionLocal();

      }
   }

   private void startElectionLocal() {
      try {
         log("############# Starting an election ############");
         // Setting basic values
         //isAnnouncing = true;

         currentPhase = Phase.ANNOUNCE;

         // Getting the neighbor
         //neighbor = hosts[(getSiteIndex(localSite) + 1) % hosts.length];

         //-- Preparing the message
         AnnounceMessage announceMessage = new AnnounceMessage();
         announceMessage.setApptitude(localHostIndex, computeLocalApptitude());

         sendQuittancedMessageToNext(announceMessage);
      } catch (IOException ex) {
         Logger.getLogger(ElectionManager.class.getName()).log(Level.SEVERE, null, ex);
      }

      log("Announced message sent");
   }

   @Override
   public void close() throws IOException {
      log("Closing connection");
      serverSocket.close();
      // TODO arrêter la boucle

      electionListener.interrupt();

      log("Everything's closed");
   }

   public Site getElected() throws InterruptedException {
      log("Someone want to get the chosen one");
      // Waiting if there is currently an election to get the new site
      synchronized (locker) {
         while (currentPhase == Phase.ANNOUNCE) {
            log("Waiting for the elected site");

            locker.wait(electionTimeout);
            if (currentPhase == Phase.ANNOUNCE) {
               startElectionLocal();
            }

            log("Locker released to get the elected site");
         }
      }

      return elected;
   }

   private void sendQuittancedMessageToNext(Message message) throws IOException {
      log("Sending message " + message.getMessageType());
      boolean unreachable;
      neighbor = hosts[(localHostIndex + 1) % hosts.length];
      do {
         unreachable = false;
         try {
            sendQuittancedMessage(message, neighbor);
         } catch (UnreachableRemoteException ex) {
            log("Neigbor unreachable, trying next");
            unreachable = true;
            neighbor = hosts[(1 + getSiteIndex(neighbor)) % hosts.length];
         }
      } while (unreachable);

   }

   private void sendMessage(Message message, SocketAddress socketAddress) throws IOException {
      DatagramPacket packet = new DatagramPacket(message.toByteArray(), message.toByteArray().length, socketAddress);
      timedoutSocket.send(packet);
   }

   private void sendMessage(Message message, Site site) throws IOException {
      sendMessage(message, site.getSocketAddress());
   }

   private void sendQuittancedMessage(Message message, Site site) throws IOException, UnreachableRemoteException {
      sendMessage(message, site);

      try {
         Message m = receiveTimeoutMessage();
         if (m.getMessageType() == Message.MessageType.QUITTANCE) {
            // Le message reçu via ce socket ne peut être que la réponse du message
            // envoyé juste au dessus
         } else {
            throw new UnreachableRemoteException();
         }
      } catch (SocketTimeoutException e) {
         // Si on atteint pas le site
         throw new UnreachableRemoteException(e);
      }

   }

   private Message receiveAndQuittanceMessage() throws IOException {
      int maxSize = Message.getMaxMessageSize(hosts.length);
      DatagramPacket packet = new DatagramPacket(new byte[maxSize], maxSize);
      serverSocket.receive(packet);

      // On transmet la quittance
      QuittanceMessage quittanceMessage = new QuittanceMessage();
      sendMessage(quittanceMessage, packet.getSocketAddress());

      return Message.parse(packet.getData(), packet.getLength());
   }

   private Message receiveMessage() throws IOException {
      int maxSize = Message.getMaxMessageSize(hosts.length);
      DatagramPacket packet = new DatagramPacket(new byte[maxSize], maxSize);
      serverSocket.receive(packet);

      return Message.parse(packet.getData(), packet.getLength());
   }

   private Message receiveTimeoutMessage() throws IOException, SocketTimeoutException {
      int maxSize = Message.getMaxMessageSize(hosts.length);
      DatagramPacket packet = new DatagramPacket(new byte[maxSize], maxSize);
      timedoutSocket.receive(packet);

      return Message.parse(packet.getData(), packet.getLength());

   }

   private static class UnreachableRemoteException extends Exception {

      public UnreachableRemoteException() {
         super();
      }

      public UnreachableRemoteException(Exception e) {
         super(e);
      }
   }
}
