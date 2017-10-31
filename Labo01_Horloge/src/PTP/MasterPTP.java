package PTP;

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

import static util.Protocol.*;
import static util.Protocol.MessageStruct.*;
import static util.Protocol.MessageType.*;

/**
 * Représente un maître PTP
 *
 * @author Remi
 */
public class MasterPTP {

	private final Timer syncTimer = new Timer();

	private TimerTask syncTask = new TimerTask() {

		private byte id = 0;
		private MulticastSocket broadcastSocket = new MulticastSocket();
		private InetAddress group = InetAddress.getByName(GROUP_ADDRESS);

		@Override
		public void run() {
			try {
				// SYNC
				System.out.println("Sending sync");
				byte[] syncData = {SYNC.asByte(), id};

				DatagramPacket packet = new DatagramPacket(syncData, syncData.length, group, SYNC_PORT);
				
				//*
				long time = System.currentTimeMillis();
				/*/
				long time = System.currentTimeMillis() + 10000;
				//*/
				broadcastSocket.send(packet);

				System.out.println("Sync sent");

				// ----------------
				// FOLLOW_UP  {1, time, id}
				System.out.println("Sending follow_up");
				
				byte[] followUpData = ByteBuffer.allocate(2 + Long.BYTES).put(FOLLOW_UP.asByte()).put(id).putLong(time).array();
				packet = new DatagramPacket(followUpData, followUpData.length, group, SYNC_PORT);
				broadcastSocket.send(packet);
				System.out.println("Follow_up sent (" + id + ")");

				//Increasing id
				id++;

			} catch (IOException ex) {
				Logger.getLogger(MasterPTP.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	};

	//Delay request
	private final Thread delayRequestThread = new Thread(new Runnable() {

		DatagramSocket socket = new DatagramSocket(DELAY_PORT);

		@Override
		public void run() {
			boolean toContinue = true;
			try {
				do {
					byte[] delayRequestBuffer = new byte[2];
					DatagramPacket packet = new DatagramPacket(delayRequestBuffer, 2);
					System.out.println("Waiting for delay request...");
					socket.receive(packet);

					/*
					try {
						Thread.sleep(2000);
					} catch (InterruptedException ex) {
						Logger.getLogger(MasterPTP.class.getName()).log(Level.SEVERE, null, ex);
					}
					/*/
					
					long time = System.currentTimeMillis();

					InetAddress address = packet.getAddress();
					int port = packet.getPort();
					byte type = packet.getData()[TYPE.ordinal()];
					byte id = packet.getData()[ID.ordinal()];

					if (type == DELAY_REQUEST.asByte()) {
						System.out.println("Delay request received");
						byte[] delayResponseBuffer = ByteBuffer.allocate(2 + Long.BYTES).put(DELAY_RESPONSE.asByte()).put(id).putLong(time).array();
						packet = new DatagramPacket(delayResponseBuffer, delayResponseBuffer.length, address, port);
						socket.send(packet);
						System.out.println("Delay response sent");
					}

				} while (toContinue);

			} catch (IOException ex) {
				Logger.getLogger(MasterPTP.class.getName()).log(Level.SEVERE, null, ex);
			}

		}
	});

	public MasterPTP() throws IOException {
		//Diffusion des messages sync et followup
		syncTimer.scheduleAtFixedRate(syncTask, 0, SYNC_PERIOD);

		//Attente des delay_request
		delayRequestThread.start();
	}

	public static void main(String ... args ) throws IOException{
		MasterPTP masterPTP = new MasterPTP();
		
	}
	
}
