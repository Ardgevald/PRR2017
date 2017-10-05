package PTPSlave;

import java.io.Console;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Représente un maître PTP
 *
 * @author Remi
 */
public class SlavePTP {

   private static final int BROADCAST_PORT = 1234;
   private static final String GROUP_ADDRESS = "234.56.78.9";
   private static final int TIMEOUT = 4000;

   private static final int BUFFER_SIZE = 2;

   private Boolean running = true;

   private TimerTask syncTask = new TimerTask() {

      private byte id = 0;

      @Override
      public void run() {

      }
   };
   private final Timer syncTimer = new Timer();

   private final Thread sync = new Thread(new Runnable() {
      @Override
      public void run() {
         MulticastSocket socket;

         try {

            byte[] buffer = new byte[BUFFER_SIZE];

            socket = new MulticastSocket(BROADCAST_PORT);
            socket.setSoTimeout(TIMEOUT);
            InetAddress group = InetAddress.getByName(GROUP_ADDRESS);
            socket.joinGroup(group);

            while (running) {
               // wait for Master
               DatagramPacket paquet = new DatagramPacket(buffer, BUFFER_SIZE);
               socket.receive(paquet);
            }

         } catch (IOException ex) {
            Logger.getLogger(SlavePTP.class.getName()).log(Level.SEVERE, null, ex);
         }
      }
   });

   private void startThread() {
      System.out.println("Thread start");
      sync.start();
      System.out.println("Thread started");
   }

   public SlavePTP() throws SocketException, IOException {
      //Diffusion des messages sync et followup
      startThread();

      //syncTimer.scheduleAtFixedRate(syncTask, 0, SYNC_PERIOD);
   }

   @Override
   protected void finalize() throws Throwable {
      super.finalize();
      running = false;
      sync.join();
   }

}
