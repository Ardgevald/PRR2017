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

/**
 * Représente un maître PTP
 *
 * @author Remi
 */
public class MasterPTP {

	private static final int SYNC_PORT = 1234;
	private static final int DELAY_PORT = 1235;
	private static final String GROUP_ADDRESS = "234.56.78.9";
	private static final long SYNC_PERIOD = 4000;

	private final Timer syncTimer = new Timer();

	private TimerTask syncTask = new TimerTask() {

		private byte id = 0;
		private MulticastSocket broadcastSocket = new MulticastSocket(SYNC_PORT);
		private InetAddress group = InetAddress.getByName(GROUP_ADDRESS);

		@Override
		public void run() {
			try {
				// SYNC
				System.out.println("Sending sync");
				byte[] syncData = {0, id};

				DatagramPacket packet = new DatagramPacket(syncData, syncData.length, group, SYNC_PORT);
				broadcastSocket.send(packet);

				System.out.println("Sync sent");

				// ----------------
				// FOLLOW_UP  {1, time, id}
				System.out.println("Sending follow_up");
				long time = System.currentTimeMillis();
				byte[] followUpData = ByteBuffer.allocate(2 + Long.BYTES).put(id).putLong(time).array();
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
					long time = System.currentTimeMillis();

					InetAddress address = packet.getAddress();
					byte type = packet.getData()[0];
					byte id = packet.getData()[1];

					if (type == 3) {
						System.out.println("Delay request received");
						byte[] delayResponseBuffer = ByteBuffer.allocate(2 + Long.BYTES).put((byte) 4).putLong(time).put(id).array();
						packet = new DatagramPacket(delayResponseBuffer, delayResponseBuffer.length, address, SYNC_PORT);
						socket.send(packet);
						System.out.println("Delay response sent to " + type);
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
