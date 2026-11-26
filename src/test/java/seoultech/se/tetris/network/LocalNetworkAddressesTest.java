package seoultech.se.tetris.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalNetworkAddressesTest {
    @Test
    void selectsEveryUsableAddressAndOrdersIpv4ForEasyLanConnection() {
        List<LocalNetworkAddresses.HostAddress> addresses = LocalNetworkAddresses.select(List.of(
                fixture("offline", false, false, false, "192.168.50.90"),
                fixture("loopback", true, true, false, "127.0.0.1", "::1"),
                fixture("vpn", true, false, true, "10.10.10.10"),
                fixture("ethernet", true, false, false,
                        "169.254.20.5", "8.8.8.8", "fe80::1234", "2001:4860:4860::8888"),
                fixture("wifi", true, false, false, "192.168.0.20", "fd12:3456:789a::20")));

        assertEquals(List.of(
                "wifi=192.168.0.20",
                "vpn=10.10.10.10",
                "ethernet=8.8.8.8",
                "ethernet=169.254.20.5",
                "wifi=fd12:3456:789a:0:0:0:0:20",
                "ethernet=2001:4860:4860:0:0:0:0:8888"),
                addresses.stream()
                        .map(address -> address.interfaceName() + "=" + address.joinAddress())
                        .toList());
    }

    @Test
    void returnsNetworkGuidanceInsteadOfLoopbackWhenNoRealAddressExists() {
        List<LocalNetworkAddresses.HostAddress> addresses = LocalNetworkAddresses.select(List.of(
                fixture("loopback", true, true, false, "127.0.0.1", "::1"),
                fixture("wifi", false, false, false, "192.168.0.20"),
                fixture("ethernet", true, false, false, "fe80::1234")));

        assertEquals(List.of(), addresses);
        String text = LocalNetworkAddresses.connectionText(addresses, 30000);
        assertTrue(text.contains("same LAN or Wi-Fi"));
        assertFalse(text.contains("127.0.0.1"));
    }

    @Test
    void connectionTextKeepsPasteableAddressesSeparateFromThePortLegend() throws Exception {
        String text = LocalNetworkAddresses.connectionText(List.of(
                new LocalNetworkAddresses.HostAddress("Wi-Fi", InetAddress.getByName("192.168.1.40")),
                new LocalNetworkAddresses.HostAddress("Ethernet", InetAddress.getByName("fd00::40"))), 30000);

        assertEquals("Enter one server address on the other computer:\n"
                + "Wi-Fi  ·  192.168.1.40\n"
                + "Ethernet  ·  fd00:0:0:0:0:0:0:40\n"
                + "Port: 30000 (configured automatically)", text);
    }

    private static LocalNetworkAddresses.InterfaceSnapshot fixture(
            String name, boolean up, boolean loopback, boolean virtual, String... addresses) {
        return new LocalNetworkAddresses.InterfaceSnapshot(name, up, loopback, virtual,
                java.util.Arrays.stream(addresses).map(LocalNetworkAddressesTest::address).toList());
    }

    private static InetAddress address(String value) {
        try { return InetAddress.getByName(value); }
        catch (java.net.UnknownHostException e) { throw new AssertionError(e); }
    }
}
