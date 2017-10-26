package PTP;

import java.io.IOException;
import java.net.DatagramPacket;
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

   private InetAddress masterAddress;

   private final TimerTask syncTask = new TimerTask() {

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
            byte id;

            socket = new MulticastSocket(BROADCAST_PORT);
            InetAddress group = InetAddress.getByName(GROUP_ADDRESS);
            socket.joinGroup(group);
            
            DatagramPacket paquet = new DatagramPacket(buffer, BUFFER_SIZE);

            do {
               // wait for Master
               socket.receive(paquet);

               if (paquet.getLength() == 2 && paquet.getData()[0] == 0) {
                  id = paquet.getData()[1];
                  masterAddress = paquet.getAddress();
               }
            } while (masterAddress == null);
            
            // Wait for FollowUp
            socket.receive(paquet);
            

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
      sync.join();
   }

   public static void main(String ... args) throws IOException{
	   SlavePTP p = new SlavePTP();
   }
   
}
