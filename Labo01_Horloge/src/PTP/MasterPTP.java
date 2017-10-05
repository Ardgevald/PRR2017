package PTP;

import java.io.Console;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Représente un maître PTP
 *
 * @author Remi
 */
public class MasterPTP {

    private static final int BROADCAST_PORT = 1234;
    private static final String GROUP_ADDRESS = "228.5.6.7";
    private static final long SYNC_PERIOD = 4000;

    private TimerTask syncTask = new TimerTask() {
	MulticastSocket broadcastSocket = new MulticastSocket(BROADCAST_PORT);
	InetAddress group = InetAddress.getByName(GROUP_ADDRESS);

	private byte id = 0;
	
	@Override
	public void run() {
	    try {
		// SYNC
		System.out.println("Sending sync");
		byte[] syncData = {0,  id};
		
		DatagramPacket packet = new DatagramPacket(syncData, syncData.length, group, BROADCAST_PORT);
		broadcastSocket.send(packet);
		
		System.out.println("Sync sent");
		
		
		
		// ----------------
		// FOLLOW_UP  {1, time, id}
		
		System.out.println("Sending follow_up");
		long time = System.currentTimeMillis();
		byte[] followUpData = ByteBuffer.allocate(2 + Long.BYTES).put(id).putLong(time).put(id).array();		
		packet = new DatagramPacket(followUpData, followUpData.length, group, BROADCAST_PORT);
		broadcastSocket.send(packet);
		System.out.println("Follow_up sent ("+id+")");
		
		//Increasing id
		id++;
		
	    } catch (IOException ex) {
		Logger.getLogger(MasterPTP.class.getName()).log(Level.SEVERE, null, ex);
	    }
	}
    };
    private final Timer syncTimer = new Timer();

    public MasterPTP() throws SocketException, IOException {
	//Diffusion des messages sync et followup
	syncTimer.scheduleAtFixedRate(syncTask, 0, SYNC_PERIOD);
	
	//Attente des delay_request
    }

}
