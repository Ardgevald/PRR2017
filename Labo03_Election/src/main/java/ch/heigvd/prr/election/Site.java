package ch.heigvd.prr.election;

import java.net.InetSocketAddress;

/**
 * Class représentant un site
 *
 * @author Rémi Jacquemard
 * @author Miguel Pombo Dias
 */
public class Site implements Comparable<Site> {

   // l'addresse du site
   private InetSocketAddress socketAddress;

   // l'aptitude du site
   private int apptitude;

   /**
    * Constructeur du site avec l'ip, le port et son aptitude
    *
    * @param ip
    * @param port
    * @param aptitude
    */
   public Site(String ip, int port, int aptitude) {
      this.socketAddress = new InetSocketAddress(ip, port);
      this.apptitude = aptitude;
   }

   /**
    * Constructeur du site sans l'aptitude (par défaut à 0)
    *
    * @param ip
    * @param port
    */
   public Site(String ip, int port) {
      this(ip, port, 0); // De base, une aptitude de 0 --> "non connu"
   }

   /**
    * récupèration de l'addresse
    * @return
    */
   public InetSocketAddress getSocketAddress() {
      return socketAddress;
   }

   /**
    * Récupération de l'aptitude
    * @return 
    */
   public int getApptitude() {
      return apptitude;
   }

   /**
    * Modification de l'addresse
    * @param socketAddress 
    */
   public void setSocketAddress(InetSocketAddress socketAddress) {
      this.socketAddress = socketAddress;
   }

   /**
    * Modification de l'aptitude
    * @param apptitude 
    */
   public void setApptitude(int apptitude) {
      this.apptitude = apptitude;
   }

   /**
    * Méthode permettant de comparer deux sites par leur aptitude et obtient
    * leur différence
    * @param t le site à comparer à celui-ci
    * @return la différence 
    */
   @Override
   public int compareTo(Site t) {
      int currentComparaison = t.apptitude - this.apptitude;
      if (currentComparaison != 0) {
         return currentComparaison;
      } else {
         // Egalité au niveau des apptitudes, on départage par rapport a l'ip
         byte[] curSiteAddress = this.socketAddress.getAddress().getAddress();
         byte[] otherSiteAddress = t.socketAddress.getAddress().getAddress();

         for (int i = 0; i < curSiteAddress.length; i++) {
            currentComparaison = otherSiteAddress[i] - curSiteAddress[i];
            if (currentComparaison != 0) {
               return currentComparaison;
            }
         }

      }

      return 0;
   }

}
