package ch.heigvd.interfacesrmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author Miguel-Portable
 */
public interface ILamportAlgorithm extends Remote{
   public long request(long localTimeStamp, int hostIndex) throws RemoteException;
   public void free(long localTimeStamp, int value, int hostIndex) throws RemoteException;
}
