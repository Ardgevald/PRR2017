package PTP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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

import static util.Protocol.*;
import static util.Protocol.MessageStruct.*;
import static util.Protocol.MessageType.*;

/**
 * Représente un maître PTP
 *
 * @author Remi
 */
public class SlavePTP {

	private static final int TIMEOUT = 4000;

	private static final int BUFFER_SIZE = 16;

	private long gap;
	private long delay;

	private boolean stop;

	private byte delayId = 0;

	private InetAddress masterAddress;
	private MulticastSocket socket;
	private DatagramSocket unicastSocket;

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

			try {
				System.out.println("sending unicast DELAY_REQUEST");
				unicastSocket = new DatagramSocket();

				byte[] buffer = {DELAY_REQUEST.asByte(), ++delayId};
				DatagramPacket paquet = new DatagramPacket(buffer, buffer.length, masterAddress, DELAY_PORT);

				long slaveTime = System.currentTimeMillis();
				System.out.println("sent at time : " + slaveTime);
				unicastSocket.send(paquet);

				buffer = new byte[BUFFER_SIZE];

				System.out.println("receiving unicast DELAY_RESPONSE");

				paquet = new DatagramPacket(buffer, buffer.length);
				unicastSocket.receive(paquet);

				if (paquet.getData()[ID.ordinal()] == DELAY_REQUEST.asByte()
						&& paquet.getData()[TYPE.ordinal()] == delayId) {

					long masterTime = ByteLongConverter.bytesToLong(Arrays.copyOfRange(paquet.getData(), 2 * Byte.BYTES, paquet.getLength()));
					System.out.println("delay : " + masterTime + " - " + slaveTime);
					delay = (masterTime - slaveTime) / 2;
				}

				System.out.println("response received");

			} catch (SocketException ex) {
				Logger.getLogger(SlavePTP.class.getName()).log(Level.SEVERE, null, ex);
			} catch (IOException ex) {
				Logger.getLogger(SlavePTP.class.getName()).log(Level.SEVERE, null, ex);
			}

			System.out.println(String.format("DELAY_REQUEST_RESPONSE : gap: %d  delay: %d", gap, delay));
			delayTimer.schedule(new TaskSchedule(), (4 + new Random().nextInt(57)) * SYNC_PERIOD);
		}
	};

	private final Thread sync = new Thread(() -> {
		try {
			waitSync();

			// start delay management
			delayTimer.schedule(new TaskSchedule(), 0);

			// continuer waitSync en continu
			while (!stop) {
				waitSync();
			}

		} catch (IOException ex) {
			Logger.getLogger(SlavePTP.class.getName()).log(Level.SEVERE, null, ex);
		}
	});

	private void waitSync() throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		Byte id = null;

		socket = new MulticastSocket(SYNC_PORT);
		InetAddress group = InetAddress.getByName(GROUP_ADDRESS);
		socket.joinGroup(group);

		DatagramPacket paquet = new DatagramPacket(buffer, BUFFER_SIZE);

		do {
			// wait for Master
			socket.receive(paquet);

			if (paquet.getLength() == 2 && paquet.getData()[TYPE.ordinal()] == SYNC.asByte()) {
				id = paquet.getData()[ID.ordinal()];
				masterAddress = paquet.getAddress();
			}
		} while (masterAddress == null);

		// Wait for FollowUp
		socket.receive(paquet);

		if (paquet.getLength() == Long.BYTES + 2 * Byte.BYTES
				&& paquet.getData()[TYPE.ordinal()] == FOLLOW_UP.asByte()
				&& paquet.getData()[ID.ordinal()] == id) {
			
			byte[] masterTime = Arrays.copyOfRange(paquet.getData(), 2 * Byte.BYTES, paquet.getLength());
			gap = ByteLongConverter.bytesToLong(masterTime) - System.currentTimeMillis();
		}

		System.out.println(String.format("SYNC : gap: %d  delay: %d", gap, delay));
	}

	private void startThread() {
		sync.start();
	}

	public long getTimeSynced() {
		return System.currentTimeMillis() + delay + gap;
	}

	public void close() {
		stop = true;
	}
}
