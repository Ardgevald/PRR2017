package ch.heigvd.prr.election;

import ch.heigvd.prr.election.Message.EchoMessage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Remi
 */
public class App {

   private static final int ECHO_TIMEOUT = 2000;
   private static final int TIMER_MAX = 10000;

   private final ElectionManager electionManager;

   private final DatagramSocket socket;

   private final Thread sendEchosThread;

   public App(byte hostIndex) throws IOException {
      this.socket = new DatagramSocket();
      socket.setSoTimeout(ECHO_TIMEOUT);
      log("Creating electionManager");

      electionManager = new ElectionManager(hostIndex);
      log("ElectionManager created");

      // TODO UTiliser les tryWithRessource ici
      electionManager.startElection();

      Random random = new Random();
      // De temps en temps, on lance un echo
      sendEchosThread = new Thread(() -> {
         try {
            while (true) {
               synchronized (this) {
                  this.wait(random.nextInt(TIMER_MAX));
               }
               sendEcho();
            }
         } catch (InterruptedException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
         }
      });
   }

   public void start() {
      sendEchosThread.start();
   }

   public void sendEcho() {
      try {
         EchoMessage message = new EchoMessage();
         byte[] data = message.toByteArray();
         Site elected = electionManager.getElected();
         DatagramPacket packet = new DatagramPacket(data, data.length, elected.getSocketAddress());

         log("Sending echo to " + elected.getSocketAddress().getPort());
         socket.send(packet);
         try {
            socket.receive(packet);
            log("Echo succesfuly received");
         } catch (SocketTimeoutException e) {
            // Si on atteint pas le site
            log("Site actially down, starting another election");
            electionManager.startElection();
         }

      } catch (SocketException ex) {
         Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
      } catch (IOException | InterruptedException ex) {
         Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
      }
   }

   private void log(String s) {
      SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

      Date resultdate = new Date(System.currentTimeMillis());
      String time = sdf.format(resultdate);
      
      System.out.println(String.format("%s - %s (%s:%d): %s",
         time,
         "App",
         socket.getLocalAddress().getHostAddress(),
         socket.getPort(),
         s));
   }

   public static void main(String... args) throws IOException {

      if (args.length < 1) {
         System.err.println("Il manque le numero de site en argument");
      } else {
         new App(Byte.valueOf(args[0])).start();
      }
   }
}
