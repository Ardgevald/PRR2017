package ch.heigvd.prr.termination;

import ch.heigvd.prr.termination.Message;
import ch.heigvd.prr.termination.UDPHandler;
import ch.heigvd.prr.termination.UDPHandler.UDPListener;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Remi
 */
public class UDPMessageHandler extends UDPHandler implements UDPListener {

	public UDPMessageHandler(InetSocketAddress address) throws SocketException {
		super(address, Message.getMaxMessageSize());
		this.addListener(this);
	}
	
	
	// ------ LISTENER

	private List<UDPMessageListener> listeners = new ArrayList<>();

	@Override
	public void dataReceived(byte[] data, int length) {
		listeners.forEach(l -> l.dataReceived(Message.parse(data, length)));
	}

	public interface UDPMessageListener {
		public void dataReceived(Message message);
	}

	public void addListener(UDPMessageListener listener) {
		this.listeners.add(listener);
	}

	// ----- SENDING
	public void sendTo(Message message, InetSocketAddress address) throws IOException {
		this.sendTo(message.toByteArray(), address);
	}

	public void sendTo(Message message, Site site) throws IOException {
		this.sendTo(message, site.getSocketAddress());
	}

}
