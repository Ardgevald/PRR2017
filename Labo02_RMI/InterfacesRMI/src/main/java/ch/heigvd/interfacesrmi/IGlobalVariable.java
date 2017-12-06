package ch.heigvd.interfacesrmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface pour la communication RMI entre le client et le serveur sur
 * un site. Permet d'obtenir et mettre à jour une variable partagée
 * entre les sites via le serveur implémentant Lamport.
 *
 * @author Miguel Pombo Dias
 * @author Rémi Jacquemard
 */
public interface IGlobalVariable extends Remote{
   /**
    * Permet de récupérer la valeur de la variable partagée.
    * Cette méthode n'est pas bloquante et retourne la valeur qui est
    * actuellement stockée dans le serveur.
    * 
    * @return  La valeur de la variable partagée
    * @throws RemoteException En cas d'erreut de communication distante
    */
   public int getVariable() throws RemoteException;
   
   /**
    * Permet de modifier la valeur de la variable partagée.
    * Cette méthode est bloquante en attendant que le serveur obtienne
    * le droit d'entrer en section critique.
    * 
    * @param value   La valeur à donner dans la variable partagée
    * @throws RemoteException En cas d'erreut de communication distante
    */
   public void setVariable(int value)  throws RemoteException;
   
    /**
    * Nom utilisé pour le nommage du registre RMI
    */
   public static final String RMI_NAME = "GlobalVariable";
}
