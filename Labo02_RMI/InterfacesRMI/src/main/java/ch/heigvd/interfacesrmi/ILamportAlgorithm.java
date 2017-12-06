package ch.heigvd.interfacesrmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface pour la communication RMI entre les serveurs. Ces deux méthodes
 * permettent d'implémenter l'algorithme de Lamport en remplacement des
 * messages.
 *
 * @author Miguel Pombo Dias
 * @author Rémi Jacquemard
 */
public interface ILamportAlgorithm extends Remote{
   /**
    * Cette méthode permet de faire une requête pour l'accès à une section
    * critique en envoyant une estampille et un identifiant
    * (équivalent du message REQUETE) et attend en retour un timestamp
    * qui remplace le message QUITTANCE.
    * 
    * @param localTimeStamp   Temps logique de l'appelant lors de l'envoi
    * @param hostIndex        Identifiant de l'appelant
    * @return                 Temps logique de l'appelé avec la quittance
    * @throws RemoteException En cas d'erreut de communication distante
    */
   public long request(long localTimeStamp, int hostIndex) throws RemoteException;
   
   /**
    * Cette méthode permet d'indiquer à un site distant lorsque l'appelant
    * a quitté la section critique. On envoie le temps logique de l'appelant et
    * son identifiant. On profite de ce message pour envoyer la valeur de la
    * variable partagée pour la mettre à jour dans l'appelé
    * 
    * @param localTimeStamp   Temps logique de l'appelant
    * @param value            Valeur de la variable partagée
    * @param hostIndex        Identifiant de l'appelant
    * @throws RemoteException En cas d'erreur de communication
    */
   public void free(long localTimeStamp, int value, int hostIndex) throws RemoteException;
   
   /**
    * Nom utilisé pour le nommage du registre RMI
    */
   public static final String RMI_NAME = "Lamport";
}
