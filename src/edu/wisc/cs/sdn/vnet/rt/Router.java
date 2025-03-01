package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		// Check if IPv4 packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			System.out.println("Not an IPv4 packet. Dropping packet.");
			return;
		}
		System.out.println("Received IPv4 packet.");

		// Cast the IP packet to an IPv4 packet
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();

		// Subroutine to verify checksum ------- THIS IS WHERE I LEFT OFF
		if (!verifyChecksum(ipPacket)) {
			System.out.println("Invalid checksum. Dropping packet.");
			return;
		}
		System.out.println("Checksum is valid.");

		// Decrement TTL by 1
		ipPacket.setTtl((byte)(ipPacket.getTtl() - 1));

		// Check if TTL is 0
		if (ipPacket.getTtl() == 0) {
			System.out.println("TTL is 0. Dropping packet.");
			return;
		} 
		System.out.println("TTL is not 0.");

		// Check if destination IP is one of the router's interfaces
		for (Iface iface : this.interfaces.values()) {
			if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
				System.out.println("Destination IP is one of the router's interfaces. Dropping packet.");
				return;
			}
		}
		System.out.println("Destination IP is not one of the router's interfaces.");

		// Check if destination IP is in routing table
		RouteEntry bestMatch = this.routeTable.lookup(ipPacket.getDestinationAddress());
		if (bestMatch == null) {
			System.out.println("No matching route in routing table. Dropping packet.");
			return;
		}
		System.out.println("Found matching route in routing table.");

		// Check ARP cache for MAC address
		ArpEntry arpEntry = this.arpCache.lookup(ipPacket.getDestinationAddress());
		if (arpEntry == null) {
			System.out.println("No matching ARP entry in ARP cache. Sending ARP request.");
		} else {
			System.out.println("Found matching ARP entry in ARP cache. Forwarding packet.");
		}

		/*************************** Update the header of the ipPacket  ******************************************/
		// Update the mac address of the source interface in ipPacket
		ipPacket.setSourceAddress(bestMatch.getInterface().getIpAddress());
		// Update the mac address of the destination interface in ipPacket
		ipPacket.setDestinationAddress(bestMatch.getGatewayAddress());
		// Reset the checksum
		ipPacket.resetChecksum();
		// Serialize the ipPacket
		byte[] serializedPacket = ipPacket.serialize();

		/***************************** Call sendPacket() with the appropriate arguments **********************/
		System.out.println("Sending packet to next hop.");
		sendPacket(serializedPacket, bestMatch.getInterface());
		
		return;
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param IPv4 the Ethernet packet that was received
	 * @return Boolean true if checksum is valid, false otherwise
	 */
	public boolean verifyChecksum(IPv4 I4packet)
	{

		// Determine header length
		byte headerLength =	I4packet.getHeaderLength();

		// Serialize the IP header with IPv4.serialize()
		byte[] serializedPacket = I4packet.serialize();

		// Convert the serialized packet to an IPv4 object
		IPv4 serializedPacket = new IPv4();
		serializedPacket.deserialize(serializedPacket, 0, serializedPacket.length);

		// Return true if the checksums match, false otherwise
		if (serializedPacket.getChecksum() == I4packet.getChecksum()) {
			return true; 
		} else {
			return false;
		}
		
	}
}
