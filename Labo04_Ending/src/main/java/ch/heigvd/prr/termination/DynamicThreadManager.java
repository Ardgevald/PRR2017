package ch.heigvd.prr.termination;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class DynamicThreadManager {

   private final int P = 10;
   private final int MINIMUM_WAIT = 5000;
   private final int MAXIMUM_WAIT = 15000;

   private final int numberOfSites;
   private final int siteNumber;
   
   private boolean newThreadForbidden = false;

   public DynamicThreadManager(int numberOfSites, int siteNumber) {
      this.numberOfSites = numberOfSites;
      this.siteNumber = siteNumber;
   }
   
   public void forbidNewThreads(){
      newThreadForbidden = true;
   }
   
   public void newTask(){
      new DynamicThread().run();
   }

   private class DynamicThread implements Runnable {

      @Override
      public void run() {
         boolean beginNewTask;
         do {

            try {
               Thread.sleep(ThreadLocalRandom.current().nextInt(MINIMUM_WAIT, MAXIMUM_WAIT));
            } catch (InterruptedException ex) {
               Logger.getLogger(DynamicThreadManager.class.getName()).log(Level.SEVERE, null, ex);
               return;
            }

            if(newThreadForbidden){
               break;
            }
               
            beginNewTask = ThreadLocalRandom.current().nextInt(100) < P;

            if (beginNewTask) {
               // démarrer un nouveau thread sur un autre site
            }

         } while (!beginNewTask);
      }
   }

   private int getSiteTarget() {
      int site = ThreadLocalRandom.current().nextInt(numberOfSites - 1);

      return (site >= siteNumber ? site + 1 : site);
   }

}
