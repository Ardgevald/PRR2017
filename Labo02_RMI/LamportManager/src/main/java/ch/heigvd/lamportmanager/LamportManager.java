package ch.heigvd.lamportmanager;

import ch.heigvd.interfacesrmi.IGlobalVariable;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 *
 */
public class LamportManager {

   private final static String VARIABLE_SERVER_NAME = "GlobalVariable";
   
   private int globalVariable;
   private final int nbSites;

   public static void main(String... args) {
      System.out.println("Test");
   }

   public LamportManager(int nbSites) {
      this.nbSites = nbSites;

      try {
         GlobalVariable serverVariable = new GlobalVariable();
         Naming.rebind(VARIABLE_SERVER_NAME, serverVariable);
         System.out.println("Serveur " + VARIABLE_SERVER_NAME + " pret");
      } catch (Exception e) {
         System.out.println("Exception a l'enregistrement: " + e);
      }
   }

   private class GlobalVariable extends UnicastRemoteObject implements IGlobalVariable {

      @Override
      public int getVariable() throws RemoteException {
         return globalVariable;
      }

      @Override
      public void setVariable(int value) throws RemoteException {
         throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
      }

   }

}
