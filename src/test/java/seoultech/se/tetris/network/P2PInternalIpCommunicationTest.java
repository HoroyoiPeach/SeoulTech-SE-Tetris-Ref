package seoultech.se.tetris.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * End-to-end internal IP TCP communication test on fixed port 30000.
 *
 * <p>Verifies real socket data transfer, handshake, bidirectional RTT, and
 * exception handling between two independent {@link P2PNetworkManager} instances
 * before proceeding to physical NAT / external IP testing.
 */
class P2PInternalIpCommunicationTest {

    private static final int TEST_PORT = 30000;

    private static final class PeerRecorder implements P2PListener {
        final List<P2PPacket> receivedGameplay = new ArrayList<>();
        final List<Long> pauseSilences = new ArrayList<>();
        final AtomicInteger resumeCount = new AtomicInteger();
        final List<P2PDisconnectReason> disconnectReasons = new ArrayList<>();
        final List<String> validationErrors = new ArrayList<>();
        final List<Float> rttSamples = new ArrayList<>();

        @Override
        public void onPacketReceived(P2PPacket packet) {
            receivedGameplay.add(packet);
        }

        @Override
        public void onPauseRequested(long silenceMillis) {
            pauseSilences.add(silenceMillis);
        }

        @Override
        public void onResumeRequested() {
            resumeCount.incrementAndGet();
        }

        @Override
        public void onDisconnectRequested(P2PDisconnectReason reason) {
            disconnectReasons.add(reason);
        }

        @Override
        public void onValidationError(String rawPayload, String reason) {
            validationErrors.add(reason);
        }

        @Override
        public void onRttUpdated(float rttMillis) {
            rttSamples.add(rttMillis);
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, Runnable poller)
            throws InterruptedException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            poller.run();
            if (!condition.getAsBoolean()) {
                Thread.sleep(5);
            }
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for internal IP P2P condition");
    }

    @Test
    void twoPeersCommunicateOverInternalIpOnPort30000() throws Exception {
        String internalIp = P2PNetworkManager.resolveLocalInternalIp();
        PeerRecorder serverRecorder = new PeerRecorder();
        PeerRecorder clientRecorder = new PeerRecorder();

        try (P2PNetworkManager server = new P2PNetworkManager(P2PConfig.defaults(), serverRecorder);
             P2PNetworkManager client = new P2PNetworkManager(P2PConfig.defaults(), clientRecorder)) {

            // 1. Bind server explicitly to internal IP + port 30000
            int boundPort = server.startServer(internalIp, TEST_PORT);
            assertEquals(TEST_PORT, boundPort);
            assertEquals(P2PConnectionState.LISTENING, server.getConnectionState());

            InetSocketAddress serverAddr = server.getLocalAddress().orElseThrow();
            assertEquals(internalIp, serverAddr.getAddress().getHostAddress());
            assertEquals(TEST_PORT, serverAddr.getPort());

            // 2. Connect client to server's internal IP + port 30000
            client.connect(internalIp, TEST_PORT);
            waitUntil(server::isConnected, server::poll);
            assertTrue(server.isConnected());
            assertTrue(client.isConnected());

            InetSocketAddress remoteSeenByClient = client.getRemoteAddress().orElseThrow();
            assertEquals(internalIp, remoteSeenByClient.getAddress().getHostAddress());
            assertEquals(TEST_PORT, remoteSeenByClient.getPort());

            // 3. Lobby handshake: mode proposal -> agreement -> mutual seed sharing
            assertTrue(server.sendModeProposal("Classic"));
            waitUntil(() -> client.getProposedMode().isPresent(), client::poll);
            assertEquals("Classic", client.getProposedMode().orElseThrow());

            assertTrue(client.sendModeAgreement(true));
            waitUntil(() -> server.getRemoteAgreed().isPresent(), server::poll);
            assertEquals(Boolean.TRUE, server.getRemoteAgreed().orElseThrow());

            long serverSeed = 111_222_333L;
            long clientSeed = 999_888_777L;
            assertTrue(server.sendSeed(serverSeed));
            assertTrue(client.sendSeed(clientSeed));
            waitUntil(() -> client.getRemoteSeed().isPresent() && server.getRemoteSeed().isPresent(), () -> {
                server.poll();
                client.poll();
            });
            assertEquals(serverSeed, client.getRemoteSeed().orElseThrow());
            assertEquals(clientSeed, server.getRemoteSeed().orElseThrow());

            // 4. Bidirectional real gameplay stream (20 packets each direction)
            int[] keySequence = {
                KeyEvent.VK_LEFT,
                KeyEvent.VK_RIGHT,
                KeyEvent.VK_UP,
                KeyEvent.VK_DOWN,
                KeyEvent.VK_SPACE
            };
            int[] stateSequence = {
                P2PPacket.STATE_NONE,
                P2PPacket.STATE_NONE,
                P2PPacket.STATE_CURR_CHANGED,
                P2PPacket.STATE_CURR_CHANGED | P2PPacket.STATE_LEVEL_UP,
                P2PPacket.STATE_GAME_OVER
            };

            int packetCount = 20;
            for (int i = 0; i < packetCount; i++) {
                int sKey = keySequence[i % keySequence.length];
                int sState = stateSequence[i % stateSequence.length];
                int cKey = keySequence[(i + 2) % keySequence.length];
                int cState = stateSequence[(i + 1) % stateSequence.length];

                assertTrue(server.sendGameplayInput(sKey, sState));
                assertTrue(client.sendGameplayInput(cKey, cState));
                server.poll();
                client.poll();
            }

            waitUntil(() -> clientRecorder.receivedGameplay.size() == packetCount
                    && serverRecorder.receivedGameplay.size() == packetCount, () -> {
                server.poll();
                client.poll();
            });

            List<P2PPacket> receivedByClient = client.drainReceivedPackets();
            List<P2PPacket> receivedByServer = server.drainReceivedPackets();
            assertEquals(packetCount, receivedByClient.size());
            assertEquals(packetCount, receivedByServer.size());

            for (int i = 0; i < packetCount; i++) {
                P2PPacket fromServer = receivedByClient.get(i);
                assertEquals(keySequence[i % keySequence.length], fromServer.keyCode());
                assertTrue(fromServer.hasState(stateSequence[i % stateSequence.length]));
                assertTrue(fromServer.timestamp() >= 0.0f);

                P2PPacket fromClient = receivedByServer.get(i);
                assertEquals(keySequence[(i + 2) % keySequence.length], fromClient.keyCode());
                assertTrue(fromClient.hasState(stateSequence[(i + 1) % stateSequence.length]));
                assertTrue(fromClient.timestamp() >= 0.0f);
            }

            // 5. Real-socket bidirectional RTT measurement
            assertTrue(server.sendHeartbeatPing());
            assertTrue(client.sendHeartbeatPing());
            waitUntil(() -> !serverRecorder.rttSamples.isEmpty() && !clientRecorder.rttSamples.isEmpty(), () -> {
                server.poll();
                client.poll();
            });
            assertTrue(server.getRttMillis() >= 0.0f);
            assertTrue(client.getRttMillis() >= 0.0f);
            assertEquals(0, server.getValidationErrorCount());
            assertEquals(0, client.getValidationErrorCount());

            System.out.printf(
                    "[P2P Internal IP OK] %s:%d | packets=%d each way | serverRTT=%.3fms | clientRTT=%.3fms%n",
                    internalIp, TEST_PORT, packetCount, server.getRttMillis(), client.getRttMillis());
        }
    }

    @Test
    void internalIpPort30000HandlesValidationPauseResumeAndTimeoutExceptions() throws Exception {
        String internalIp = P2PNetworkManager.resolveLocalInternalIp();
        // Real-time thresholds scaled for fast JUnit execution (heartbeat disabled during silence window)
        P2PConfig fastRealTimeConfig = new P2PConfig(17L, 5_000L, 180L, 550L, 1);
        PeerRecorder serverRecorder = new PeerRecorder();
        PeerRecorder clientRecorder = new PeerRecorder();

        try (P2PNetworkManager server = new P2PNetworkManager(fastRealTimeConfig, serverRecorder);
             P2PNetworkManager client = new P2PNetworkManager(fastRealTimeConfig, clientRecorder)) {

            assertEquals(TEST_PORT, server.startServer(internalIp, TEST_PORT));
            client.connect(internalIp, TEST_PORT);
            waitUntil(server::isConnected, server::poll);

            // 1. Corrupt payload validation over real TCP socket (connection must survive)
            assertTrue(client.sendRawLineForTesting("INVALID_NON_JSON_PAYLOAD"));
            assertTrue(client.sendRawLineForTesting("{\"KeyCode\":37,\"State\":999999,\"Client/Server_time\":1.0}"));
            assertTrue(client.sendGameplayInput(KeyEvent.VK_DOWN, P2PPacket.STATE_CURR_CHANGED));

            waitUntil(() -> server.getValidationErrorCount() == 2
                    && serverRecorder.receivedGameplay.size() == 1, server::poll);
            assertTrue(server.isConnected());
            assertEquals(KeyEvent.VK_DOWN, server.pollReceivedPacket().keyCode());

            // 2. Real-time silence >= 180ms -> PAUSED, then packet before 550ms -> RESUMED
            Thread.sleep(220);
            server.poll();
            assertEquals(P2PConnectionState.PAUSED, server.getConnectionState());
            assertFalse(serverRecorder.pauseSilences.isEmpty());

            assertTrue(client.sendGameplayInput(KeyEvent.VK_LEFT, P2PPacket.STATE_NONE));
            waitUntil(server::isConnected, server::poll);
            assertEquals(1, serverRecorder.resumeCount.get());
            assertTrue(serverRecorder.disconnectReasons.isEmpty());

            // 3. Real-time silence >= 550ms -> DISCONNECTED (TIMEOUT)
            Thread.sleep(600);
            server.poll();
            assertEquals(P2PConnectionState.DISCONNECTED, server.getConnectionState());
            assertEquals(P2PDisconnectReason.TIMEOUT, server.getLastDisconnectReason());
            assertEquals(List.of(P2PDisconnectReason.TIMEOUT), serverRecorder.disconnectReasons);
        }
    }
}
