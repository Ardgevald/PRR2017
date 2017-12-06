package ch.heigvd.interfacesrmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author Miguel-Portable
 */
public interface IGlobalVariable extends Remote{
   public int getVariable() throws RemoteException;
   public void setVariable(int value)  throws RemoteException;
   
   public static final String RMI_NAME = "GlobalVariable";
}
