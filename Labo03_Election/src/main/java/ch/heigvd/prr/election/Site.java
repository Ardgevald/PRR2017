package ch.heigvd.prr.election;

import java.net.InetSocketAddress;

public class Site {

	private InetSocketAddress socketAddress;
	private int apptitude;

	public Site(String ip, int port, int apptitude) {
		this.socketAddress = new InetSocketAddress(ip, port);
		this.apptitude = apptitude;
	}

	public InetSocketAddress getSocketAddress() {
		return socketAddress;
	}

	public int getApptitude() {
		return apptitude;
	}

	public void setSocketAddress(InetSocketAddress socketAddress) {
		this.socketAddress = socketAddress;
	}

	public void setApptitude(int apptitude) {
		this.apptitude = apptitude;
	}

	
}
