package ch.heigvd.prr.election;

import java.net.InetSocketAddress;

public class Site implements Comparable<Site> {

	private InetSocketAddress socketAddress;
	private int apptitude;

	public Site(String ip, int port, int apptitude) {
		this.socketAddress = new InetSocketAddress(ip, port);
		this.apptitude = apptitude;
	}

	public Site(String ip, int port) {
		this(ip, port, 0); // De base, une aptitude de 0 --> "non connu"
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

	@Override
	public int compareTo(Site t) {
		int currentComparaison = t.apptitude - this.apptitude;
		if (currentComparaison != 0) {
			return currentComparaison;
		} else {
			// Egalité au niveau des apptitudes, on départage par rapport a l'ip
			byte[] curSiteAddress = this.socketAddress.getAddress().getAddress();
			byte[] otherSiteAddress = t.socketAddress.getAddress().getAddress();

			for (int i = 0; i < curSiteAddress.length; i++) {
				currentComparaison = otherSiteAddress[i] - curSiteAddress[i];
				if (currentComparaison != 0) {
					return currentComparaison;
				}
			}

		}

		return 0;
	}

}
