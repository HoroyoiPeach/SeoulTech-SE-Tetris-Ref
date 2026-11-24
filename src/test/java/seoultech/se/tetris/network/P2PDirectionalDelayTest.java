package seoultech.se.tetris.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.network.P2PBattleSession.Phase;
import seoultech.se.tetris.network.P2PBattleSession.View;

@Timeout(25)
class P2PDirectionalDelayTest {
    @ParameterizedTest(name = "host-to-client delayed = {0}")
    @ValueSource(booleans = {true, false})
    void warnsBothPlayersFreezesHostAndDiscardsOutdatedInputs(boolean towardGuest) throws Exception {
        try (Round round = new Round()) {
            AtomicBoolean held = towardGuest ? round.holdToGuest : round.holdToHost;
            int initialX = round.hostView.get().match().second().x();
            held.set(true);
            if (!towardGuest) {
                // These commands entered TCP before the sender learned about the delay.
                round.guest.input(GameCommand.RIGHT);
                round.guest.input(GameCommand.RIGHT);
            }
            await(() -> round.hostView.get().delayed() && round.guestView.get().delayed(),
                "Both players should see the 999ms silence warning on a one-way stall");
            var frozenHost = round.hostView.get().match();
            round.host.input(GameCommand.HARD_DROP);
            round.guest.input(GameCommand.HARD_DROP);
            Thread.sleep(1_200); // Cross both the game clock and the default gravity interval.
            assertEquals(frozenHost, round.hostView.get().match(), "Host must freeze while either peer is delayed");
            held.set(false);
            await(() -> !round.hostView.get().delayed() && !round.guestView.get().delayed(),
                "Both delay warnings should clear before the 10s disconnect deadline");

            // A new command and its echoed snapshot provide an ordered marker after the held bytes.
            round.guest.input(GameCommand.LEFT);
            await(() -> round.hostView.get().match().second().x() == initialX - 1
                && round.guestView.get().match().second().x() == initialX - 1,
                "Only the new input should move the guest piece");
            assertTrue(round.hostView.get().match().first().fixedCells().stream()
                .flatMap(List::stream).allMatch(Objects::isNull), "Delayed host hard drop must be discarded");
            assertTrue(round.guestView.get().match().second().fixedCells().stream()
                .flatMap(List::stream).allMatch(Objects::isNull), "Delayed guest hard drop must be discarded");
        }
    }

    @Test
    void oneWaySilenceForTenSecondsEndsTheRoundAndReturnsBothPeersToSetup() throws Exception {
        try (Round round = new Round()) {
            round.holdToGuest.set(true);
            await(() -> round.hostView.get().delayed() && round.guestView.get().delayed(),
                "Both players should see the warning before the disconnect");
            await(() -> round.hostView.get().phase() == Phase.SETUP && round.guestView.get().phase() == Phase.SETUP,
                Duration.ofSeconds(13), "Both peers should return to setup after sustained one-way loss");
            assertTrue(round.guestView.get().notice().contains("timed out"));
            assertFalse(round.hostView.get().notice().isBlank());
        }
    }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        await(condition, Duration.ofSeconds(5), message);
    }

    private static void await(BooleanSupplier condition, Duration limit, String message) throws Exception {
        long start = System.nanoTime();
        while (!condition.getAsBoolean() && System.nanoTime() - start < limit.toNanos()) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    /** Two real session endpoints, with a controllable relay in each TCP direction. */
    private static final class Round implements AutoCloseable {
        final AtomicReference<View> hostView = new AtomicReference<>();
        final AtomicReference<View> guestView = new AtomicReference<>();
        final AtomicBoolean holdToHost = new AtomicBoolean();
        final AtomicBoolean holdToGuest = new AtomicBoolean();
        final P2PBattleSession host = new P2PBattleSession(Difficulty.NORMAL, 60, hostView::set);
        final P2PBattleSession guest = new P2PBattleSession(Difficulty.NORMAL, 60, guestView::set);
        private Socket guestSocket;
        private Socket hostSocket;

        Round() throws Exception {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            try (ServerSocket relay = new ServerSocket(0, 1, loopback)) {
                relay.setSoTimeout((int) TimeUnit.SECONDS.toMillis(5));
                host.host(0);
                await(() -> host.listeningPort() > 0, "Host should bind an ephemeral port");
                guest.join(loopback.getHostAddress(), relay.getLocalPort());
                guestSocket = relay.accept();
                hostSocket = new Socket(loopback, host.listeningPort());
                hostSocket.setTcpNoDelay(true);
                guestSocket.setTcpNoDelay(true);
                forward(hostSocket, guestSocket, holdToGuest);
                forward(guestSocket, hostSocket, holdToHost);
                await(() -> guestView.get() != null && guestView.get().phase() == Phase.LOBBY,
                    "Guest should reach lobby");
                host.selectMode(GameMode.TIME_LIMIT);
                await(() -> guestView.get().mode() == GameMode.TIME_LIMIT, "Guest should see selected mode");
                host.ready();
                guest.ready();
                await(() -> guestView.get().phase() == Phase.PLAYING, "Both ready should start the round");
            } catch (Exception | AssertionError failure) {
                close();
                throw failure;
            }
        }

        private static void forward(Socket from, Socket to, AtomicBoolean hold) {
            Thread.startVirtualThread(() -> {
                try {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = from.getInputStream().read(buffer)) >= 0) {
                        while (hold.get() && !from.isClosed()) Thread.sleep(5);
                        if (from.isClosed()) return;
                        to.getOutputStream().write(buffer, 0, count);
                    }
                    to.shutdownOutput(); // Propagate a peer disconnect to the other socket.
                } catch (IOException ignored) {
                    // Closing the test releases blocked reads on both relay workers.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        @Override public void close() throws IOException {
            host.close();
            guest.close();
            holdToGuest.set(false);
            holdToHost.set(false);
            try {
                if (guestSocket != null) guestSocket.close();
            } finally {
                if (hostSocket != null) hostSocket.close();
            }
        }
    }
}
