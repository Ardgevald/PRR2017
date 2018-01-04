/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.heigvd.prr.election;

import java.io.IOException;

/**
 *
 * @author Remi
 */
public class App {
	public static void main(String ... args) throws IOException{
		// UTiliser les tryWithRessource ici
		ElectionManager electionManager1 = new ElectionManager((byte)0);
		ElectionManager electionManager2 = new ElectionManager((byte)1);
		System.out.println("Election manager running");
		
		electionManager1.startElection();
		
	}
}
