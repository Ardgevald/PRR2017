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
 * Cette classe permet de gérer une élection pour un site donné
 * 
 * Il y a plusieurs cas de figures possibles
 *    - On construit cette classe en lui donnant les sites existants
 *    - On ne donne pas les sites, ce qui nécessite d'avoir un fichier
 *    hosts.txt au même endroit que l'application contenant un hôte
 *    par ligne, décrit par son addresse et son port
 * 
 * Le client doit commencer l'utilisation de la classe en 
 * appelant la méthode startElection()
 * 
 * Ensuite, il peut faire appel à la méthode getElected() qui lui donnera
 * le site élu dès qu'il est disponible
 * 
 * Son comportement est d'attendre un message en permanence pour être
 * prêt à continuer une élection
 * 
 * Il peut en lancer une à tout moment, on évite d'en lancer une si on sait
 * qu'une autre est déjà en cours, sauf dans le cas où on est lancé pour
 * la première fois (le nouveau site peut avoir une aptitude plus élevée)
 * 
 * @author Rémi Jacquemard
 * @author Miguel Pombo Dias
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

   /**
    * Classe de log qui permet d'afficher en console des messages datés avec la
    * classe qui a produit le message
    *
    * @param s le message à afficher
    */
   private void log(String s) {

      SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

      Date resultdate = new Date(System.currentTimeMillis());
      String time = sdf.format(resultdate);

      System.out.println(String.format("%s - %s (%d): %s",
         time,
         "ElectionManager",
         localSite.getSocketAddress().getPort(),
         s));
   }

   /**
    * Constructeur principal de l'ElectionManager
    * On met en place tout ce qui est nécessaire à traiter un message d'une élection
    * On ne commence pas encore le processus ici
    *
    * @param hosts            Un tableau des sites
    * @param hostIndex
    * @throws SocketException
    */
   public ElectionManager(Site[] hosts, byte hostIndex) throws SocketException, IOException {
      this.hosts = hosts;
      this.localSite = hosts[hostIndex];
      this.localHostIndex = hostIndex;

      // On calcule un temps de timeout pour l'obtention d'une élection proportionnel
      // au nombre de sites et au temps de timeout de chacun
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
                  // Reception d'un message de type Annonce
                  case ANNOUNCE:
                     log("ANNOUNCE received");
                     AnnounceMessage announceMessage = (AnnounceMessage) message;

                     log("Annonce : " + announceMessage.getApptitudes().toString());

                     // On vérifie l'annonce
                     if (announceMessage.getApptitude(localHostIndex) != null) {
                        /*
                           Ici, on a déjà écrit notre aptitude dans ce message, donc
                           on détermine qui est l'élu et on envoie les résultats
                           aux sites suivants
                         */
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

                        /*
                         On envoie les résultats
                         Dans un nouveau thread afin de se débloquer d'ici
                         dans le cas où on s'envoit à soit même le résultat
                         */
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

                     } else {
                        // On recoit pour la première fois le message
                        // On le met à jour avant de le transmettre au site suivant
                        log("Updating apptitude and transmitting further");
                        currentPhase = Phase.ANNOUNCE;
                        // On met à jour la liste
                        announceMessage.setApptitude(localHostIndex, computeLocalAptitude());

                        // On le retransmet
                        sendQuittancedMessageToNext(announceMessage);

                     }
                     break;
                  // Reception d'un message de type Résultat
                  case RESULTS:
                     
                     log("RESULTS received");
                     ResultsMessage resultsMessage = (ResultsMessage) message;

                     log("Result : " + resultsMessage.getSeenSites().toString());

                     if (resultsMessage.getSeenSites().contains(localHostIndex)) {
                        /*
                           Si le résultat est déjà connu, alors on ne fait
                           qu'arrêter la propagation, rien d'autre
                        */
                        log("############## ELECTIONS ENDED ############");
                        currentPhase = null;
                     } else if (currentPhase == Phase.RESULT && getSiteIndex(elected) != resultsMessage.getElectedIndex()) {
                        /* 
                           Ici, c'est un résultat qu'on a pas vu et qui n'était pas
                           attendu, il y a une incohérence sur celui qui est élu
                           On relance donc une élection
                        */
                        log("Incoherent result : starting new election");
                        startElectionLocal();

                     } else if (currentPhase == Phase.ANNOUNCE) {
                        /*
                           Dans ce cas, on a vu passer une annonce et on a reçu
                           un résultat, on en prend note, et on considère que
                           l'on ne recevra plus de message
                        */
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
                  // réception d'un message Echo
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

   /**
    * Constructeur avec un tableau de tableaux de strings
    * On convertit le tableau de tableau en un tableau de sites avant de le transmettre
    * @param hosts            le tableau des hôtes en String
    * @param hostIndex        l'indice de l'hôte courant
    * @throws SocketException
    * @throws IOException 
    */
   public ElectionManager(String[][] hosts, byte hostIndex) throws SocketException, IOException {
      this(Arrays.stream(hosts)
         .map((s) -> new Site(s[0], Integer.parseInt(s[1])))
         .toArray(Site[]::new), hostIndex);
   }

   /**
    * Constructeur sans les hôtes que l'on récupère dans le ficher prévu
    * à cet effet
    * @param hostIndex
    * @throws IOException 
    */
   public ElectionManager(byte hostIndex) throws IOException {
      // Retreiving the other hosts from the hosts.txt file;
      this(Files.readAllLines(Paths.get("hosts.txt")).stream()
         .map((s) -> s.split(" "))
         .toArray(String[][]::new), hostIndex);
   }

   /**
    * Méthode permettant de récupérer l'indice du site à partir d'un site
    * @param site le site dont on veut obtenir l'identifiant
    * @return l'identifiant du site
    */
   private byte getSiteIndex(Site site) {
      for (int i = 0; i < hosts.length; i++) {
         if (hosts[i] == site) {
            return (byte) i;
         }
      }

      // Si on ne trouve pas l'incide, on a un souci
      throw new IllegalArgumentException("The site passed in parameters could not be found");
   }

   /**
    * calcul de l'aptitude du site en utilisant le port utilisé ainsi qu'une
    * partie de l'addresse ip.
    * 
    * Cette méthode peut-être surchargée par une sous-classe pour avoir un comportement
    * différent si nécessaire
    * 
    * @return un nombre indiquant l'aptitude d'une machine
    */
   private int computeLocalAptitude() {
      return serverSocket.getLocalAddress().getAddress()[3] + serverSocket.getLocalPort();
   }

   /**
    * Cette méthode permet du côté applicatif de lancer une élection
    * On prépare l'electionListener pour gérer les messages entrants une seule fois
    * et on évite de lancer une élection si une est déjà en cours
    * 
    * La méthode startElectionLocal() est utilisée pour la gestion même de l'élection
    * 
    * @throws IOException 
    */
   public void startElection() throws IOException {

      // Thread de réception des messages
      if (!electionListener.isAlive()) {
         // Launching the listening thread
         electionListener.start();
      }

      // attente en cas d'élection en cours
      synchronized (locker) {
         // on évite de relancer une élection si déjà en cours
         if (currentPhase != null) {
            log("Starting an election, but an election is already running");
            return;
         }

         // lancement de l'élection à proprement parler
         startElectionLocal();

      }
   }

   /**
    * On commence ici le processus de l'élection en commencant pas une annonce
    */
   private void startElectionLocal() {
      try {
         log("############# Starting an election ############");
         
         // Changement de phase pour de l'annonce
         currentPhase = Phase.ANNOUNCE;

         // On envoie un message d'annonce avec notre aptitude
         AnnounceMessage announceMessage = new AnnounceMessage();
         announceMessage.setApptitude(localHostIndex, computeLocalAptitude());

         // on envoie au prochain qui veut bien répondre
         sendQuittancedMessageToNext(announceMessage);
      } catch (IOException ex) {
         Logger.getLogger(ElectionManager.class.getName()).log(Level.SEVERE, null, ex);
      }

      log("Announced message sent");
   }

   /**
    * Lors de la fermeture de l'ElectionManager, on interromp le thread de réception
    * et on ferme le socket serveur
    * @throws IOException 
    */
   @Override
   public void close() throws IOException {
      log("Closing connection");
      serverSocket.close();

      electionListener.interrupt();

      log("Everything's closed");
   }

   /**
    * Méthode permettant d'obtenir le site élu
    * Si le site n'est pas encore décidé, la méthode est bloquante jusqu'à ce qu'un
    * élu soit disponible
    * @return  le site qui a été élu
    * @throws InterruptedException 
    */
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

   /**
    * permet d'envoyer un message à un site et demande une quittance
    * Si le site n'est pas atteignable, on envoie au site suivant jusqu'à
    * ce qu'un des sites réponde
    * 
    * Dans le pire des cas, le site émetteur répond à son propre message
    * @param message
    * @throws IOException 
    */
   private void sendQuittancedMessageToNext(Message message) throws IOException {
      log("Sending message " + message.getMessageType());
      boolean unreachable;
      // on récupère le site suivant à contacter
      neighbor = hosts[(localHostIndex + 1) % hosts.length];
      
      do {
         unreachable = false;
         try {
            sendQuittancedMessage(message, neighbor);
         } catch (UnreachableRemoteException ex) {
            log("Neigbor unreachable, trying next");
            unreachable = true;
            // si le site n'est pas atteignable, on contacte le site suivant
            neighbor = hosts[(1 + getSiteIndex(neighbor)) % hosts.length];
         }
      } while (unreachable);

   }

   /**
    * Envoie un message à une certaine addresse
    * @param message le message à envoyer
    * @param socketAddress l'addresse pour l'envoi du message
    * @throws IOException 
    */
   private void sendMessage(Message message, SocketAddress socketAddress) throws IOException {
      DatagramPacket packet = new DatagramPacket(message.toByteArray(), message.toByteArray().length, socketAddress);
      timedoutSocket.send(packet);
   }

   /**
    * Envoie un message à un site
    * @param message le message à envoyer
    * @param site le site à qui envoyer
    * @throws IOException 
    */
   private void sendMessage(Message message, Site site) throws IOException {
      sendMessage(message, site.getSocketAddress());
   }

   /**
    * Envoi d'u nmessage avec attente de la quittance
    * @param message le message à envoyer
    * @param site le site à qui envoyer
    * @throws IOException
    * @throws ch.heigvd.prr.election.ElectionManager.UnreachableRemoteException Si le site n'est pas atteignable
    */
   private void sendQuittancedMessage(Message message, Site site) throws IOException, UnreachableRemoteException {
      sendMessage(message, site);

      // on a un timeout pour l'envoi de message
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
   
   /**
    * Réception d'un message et envoi de la quittance
    * @return le message reçu
    * @throws IOException
    */
   private Message receiveAndQuittanceMessage() throws IOException {
      int maxSize = Message.getMaxMessageSize(hosts.length);
      DatagramPacket packet = new DatagramPacket(new byte[maxSize], maxSize);
      serverSocket.receive(packet);
      
      // On transmet la quittance
      QuittanceMessage quittanceMessage = new QuittanceMessage();
      sendMessage(quittanceMessage, packet.getSocketAddress());

      return Message.parse(packet.getData(), packet.getLength());
   }

   /**
    * Réception d'un message quelconque
    * @return le message reçu
    * @throws IOException 
    */
   private Message receiveMessage() throws IOException {
      int maxSize = Message.getMaxMessageSize(hosts.length);
      DatagramPacket packet = new DatagramPacket(new byte[maxSize], maxSize);
      serverSocket.receive(packet);

      return Message.parse(packet.getData(), packet.getLength());
   }

  /**
   * 
   * @return
   * @throws IOException
   * @throws SocketTimeoutException 
   */
   private Message receiveTimeoutMessage() throws IOException, SocketTimeoutException {
      int maxSize = Message.getMaxMessageSize(hosts.length);
      DatagramPacket packet = new DatagramPacket(new byte[maxSize], maxSize);
      timedoutSocket.receive(packet);

      return Message.parse(packet.getData(), packet.getLength());

   }

   /**
    * Exception pour le traitement des sites non atteignables
    */
   private static class UnreachableRemoteException extends Exception {

      public UnreachableRemoteException() {
         super();
      }

      public UnreachableRemoteException(Exception e) {
         super(e);
      }
   }
}
