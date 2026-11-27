package seoultech.se.tetris.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.BattleMatchSnapshot;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.MatchEndReason;
import seoultech.se.tetris.game.MatchOutcome;
import seoultech.se.tetris.network.P2PBattleSession.Phase;
import seoultech.se.tetris.network.P2PBattleSession.View;

/** Exercises the public session over real TCP, including messages from an earlier round. */
class P2PBattleSessionIntegrationTest {
    private static final String LOOPBACK = "127.0.0.1";

    private static final class Peer implements AutoCloseable {
        final AtomicReference<View> current = new AtomicReference<>();
        final P2PBattleSession session = new P2PBattleSession(Difficulty.NORMAL, 3, current::set);

        boolean in(Phase phase) {
            View view = current.get();
            return view != null && view.phase() == phase;
        }

        BattleMatchSnapshot match() {
            return current.get().match();
        }

        int listen() throws Exception {
            session.host(0);
            await("server listening", () -> in(Phase.LISTENING));
            int port = session.listeningPort();
            assertTrue(port > 0);
            return port;
        }

        @Override public void close() {
            session.close();
        }
    }

    private static void await(String description, BooleanSupplier condition) throws Exception {
        await(description, condition, () -> {}, Duration.ofSeconds(5));
    }

    private static void await(String description, BooleanSupplier condition, Runnable pump,
            Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            pump.run();
            Thread.sleep(5);
        }
        assertTrue(condition.getAsBoolean(), description);
    }

    private static void connect(Peer host, Peer guest) throws Exception {
        guest.session.join(LOOPBACK, host.listen());
        await("both peers in the lobby", () -> host.in(Phase.LOBBY) && guest.in(Phase.LOBBY));
        assertTrue(host.current.get().host());
        assertFalse(guest.current.get().host());
    }

    @ParameterizedTest
    @EnumSource(GameMode.class)
    void bothPeersPlayTheSelectedModeAndCanStartAnotherRoundOnTheSameConnection(GameMode mode)
            throws Exception {
        try (Peer host = new Peer(); Peer guest = new Peer()) {
            connect(host, guest);
            host.session.selectMode(mode);
            await("both peers show selected mode", () ->
                host.current.get().mode() == mode && guest.current.get().mode() == mode);

            guest.session.ready();
            await("guest alone cannot start", () -> guest.current.get().guestReady()
                && host.current.get().guestReady());
            assertTrue(host.in(Phase.LOBBY) && guest.in(Phase.LOBBY));
            assertFalse(host.current.get().hostReady());
            host.session.ready();
            await("both peers start", () -> host.in(Phase.PLAYING) && guest.in(Phase.PLAYING));

            assertEquals(mode, host.match().first().mode());
            assertEquals(mode, guest.match().second().mode());
            int p1 = host.match().first().x();
            int p2 = host.match().second().x();
            host.session.input(GameCommand.RIGHT);
            await("guest sees only the host move", () ->
                guest.match().first().x() == p1 + 1 && guest.match().second().x() == p2);
            guest.session.input(GameCommand.LEFT);
            await("guest input reaches both screens", () ->
                host.match().second().x() == p2 - 1 && guest.match().second().x() == p2 - 1);
            assertEquals(p1 + 1, host.match().first().x());

            if (mode == GameMode.TIME_LIMIT) {
                guest.session.input(GameCommand.SOFT_DROP);
                await("guest score advantage", () -> host.match().second().score() > host.match().first().score());
            } else {
                for (int i = 0; i < 30; i++) guest.session.input(GameCommand.HARD_DROP);
            }
            await("both result screens retain the final result without an extra click", () ->
                host.in(Phase.RESULT) && guest.in(Phase.RESULT)
                    && host.match() != null && guest.match() != null
                    && host.match().decision().isPresent() && guest.match().decision().isPresent());
            assertEquals(host.match(), guest.match());
            var result = host.match().decision().orElseThrow();
            assertEquals(mode == GameMode.TIME_LIMIT ? MatchEndReason.TIME_EXPIRED : MatchEndReason.REMOTE_TOP_OUT,
                result.reason());
            assertEquals(mode == GameMode.TIME_LIMIT ? MatchOutcome.REMOTE_WIN : MatchOutcome.LOCAL_WIN,
                result.outcome());

            host.session.acknowledgeResult();
            await("guest sees that host reviewed the result", () ->
                host.in(Phase.RESULT) && guest.in(Phase.RESULT)
                    && host.current.get().hostReady() && guest.current.get().hostReady());
            assertFalse(host.current.get().guestReady() || guest.current.get().guestReady());
            guest.session.acknowledgeResult();
            await("both acknowledgements return the original connection to lobby", () ->
                host.in(Phase.LOBBY) && guest.in(Phase.LOBBY));
            assertNull(host.match());
            assertNull(guest.match());
            assertFalse(host.current.get().hostReady() || host.current.get().guestReady());
            host.session.ready();
            await("host alone cannot restart", () -> guest.current.get().hostReady());
            assertTrue(guest.in(Phase.LOBBY));
            guest.session.ready();
            await("new match started by both peers", () -> host.in(Phase.PLAYING) && guest.in(Phase.PLAYING));
            assertEquals(0, guest.match().first().score());
            assertEquals(0, guest.match().second().score());
            assertTrue(guest.match().firstPendingAttackRows().isEmpty());
            assertTrue(guest.match().secondPendingAttackRows().isEmpty());
        }
    }

    private static final class RawPeer implements P2PListener, AutoCloseable {
        final P2PNetworkManager transport = new P2PNetworkManager(P2PConfig.defaults(), this);
        final List<BattleMessage.Lobby> lobbies = new ArrayList<>();
        BattleMatchSnapshot lastState;
        final List<Boolean> linkChanges = new ArrayList<>();
        final List<BattleMessage.ResultReady> resultReadiness = new ArrayList<>();
        int pongs;

        @Override public void onMatchFrame(String payload) {
            switch (BattleMessageCodec.decode(payload)) {
                case BattleMessage.Lobby lobby -> lobbies.add(lobby);
                case BattleMessage.State state -> lastState = state.snapshot();
                case BattleMessage.Link link -> {
                    linkChanges.add(link.delayed());
                    if (link.delayed()) send(new BattleMessage.LinkAck(link.round()));
                }
                case BattleMessage.ResultReady ready -> resultReadiness.add(ready);
                default -> throw new AssertionError("Unexpected server message");
            }
        }

        @Override public void onRttUpdated(float millis) { pongs++; }
        @Override public void close() { transport.close(); }

        void send(BattleMessage message) {
            assertTrue(transport.sendMatchFrame(BattleMessageCodec.encode(message)));
        }

        void awaitWire(String description, BooleanSupplier condition) throws Exception {
            await(description, condition, transport::poll, Duration.ofSeconds(5));
        }
    }

    @Test
    void lateUserActionsBelongToTheRoundShownOnTheirScreen() throws Exception {
        try (Peer host = new Peer(); Peer guest = new Peer()) {
            connect(host, guest);
            long oldLobby = guest.current.get().round();
            host.session.selectMode(GameMode.ITEM);
            await("new lobby delivered", () -> guest.current.get().mode() == GameMode.ITEM);
            guest.session.ready(oldLobby);
            host.session.ready();
            await("host readiness returned to guest", () -> guest.current.get().hostReady());
            assertTrue(guest.in(Phase.LOBBY));
            assertFalse(guest.current.get().guestReady());

            guest.session.ready(guest.current.get().round());
            await("new round started", () -> guest.in(Phase.PLAYING) && host.in(Phase.PLAYING));
            int x = guest.match().second().x();
            guest.session.input(GameCommand.HARD_DROP, oldLobby);
            guest.session.input(GameCommand.RIGHT, guest.current.get().round());
            await("current move processed", () -> host.match().second().x() == x + 1);
            assertTrue(host.match().second().fixedCells().stream().flatMap(List::stream).allMatch(Objects::isNull));
        }
    }

    @Test
    void modeChangeDropsPreviouslyQueuedReadinessAndCommands() throws Exception {
        try (Peer host = new Peer(); RawPeer client = new RawPeer()) {
            client.transport.connect(LOOPBACK, host.listen());
            client.awaitWire("first lobby", () -> !client.lobbies.isEmpty());
            long oldRound = client.lobbies.getLast().round();
            client.send(new BattleMessage.Ready(oldRound));
            await("old guest ready", () -> host.current.get().guestReady());
            host.session.selectMode(GameMode.ITEM);
            client.awaitWire("mode change creates new round",
                () -> client.lobbies.getLast().round() > oldRound);
            var current = client.lobbies.getLast();
            assertEquals(GameMode.ITEM, current.mode());
            assertFalse(current.hostReady() || current.guestReady());
            host.session.ready();
            client.awaitWire("just host ready", () -> client.lobbies.getLast().hostReady());

            client.send(new BattleMessage.Ready(oldRound));
            int pongs = client.pongs;
            assertTrue(client.transport.sendHeartbeatPing());
            client.awaitWire("TCP processed the preceding stale ready", () -> client.pongs > pongs);
            assertTrue(host.in(Phase.LOBBY));
            assertFalse(host.current.get().guestReady());
            assertNull(client.lastState);

            client.send(new BattleMessage.Ready(current.round()));
            client.awaitWire("new round started", () -> client.lastState != null);
            int originalX = client.lastState.second().x();
            client.send(new BattleMessage.Input(oldRound, GameCommand.HARD_DROP));
            client.send(new BattleMessage.Input(current.round(), GameCommand.RIGHT));
            client.awaitWire("current input applied", () -> client.lastState.second().x() == originalX + 1);
            assertTrue(client.lastState.second().fixedCells().stream()
                .flatMap(List::stream).allMatch(Objects::isNull), "stale hard drop must not lock a block");
        }
    }

    @Test
    void failedConnectAndPeerDisconnectReturnToSetupAndAllowAnotherConnection() throws Exception {
        try (Peer host = new Peer(); Peer guest = new Peer()) {
            int unused;
            try (var reserved = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))) {
                unused = reserved.getLocalPort();
            }
            guest.session.join(LOOPBACK, unused);
            await("connection failure reported", () -> guest.in(Phase.SETUP) && !guest.current.get().notice().isBlank());
            connect(host, guest);
            host.session.ready();
            guest.session.ready();
            await("both joined the running game", () -> host.in(Phase.PLAYING) && guest.in(Phase.PLAYING));

            host.session.leave();
            await("guest returned to setup with a disconnect notice", () ->
                guest.in(Phase.SETUP) && !guest.current.get().notice().isBlank());
            connect(host, guest);
            assertFalse(host.current.get().hostReady() || host.current.get().guestReady());
        }
    }

    @Test
    void silenceDisplaysADelayRecoversAndUltimatelyReturnsToSetup() throws Exception {
        try (Peer host = new Peer(); RawPeer client = new RawPeer()) {
            client.transport.connect(LOOPBACK, host.listen());
            client.awaitWire("lobby", () -> !client.lobbies.isEmpty());
            host.session.ready();
            client.send(new BattleMessage.Ready(client.lobbies.getLast().round()));
            client.awaitWire("both players started", () -> client.lastState != null);
            await("host displays delay in the match", () ->
                host.in(Phase.PLAYING) && host.current.get().delayed());
            assertTrue(client.transport.sendHeartbeatPing());
            client.awaitWire("both peers observe delay and recovery", () ->
                !host.current.get().delayed() && client.linkChanges.equals(List.of(true, false)));
            await("silent peer times out", () -> host.in(Phase.SETUP), () -> {}, Duration.ofSeconds(13));
            assertFalse(host.current.get().notice().isBlank());
        }
    }

    @Test
    void resultAcknowledgementsMadeDuringDelayAreAppliedAfterRecovery() throws Exception {
        try (Peer host = new Peer(); RawPeer client = new RawPeer()) {
            client.transport.connect(LOOPBACK, host.listen());
            client.awaitWire("lobby", () -> !client.lobbies.isEmpty());
            long finishedRound = client.lobbies.getLast().round();
            host.session.ready();
            client.send(new BattleMessage.Ready(finishedRound));
            client.awaitWire("match started", () -> client.lastState != null && client.lastState.started());
            for (int i = 0; i < 32; i++) {
                client.send(new BattleMessage.Input(finishedRound, GameCommand.HARD_DROP));
            }
            client.awaitWire("final result received", () ->
                client.lastState != null && client.lastState.decision().isPresent());
            await("host retains result while the client is silent", () ->
                host.in(Phase.RESULT) && host.current.get().delayed());

            host.session.acknowledgeResult();
            await("host remembers its acknowledgement during the delay", () ->
                host.in(Phase.RESULT) && host.current.get().hostReady());
            client.send(new BattleMessage.Rematch(finishedRound));
            client.awaitWire("recovery applies both acknowledgements and opens a clean lobby", () ->
                !client.lobbies.isEmpty() && client.lobbies.getLast().round() > finishedRound);
            await("host returned to lobby", () -> host.in(Phase.LOBBY));
            assertNull(host.match());
            assertFalse(host.current.get().hostReady() || host.current.get().guestReady());
        }
    }
}
