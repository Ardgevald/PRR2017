/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
