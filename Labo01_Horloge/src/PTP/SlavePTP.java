package PTP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import util.ByteLongConverter;

/**
 * Représente un maître PTP
 *
 * @author Remi
 */
public class SlavePTP {

	private static final int BROADCAST_PORT = 1234;
	private static final String GROUP_ADDRESS = "234.56.78.9";
	private static final int TIMEOUT = 4000;

	private static final int BUFFER_SIZE = 16;

	private long gap;
	private long delay;
	
	private boolean stop;

	private InetAddress masterAddress;
	private MulticastSocket socket;

	public SlavePTP() throws SocketException, IOException {
		this.gap = 0;
		this.delay = 0;
		//Diffusion des messages sync et followup
		startThread();
		//syncTimer.scheduleAtFixedRate(syncTask, 0, SYNC_PERIOD);
	}

	private final Timer delayTimer = new Timer();

	private class TaskSchedule extends TimerTask {

		@Override
		public void run() {
			System.out.println(String.format("gap: %d  delay: %d", gap, delay));
			
			delay = 2;
			// do things
			delayTimer.schedule(new TaskSchedule(), (4 + new Random().nextInt(57)) * 1000);
		}
	};

	private final Thread sync = new Thread(() -> {
		try {

			waitSync();

			// start delay management
			delayTimer.schedule(new TaskSchedule(), 0);

			// continuer waitSync en continu
			while (!stop){
				waitSync();
			}
			
		} catch (IOException ex) {
			Logger.getLogger(SlavePTP.class.getName()).log(Level.SEVERE, null, ex);
		}
	});

	private void waitSync() throws IOException {
		System.out.println(String.format("gap: %d  delay: %d", gap, delay));

		byte[] buffer = new byte[BUFFER_SIZE];
		Byte id = null;

		socket = new MulticastSocket(BROADCAST_PORT);
		InetAddress group = InetAddress.getByName(GROUP_ADDRESS);
		socket.joinGroup(group);

		DatagramPacket paquet = new DatagramPacket(buffer, BUFFER_SIZE);

		do {
			// wait for Master
			socket.receive(paquet);

			if (paquet.getLength() == 2 && paquet.getData()[0] == 0) {
				id = paquet.getData()[1];
				masterAddress = paquet.getAddress();
			}
		} while (masterAddress == null);

		// Wait for FollowUp
		socket.receive(paquet);

		if (paquet.getLength() == Long.BYTES + 2 * Byte.BYTES && paquet.getData()[0] == 1 && paquet.getData()[1] == id) {
			byte[] gapByte = Arrays.copyOfRange(paquet.getData(), 2 * Byte.BYTES, paquet.getLength());
			gap = ByteLongConverter.bytesToLong(gapByte) - System.currentTimeMillis();
		}
	}

	private void startThread() {
		sync.start();
	}

	public long getTimeSynced() {
		return System.currentTimeMillis() + delay + gap;
	}
	
	public void close(){
		stop = true;
	}
}
