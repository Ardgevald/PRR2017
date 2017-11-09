package ch.heigvd.interfacesrmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author Miguel-Portable
 */
public interface ILamportAlgorithm extends Remote{
   public void request(long localTimeStamp) throws RemoteException;
   public void free(int value) throws RemoteException;
}
