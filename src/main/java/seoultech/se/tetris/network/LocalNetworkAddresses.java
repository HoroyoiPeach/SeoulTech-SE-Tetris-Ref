package seoultech.se.tetris.network;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

/** Discovers local addresses that another computer can use to join a hosted game. */
public final class LocalNetworkAddresses {
    private static final String NO_ADDRESS_MESSAGE =
            "No usable LAN address found.\nConnect this computer to the same LAN or Wi-Fi as the other computer.";

    private LocalNetworkAddresses() {}

    /** A usable address and the network interface that owns it. */
    public record HostAddress(String interfaceName, InetAddress address) {
        public HostAddress {
            interfaceName = Objects.requireNonNull(interfaceName, "interfaceName");
            address = Objects.requireNonNull(address, "address");
        }

        /** Returns a value that can be pasted directly into the JOIN address field. */
        public String joinAddress() { return address.getHostAddress(); }
    }

    /** Returns every currently usable non-loopback host address in client-friendly order. */
    public static List<HostAddress> discover() throws SocketException {
        List<InterfaceSnapshot> snapshots = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            try {
                snapshots.add(new InterfaceSnapshot(
                        displayName(networkInterface),
                        networkInterface.isUp(),
                        networkInterface.isLoopback(),
                        networkInterface.isVirtual(),
                        java.util.Collections.list(networkInterface.getInetAddresses())));
            } catch (SocketException ignored) {
                // An interface can disappear while the operating system's list is being inspected.
            }
        }
        return select(snapshots);
    }

    /** Builds the complete text shown to a client, without ever advertising loopback. */
    public static String connectionText(List<HostAddress> addresses, int port) {
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("Invalid TCP port: " + port);
        if (addresses.isEmpty()) return NO_ADDRESS_MESSAGE;
        StringBuilder text = new StringBuilder("Enter one server address on the other computer:\n");
        for (HostAddress candidate : addresses) {
            text.append(candidate.interfaceName()).append("  ·  ").append(candidate.joinAddress()).append('\n');
        }
        return text.append("Port: ").append(port).append(" (configured automatically)").toString();
    }

    /** Discovers addresses and builds the complete host-screen text. */
    public static String discoverConnectionText(int port) throws SocketException {
        return connectionText(discover(), port);
    }

    private static String displayName(NetworkInterface networkInterface) {
        String displayName = networkInterface.getDisplayName();
        return displayName == null || displayName.isBlank() ? networkInterface.getName() : displayName;
    }

    static List<HostAddress> select(List<InterfaceSnapshot> interfaces) {
        List<RankedHostAddress> selected = new ArrayList<>();
        for (InterfaceSnapshot networkInterface : interfaces) {
            if (!networkInterface.up() || networkInterface.loopback()) continue;
            for (InetAddress address : networkInterface.addresses()) {
                if (priority(address) >= 0) {
                    selected.add(new RankedHostAddress(
                            new HostAddress(networkInterface.name(), address), networkInterface.virtual()));
                }
            }
        }
        selected.sort(Comparator
                .comparingInt((RankedHostAddress candidate) -> priority(candidate.hostAddress().address()))
                .thenComparing(RankedHostAddress::virtual)
                .thenComparing(candidate -> candidate.hostAddress().interfaceName())
                .thenComparing(candidate -> candidate.hostAddress().joinAddress()));
        return selected.stream().map(RankedHostAddress::hostAddress).toList();
    }

    private static int priority(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isMulticastAddress()) return -1;
        if (address instanceof Inet4Address) {
            if (address.isSiteLocalAddress()) return 0;
            if (address.isLinkLocalAddress()) return 2;
            return 1;
        }
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            if ((first & 0xfe) == 0xfc) return 3; // fc00::/7 unique-local address
            if ((first & 0xe0) == 0x20) return 4; // 2000::/3 global-unicast address
        }
        return -1;
    }

    record InterfaceSnapshot(
            String name, boolean up, boolean loopback, boolean virtual, List<InetAddress> addresses) {
        InterfaceSnapshot {
            name = Objects.requireNonNull(name, "name");
            addresses = List.copyOf(addresses);
        }
    }

    private record RankedHostAddress(HostAddress hostAddress, boolean virtual) {}
}
