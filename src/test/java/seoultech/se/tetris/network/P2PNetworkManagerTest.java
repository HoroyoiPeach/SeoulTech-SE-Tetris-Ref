package seoultech.se.tetris.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class P2PNetworkManagerTest {

    private static final class ManualClock {
        private long nanos;

        ManualClock(long initialMillis) {
            this.nanos = initialMillis * 1_000_000L;
        }

        long nowNanos() {
            return nanos;
        }

        void advanceMillis(long deltaMillis) {
            nanos += deltaMillis * 1_000_000L;
        }
    }

    private static final class RecordingListener implements P2PListener {
        final AtomicInteger connectedCount = new AtomicInteger();
        final List<P2PPacket> gameplayPackets = new ArrayList<>();
        final List<String> matchFrames = new ArrayList<>();
        final List<Long> seeds = new ArrayList<>();
        final List<String> modes = new ArrayList<>();
        final List<Boolean> agreements = new ArrayList<>();
        final List<Long> pauseSilences = new ArrayList<>();
        final AtomicInteger resumeCount = new AtomicInteger();
        final List<P2PDisconnectReason> disconnectReasons = new ArrayList<>();
        final List<String> validationErrors = new ArrayList<>();
        final List<Float> rttUpdates = new ArrayList<>();

        @Override
        public void onConnected() {
            connectedCount.incrementAndGet();
        }

        @Override
        public void onPacketReceived(P2PPacket packet) {
            gameplayPackets.add(packet);
        }

        @Override
        public void onMatchFrame(String payload) {
            if (payload.equals("BAD")) throw new IllegalArgumentException("Unknown match message");
            matchFrames.add(payload);
        }

        @Override
        public void onSeedReceived(long seed) {
            seeds.add(seed);
        }

        @Override
        public void onModeProposed(String mode) {
            modes.add(mode);
        }

        @Override
        public void onModeAgreed(boolean agreed) {
            agreements.add(agreed);
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
            rttUpdates.add(rttMillis);
        }
    }

    private static void connectLoopbackPair(P2PNetworkManager server, P2PNetworkManager client)
            throws IOException, InterruptedException {
        int port = server.startServer(0);
        assertEquals(P2PConnectionState.LISTENING, server.getConnectionState());

        client.connect("127.0.0.1", port);

        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!server.isConnected() && System.nanoTime() < deadline) {
            server.poll();
            if (!server.isConnected()) {
                Thread.sleep(2);
            }
        }
        assertTrue(server.isConnected(), "Server should accept client connection");
        assertTrue(client.isConnected(), "Client should be connected");
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, Runnable poller)
            throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            poller.run();
            if (!condition.getAsBoolean()) {
                Thread.sleep(2);
            }
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for P2P condition");
    }

    @Test
    void defaultConfigMatchesSpecificationThresholds() {
        P2PConfig defaults = P2PConfig.defaults();
        assertEquals(17L, defaults.pollIntervalMs());
        assertEquals(500L, defaults.heartbeatIntervalMs());
        assertEquals(999L, defaults.pauseThresholdMs());
        assertEquals(10_000L, defaults.disconnectThresholdMs());
        assertEquals(1, defaults.socketTimeoutMs());

        assertThrows(IllegalArgumentException.class, () -> new P2PConfig(0, 500, 999, 10_000, 1));
        assertThrows(IllegalArgumentException.class, () -> new P2PConfig(17, 0, 999, 10_000, 1));
        assertThrows(IllegalArgumentException.class, () -> new P2PConfig(17, 500, 0, 10_000, 1));
        assertThrows(IllegalArgumentException.class, () -> new P2PConfig(17, 500, 1000, 1000, 1));
        assertThrows(IllegalArgumentException.class, () -> new P2PConfig(17, 500, 999, 10_000, -1));
    }

    @Test
    void packetSerializationPreservesFieldsAndBitFlags() {
        int combinedState = P2PPacket.STATE_CURR_CHANGED | P2PPacket.STATE_LEVEL_UP;
        P2PPacket packet = P2PPacket.gameplay(KeyEvent.VK_SPACE, combinedState, 1234.5f);

        String wire = packet.serialize();
        assertTrue(wire.contains("\"KeyCode\":" + KeyEvent.VK_SPACE));
        assertTrue(wire.contains("\"State\":" + combinedState));
        assertTrue(wire.contains("\"Client/Server_time\":1234.500"));

        P2PPacket decoded = P2PPacket.deserialize(wire);
        assertEquals(KeyEvent.VK_SPACE, decoded.keyCode());
        assertEquals(combinedState, decoded.state());
        assertEquals(1234.5f, decoded.timestamp(), 0.01f);
        assertTrue(decoded.hasState(P2PPacket.STATE_CURR_CHANGED));
        assertTrue(decoded.hasState(P2PPacket.STATE_LEVEL_UP));
        assertFalse(decoded.hasState(P2PPacket.STATE_GAME_OVER));
        assertTrue(decoded.isGameplayPacket());
        assertFalse(decoded.isHeartbeatOnly());
    }

    @Test
    void packetValidationRejectsMalformedAndTamperedPayloads() {
        assertThrows(IllegalArgumentException.class, () -> P2PPacket.deserialize(null));
        assertThrows(IllegalArgumentException.class, () -> P2PPacket.deserialize("   "));
        assertThrows(IllegalArgumentException.class, () -> P2PPacket.deserialize("not-json"));
        assertThrows(IllegalArgumentException.class, () -> P2PPacket.deserialize("{}"));
        assertThrows(IllegalArgumentException.class,
                () -> P2PPacket.deserialize("{\"KeyCode\":37,\"State\":0}"));
        assertThrows(IllegalArgumentException.class,
                () -> P2PPacket.deserialize("{\"KeyCode\":\"37\",\"State\":0,\"Client/Server_time\":1.0}"));
        assertThrows(IllegalArgumentException.class,
                () -> P2PPacket.deserialize("{\"KeyCode\":37,\"State\":999999,\"Client/Server_time\":1.0}"));
        assertThrows(IllegalArgumentException.class,
                () -> P2PPacket.deserialize("{\"KeyCode\":37,\"State\":0,\"Client/Server_time\":-5.0}"));
        assertThrows(IllegalArgumentException.class,
                () -> P2PPacket.deserialize("{\"KeyCode\":37,\"State\":0,\"Client/Server_time\":NaN}"));
        assertThrows(IllegalArgumentException.class,
                () -> P2PPacket.deserialize("{\"KeyCode\":37,\"State\":0,\"Client/Server_time\":1.0,\"Extra\":1}"));
        assertThrows(IllegalArgumentException.class,
                () -> P2PPacket.deserialize("{\"KeyCode\":37,\"KeyCode\":38,\"State\":0,\"Client/Server_time\":1.0}"));
        assertThrows(IllegalArgumentException.class,
                () -> P2PPacket.deserialize("{\"KeyCode\":0,\"State\":" + P2PPacket.STATE_SEED_SYNC
                        + ",\"Client/Server_time\":1.0}"));
        assertThrows(IllegalArgumentException.class,
                () -> P2PPacket.deserialize("{\"KeyCode\":0,\"State\":0,\"Client/Server_time\":1.0,"
                        + "\"Mode\":\"bad\\\"mode\"}"));

        String oversized = "{" + " ".repeat(P2PPacket.MAX_PACKET_LENGTH + 10) + "}";
        assertThrows(IllegalArgumentException.class, () -> P2PPacket.deserialize(oversized));
    }

    @Test
    void normalFlowSupportsHandshakeSeedSharingAndBidirectionalGameplay() throws Exception {
        ManualClock serverClock = new ManualClock(1_000);
        ManualClock clientClock = new ManualClock(5_000);
        RecordingListener serverListener = new RecordingListener();
        RecordingListener clientListener = new RecordingListener();

        try (P2PNetworkManager server = new P2PNetworkManager(P2PConfig.defaults(), serverListener, serverClock::nowNanos);
             P2PNetworkManager client = new P2PNetworkManager(P2PConfig.defaults(), clientListener, clientClock::nowNanos)) {

            connectLoopbackPair(server, client);
            assertEquals(1, serverListener.connectedCount.get());
            assertEquals(1, clientListener.connectedCount.get());

            // Lobby flow: server proposes mode -> client agrees -> both exchange seeds
            assertTrue(server.sendModeProposal("Classic"));
            waitUntil(() -> client.getProposedMode().isPresent(), client::poll);
            assertEquals("Classic", client.getProposedMode().orElseThrow());
            assertEquals(List.of("Classic"), clientListener.modes);

            assertTrue(client.sendModeAgreement(true));
            waitUntil(() -> server.getRemoteAgreed().isPresent(), server::poll);
            assertEquals(Boolean.TRUE, server.getRemoteAgreed().orElseThrow());
            assertEquals(List.of(true), serverListener.agreements);

            assertTrue(server.sendSeed(424242L));
            assertTrue(client.sendSeed(987654L));
            waitUntil(() -> client.getRemoteSeed().isPresent() && server.getRemoteSeed().isPresent(), () -> {
                server.poll();
                client.poll();
            });
            assertEquals(424242L, client.getRemoteSeed().orElseThrow());
            assertEquals(987654L, server.getRemoteSeed().orElseThrow());

            // Gameplay flow: send moveLeft, rotate, hardDrop with state bit flags in FIFO order
            assertTrue(server.sendGameplayInput(KeyEvent.VK_LEFT, P2PPacket.STATE_NONE));
            assertTrue(server.sendGameplayInput(KeyEvent.VK_UP, P2PPacket.STATE_NONE));
            assertTrue(server.sendGameplayInput(KeyEvent.VK_SPACE,
                    P2PPacket.STATE_CURR_CHANGED | P2PPacket.STATE_LEVEL_UP));
            assertTrue(client.sendGameplayInput(KeyEvent.VK_RIGHT, P2PPacket.STATE_GAME_OVER));

            waitUntil(() -> clientListener.gameplayPackets.size() == 3
                    && serverListener.gameplayPackets.size() == 1, () -> {
                server.poll();
                client.poll();
            });

            P2PPacket first = client.pollReceivedPacket();
            P2PPacket second = client.pollReceivedPacket();
            P2PPacket third = client.pollReceivedPacket();
            assertNotNull(first);
            assertNotNull(second);
            assertNotNull(third);
            assertNull(client.pollReceivedPacket());

            assertEquals(KeyEvent.VK_LEFT, first.keyCode());
            assertEquals(KeyEvent.VK_UP, second.keyCode());
            assertEquals(KeyEvent.VK_SPACE, third.keyCode());
            assertTrue(third.hasState(P2PPacket.STATE_CURR_CHANGED));
            assertTrue(third.hasState(P2PPacket.STATE_LEVEL_UP));

            P2PPacket fromClient = server.pollReceivedPacket();
            assertNotNull(fromClient);
            assertEquals(KeyEvent.VK_RIGHT, fromClient.keyCode());
            assertTrue(fromClient.hasState(P2PPacket.STATE_GAME_OVER));
        }
    }

    @Test
    void receiverValidationIgnoresCorruptPacketsWithoutDroppingConnection() throws Exception {
        ManualClock clock = new ManualClock(1_000);
        RecordingListener serverListener = new RecordingListener();
        RecordingListener clientListener = new RecordingListener();

        try (P2PNetworkManager server = new P2PNetworkManager(P2PConfig.defaults(), serverListener, clock::nowNanos);
             P2PNetworkManager client = new P2PNetworkManager(P2PConfig.defaults(), clientListener, clock::nowNanos)) {

            connectLoopbackPair(server, client);

            assertTrue(client.sendRawLineForTesting("{corrupted-json}"));
            assertTrue(client.sendRawLineForTesting("{\"KeyCode\":37,\"State\":-1,\"Client/Server_time\":1.0}"));
            assertTrue(client.sendGameplayInput(KeyEvent.VK_DOWN, P2PPacket.STATE_NONE));

            waitUntil(() -> serverListener.gameplayPackets.size() == 1
                    && server.getValidationErrorCount() == 2, server::poll);

            assertTrue(server.isConnected());
            assertEquals(2, serverListener.validationErrors.size());
            assertEquals(KeyEvent.VK_DOWN, server.pollReceivedPacket().keyCode());
        }
    }

    @Test
    void heartbeatTimerResetsOnGameplaySendAndMeasuresRttOnBothSides() throws Exception {
        ManualClock serverClock = new ManualClock(10_000);
        ManualClock clientClock = new ManualClock(20_000);
        RecordingListener serverListener = new RecordingListener();
        RecordingListener clientListener = new RecordingListener();

        try (P2PNetworkManager server = new P2PNetworkManager(P2PConfig.defaults(), serverListener, serverClock::nowNanos);
             P2PNetworkManager client = new P2PNetworkManager(P2PConfig.defaults(), clientListener, clientClock::nowNanos)) {

            connectLoopbackPair(server, client);

            // Advance 400ms and send a gameplay packet -> heartbeat idle timer must reset
            serverClock.advanceMillis(400);
            assertTrue(server.sendGameplayInput(KeyEvent.VK_DOWN, P2PPacket.STATE_NONE));
            assertEquals(0, server.getHeartbeatSentCount());

            // Advance another 400ms (800ms total, 400ms since last send) -> still no empty heartbeat
            serverClock.advanceMillis(400);
            server.poll();
            assertEquals(0, server.getHeartbeatSentCount());

            // Advance 100ms more (500ms since last send) -> empty heartbeat ping sent
            serverClock.advanceMillis(100);
            server.poll();
            assertEquals(1, server.getHeartbeatSentCount());
        }

        // Deterministic bidirectional RTT verification on both server and client
        ManualClock sClock = new ManualClock(1_000);
        ManualClock cClock = new ManualClock(9_000);
        RecordingListener sListener = new RecordingListener();
        RecordingListener cListener = new RecordingListener();

        try (P2PNetworkManager server = new P2PNetworkManager(P2PConfig.defaults(), sListener, sClock::nowNanos);
             P2PNetworkManager client = new P2PNetworkManager(P2PConfig.defaults(), cListener, cClock::nowNanos)) {

            connectLoopbackPair(server, client);

            // Server sends heartbeat after 500ms idle
            sClock.advanceMillis(500);
            server.poll();
            assertEquals(1, server.getHeartbeatSentCount());

            // Simulate 24ms network round-trip before server receives client's pong
            sClock.advanceMillis(24);
            waitUntil(() -> !sListener.rttUpdates.isEmpty(), () -> {
                client.poll();
                server.poll();
            });
            assertEquals(24.0f, server.getRttMillis(), 0.01f);

            // Client sends heartbeat after 500ms idle
            cClock.advanceMillis(500);
            client.poll();
            assertEquals(1, client.getHeartbeatSentCount());

            // Simulate 38ms network round-trip before client receives server's pong
            cClock.advanceMillis(38);
            waitUntil(() -> !cListener.rttUpdates.isEmpty(), () -> {
                server.poll();
                client.poll();
            });
            assertEquals(38.0f, client.getRttMillis(), 0.01f);
        }
    }

    @Test
    void samplesSustainedRoundTripDelayWhileMatchFramesKeepArriving() throws Exception {
        ManualClock clock = new ManualClock(0);
        RecordingListener listener = new RecordingListener();
        try (P2PNetworkManager server = new P2PNetworkManager(P2PConfig.defaults(), listener, clock::nowNanos)) {
            int port = server.startServer("127.0.0.1", 0);
            try (Socket peer = new Socket("127.0.0.1", port)) {
                peer.setSoTimeout(2_000);
                peer.setTcpNoDelay(true);
                var fromServer = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.US_ASCII));
                var toServer = new PrintWriter(peer.getOutputStream(), true, StandardCharsets.US_ASCII);
                waitUntil(server::isConnected, server::poll);

                // Real match traffic arrives every 500 ms. The peer echoes probes 1,000 ms later.
                List<Float> probes = new ArrayList<>();
                for (int tick = 1; tick <= 4; tick++) {
                    clock.advanceMillis(500);
                    assertTrue(server.sendMatchFrame("state-" + tick));
                    assertEquals("B1 state-" + tick, fromServer.readLine());
                    P2PPacket ping = P2PPacket.deserialize(fromServer.readLine());
                    assertTrue(ping.hasState(P2PPacket.STATE_HEARTBEAT_PING));
                    probes.add(ping.timestamp());
                    if (tick >= 3) {
                        toServer.println(P2PPacket.heartbeatPong(probes.get(tick - 3)).serialize());
                    }
                    toServer.println("B1 input-" + tick);
                    int expectedFrames = tick;
                    waitUntil(() -> listener.matchFrames.size() == expectedFrames, server::poll);
                }
                assertEquals(List.of(1_000f, 1_000f), listener.rttUpdates);

                // Delivery recovers and the remaining echoes arrive in TCP order.
                clock.advanceMillis(25);
                toServer.println(P2PPacket.heartbeatPong(probes.get(2)).serialize());
                toServer.println(P2PPacket.heartbeatPong(probes.get(3)).serialize());
                waitUntil(() -> listener.rttUpdates.size() == 4, server::poll);
                assertEquals(25f, server.getRttMillis(), 0.01f);
                assertTrue(server.isConnected());
                assertTrue(listener.pauseSilences.isEmpty());
            }
        }
    }

    @Test
    void exceptionFlowPausesAt999msAndResumesWhenPacketArrivesWithin10000ms() throws Exception {
        ManualClock serverClock = new ManualClock(1_000);
        ManualClock clientClock = new ManualClock(1_000);
        RecordingListener serverListener = new RecordingListener();

        try (P2PNetworkManager server = new P2PNetworkManager(P2PConfig.defaults(), serverListener, serverClock::nowNanos);
             P2PNetworkManager client = new P2PNetworkManager(P2PConfig.defaults(), P2PListener.NONE, clientClock::nowNanos)) {

            connectLoopbackPair(server, client);

            // 998ms silence: still connected, no pause request
            serverClock.advanceMillis(998);
            server.poll();
            assertEquals(P2PConnectionState.CONNECTED, server.getConnectionState());
            assertTrue(serverListener.pauseSilences.isEmpty());

            // +1ms -> 999ms silence: immediate pause request
            serverClock.advanceMillis(1);
            server.poll();
            assertEquals(P2PConnectionState.PAUSED, server.getConnectionState());
            assertTrue(server.isPaused());
            assertEquals(1, serverListener.pauseSilences.size());
            assertEquals(999L, serverListener.pauseSilences.getFirst());

            // Stay silent until 9,500ms (< 10,000ms): still paused, no duplicate pause or disconnect
            serverClock.advanceMillis(8_501);
            server.poll();
            assertEquals(P2PConnectionState.PAUSED, server.getConnectionState());
            assertEquals(1, serverListener.pauseSilences.size());
            assertTrue(serverListener.disconnectReasons.isEmpty());

            // Client sends a packet before 10,000ms -> server resumes
            assertTrue(client.sendGameplayInput(KeyEvent.VK_LEFT, P2PPacket.STATE_NONE));
            waitUntil(server::isConnected, server::poll);
            assertEquals(P2PConnectionState.CONNECTED, server.getConnectionState());
            assertEquals(1, serverListener.resumeCount.get());
            assertTrue(serverListener.disconnectReasons.isEmpty());
        }
    }

    @Test
    void exceptionFlowDisconnectsWhenSilenceReaches10000msAndSupportsCustomThresholds() throws Exception {
        // 1) Default 999ms / 10,000ms thresholds
        ManualClock serverClock = new ManualClock(1_000);
        RecordingListener serverListener = new RecordingListener();

        try (P2PNetworkManager server = new P2PNetworkManager(P2PConfig.defaults(), serverListener, serverClock::nowNanos);
             P2PNetworkManager client = new P2PNetworkManager()) {

            connectLoopbackPair(server, client);

            serverClock.advanceMillis(1_000);
            server.poll();
            assertEquals(P2PConnectionState.PAUSED, server.getConnectionState());

            serverClock.advanceMillis(9_000); // total 10,000ms
            server.poll();
            assertEquals(P2PConnectionState.DISCONNECTED, server.getConnectionState());
            assertEquals(P2PDisconnectReason.TIMEOUT, server.getLastDisconnectReason());
            assertEquals(List.of(P2PDisconnectReason.TIMEOUT), serverListener.disconnectReasons);
        }

        // 2) Custom configurable thresholds (e.g., 300ms pause, 1500ms disconnect)
        P2PConfig customConfig = new P2PConfig(17L, 200L, 300L, 1_500L, 1);
        ManualClock customClock = new ManualClock(1_000);
        RecordingListener customListener = new RecordingListener();

        try (P2PNetworkManager server = new P2PNetworkManager(customConfig, customListener, customClock::nowNanos);
             P2PNetworkManager client = new P2PNetworkManager()) {

            connectLoopbackPair(server, client);

            customClock.advanceMillis(300);
            server.poll();
            assertEquals(P2PConnectionState.PAUSED, server.getConnectionState());

            customClock.advanceMillis(1_200);
            server.poll();
            assertEquals(P2PConnectionState.DISCONNECTED, server.getConnectionState());
            assertEquals(P2PDisconnectReason.TIMEOUT, server.getLastDisconnectReason());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {9_999, 10_000, 10_001})
    void alreadyQueuedFramesCannotReviveAnExpiredConnection(long silence) throws Exception {
        ManualClock clock = new ManualClock(0);
        RecordingListener listener = new RecordingListener();
        try (P2PNetworkManager receiver = new P2PNetworkManager(P2PConfig.defaults(), listener, clock::nowNanos);
             P2PNetworkManager sender = new P2PNetworkManager()) {
            connectLoopbackPair(receiver, sender);
            clock.advanceMillis(999);
            receiver.poll();
            assertEquals(List.of(999L), listener.pauseSilences);
            assertTrue(sender.sendMatchFrame("READY"));
            assertTrue(sender.sendGameplayInput(37, P2PPacket.STATE_NONE));
            clock.advanceMillis(silence - 999);
            if (silence < 10_000) {
                waitUntil(() -> listener.gameplayPackets.size() == 1, receiver::poll);
                assertEquals(List.of("READY"), listener.matchFrames);
                assertEquals(1, listener.resumeCount.get());
                assertTrue(listener.disconnectReasons.isEmpty());
            } else {
                receiver.poll();
                receiver.poll();
                assertEquals(List.of(P2PDisconnectReason.TIMEOUT), listener.disconnectReasons);
                assertEquals(0, listener.resumeCount.get());
                assertTrue(listener.gameplayPackets.isEmpty());
                assertTrue(listener.matchFrames.isEmpty());
            }
        }
    }

    @Test
    void matchFramesAndJsonCanShareAConnectionAcrossReadBoundaries() throws Exception {
        RecordingListener listener = new RecordingListener();
        try (P2PNetworkManager receiver = new P2PNetworkManager(P2PConfig.defaults(), listener);
             P2PNetworkManager sender = new P2PNetworkManager()) {
            connectLoopbackPair(receiver, sender);
            List<String> expected = new ArrayList<>();
            String board = "A".repeat(65_534);
            for (int n = 10; n < 34; n++) {
                String snapshot = n + board;
                expected.add(snapshot);
                assertTrue(sender.sendMatchFrame(snapshot));
            }
            assertTrue(sender.sendModeProposal("Item"));
            expected.add("NEXT");
            assertTrue(sender.sendMatchFrame("NEXT"));
            waitUntil(() -> listener.matchFrames.size() == expected.size(), () -> {
                sender.poll();
                receiver.poll();
            });
            assertEquals(expected, listener.matchFrames);
            assertEquals(List.of("Item"), listener.modes);
            assertNull(receiver.pollReceivedPacket());
        }
    }

    @Test
    void rejectedMatchLinesDoNotResetSilenceOrResumeTheMatch() throws Exception {
        ManualClock clock = new ManualClock(0);
        RecordingListener listener = new RecordingListener();
        P2PConfig config = new P2PConfig(17, 20_000, 999, 10_000, 1);
        try (P2PNetworkManager receiver = new P2PNetworkManager(config, listener, clock::nowNanos);
             P2PNetworkManager sender = new P2PNetworkManager()) {
            connectLoopbackPair(receiver, sender);
            clock.advanceMillis(999);
            receiver.poll();
            List<String> badFrames = List.of("B1 ", "B1 A\rB", "B1 한글", "B1 " + "A".repeat(65_537), "B1 BAD");
            for (String frame : badFrames) assertTrue(sender.sendRawLineForTesting(frame));
            assertThrows(IllegalArgumentException.class, () -> sender.sendMatchFrame("\n"));
            waitUntil(() -> receiver.getValidationErrorCount() == badFrames.size(), () -> {
                sender.poll();
                receiver.poll();
            });
            assertTrue(receiver.isPaused());
            assertEquals(999, receiver.getSilenceMillis());
            assertEquals(0, listener.resumeCount.get());
            assertTrue(listener.matchFrames.isEmpty());
            assertTrue(sender.sendMatchFrame("VALID"));
            waitUntil(receiver::isConnected, receiver::poll);
            assertEquals(List.of("VALID"), listener.matchFrames);
            assertEquals(1, listener.resumeCount.get());
        }
    }

    @Test
    void gracefulCloseNotifiesPeerAndEmptyPollReturnsImmediately() throws Exception {
        RecordingListener serverListener = new RecordingListener();
        AtomicReference<P2PDisconnectReason> peerReason = new AtomicReference<>();
        P2PListener clientListener = new P2PListener() {
            @Override
            public void onDisconnectRequested(P2PDisconnectReason reason) {
                peerReason.set(reason);
            }
        };

        P2PNetworkManager server = new P2PNetworkManager(P2PConfig.defaults(), serverListener);
        try (P2PNetworkManager client = new P2PNetworkManager(P2PConfig.defaults(), clientListener)) {
            connectLoopbackPair(server, client);

            // Single-threaded non-blocking poll on empty socket should return well under 17ms
            AtomicLong start = new AtomicLong(System.nanoTime());
            List<P2PPacket> empty = client.poll();
            long elapsedMs = (System.nanoTime() - start.get()) / 1_000_000L;
            assertTrue(empty.isEmpty());
            assertTrue(elapsedMs < P2PConfig.DEFAULT_POLL_INTERVAL_MS,
                    "Non-blocking poll should return immediately, took " + elapsedMs + "ms");

            // Server closes -> client observes PEER_DISCONNECTED on poll
            server.close();
            assertEquals(P2PConnectionState.DISCONNECTED, server.getConnectionState());
            assertEquals(P2PDisconnectReason.LOCAL_CLOSED, server.getLastDisconnectReason());

            waitUntil(() -> client.getConnectionState() == P2PConnectionState.DISCONNECTED, client::poll);
            assertEquals(P2PDisconnectReason.PEER_DISCONNECTED, client.getLastDisconnectReason());
            assertEquals(P2PDisconnectReason.PEER_DISCONNECTED, peerReason.get());
        } finally {
            server.close();
        }
    }
}
