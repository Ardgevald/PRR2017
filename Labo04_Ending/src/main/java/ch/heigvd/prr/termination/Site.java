package ch.heigvd.prr.termination;

import java.net.InetSocketAddress;

/**
 * Class représentant un site
 *
 * @author Rémi Jacquemard
 * @author Miguel Pombo Dias
 */
public class Site {

   // l'addresse du site
   private InetSocketAddress socketAddress;

	/**
	 * Constructeur du site
	 * @param socketAddress l'adresse du site (dont le port)
	 */
	public Site(InetSocketAddress socketAddress){
		this.socketAddress = socketAddress;
	}
	
   /**
    * Constructeur du site
    *
    * @param ip l'adresse ip (ou hostname) du site
    * @param port le port du site
    */
   public Site(String ip, int port) {
      this(new InetSocketAddress(ip, port));
		
   }

   /**
    * récupèration de l'addresse
    * @return
    */
   public InetSocketAddress getSocketAddress() {
      return socketAddress;
   }

   /**
    * Modification de l'addresse
    * @param socketAddress 
    */
   public void setSocketAddress(InetSocketAddress socketAddress) {
      this.socketAddress = socketAddress;
   }

}
