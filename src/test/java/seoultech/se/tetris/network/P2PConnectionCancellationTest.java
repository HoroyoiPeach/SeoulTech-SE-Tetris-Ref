package seoultech.se.tetris.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.network.P2PBattleSession.Phase;
import seoultech.se.tetris.network.P2PBattleSession.View;

class P2PConnectionCancellationTest {
    @Test
    void cancellingPendingDialClosesItsOwnedSocketAndRejectsLateCompletion() throws Exception {
        CountDownLatch connectStarted = new CountDownLatch(1);
        CountDownLatch releaseConnect = new CountDownLatch(1);
        AtomicReference<SocketChannel> openedChannel = new AtomicReference<>();
        P2PNetworkManager.ChannelConnector connector = (channel, address, timeoutMillis) -> {
            openedChannel.set(channel);
            connectStarted.countDown();
            awaitLatch(releaseConnect);
        };
        P2PNetworkManager network = new P2PNetworkManager(P2PConfig.defaults(), P2PListener.NONE,
            System::nanoTime, host -> new InetAddress[] { InetAddress.getLoopbackAddress() }, connector);
        P2PNetworkManager.ConnectAttempt attempt = network.prepareConnect("pending.test", 12345);
        Thread worker = new Thread(() -> {
            try {
                attempt.dial();
            } catch (Exception ignored) {
                // Cancellation may surface while a real blocking connect is being closed.
            }
        });
        worker.setDaemon(true);
        worker.start();
        try {
            assertTrue(connectStarted.await(2, TimeUnit.SECONDS));
            network.close();
            assertFalse(openedChannel.get().isOpen());
            assertThrows(ClosedChannelException.class, () -> network.completeConnect(attempt));
        } finally {
            releaseConnect.countDown();
            worker.join(2_000);
            network.close();
        }
        assertFalse(worker.isAlive());
    }

    @Test
    void cancellingBlockedResolutionReturnsToSetupAndRetryConnectsImmediately() throws Exception {
        CountDownLatch firstResolutionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstResolution = new CountDownLatch(1);
        AtomicInteger resolutions = new AtomicInteger();
        P2PNetworkManager.AddressResolver resolver = host -> {
            if (resolutions.incrementAndGet() == 1) {
                firstResolutionStarted.countDown();
                awaitLatch(releaseFirstResolution);
            }
            return new InetAddress[] { InetAddress.getLoopbackAddress() };
        };

        AtomicReference<View> hostView = new AtomicReference<>();
        AtomicReference<View> guestView = new AtomicReference<>();
        try (P2PBattleSession host = new P2PBattleSession(Difficulty.NORMAL, 30, hostView::set);
                P2PBattleSession guest = new P2PBattleSession(
                    Difficulty.NORMAL, 30, guestView::set, resolver)) {
            host.host(0);
            await("host listening", () -> phase(hostView) == Phase.LISTENING, Duration.ofSeconds(2));
            int port = host.listeningPort();

            guest.join("blocked.test", port);
            assertTrue(firstResolutionStarted.await(2, TimeUnit.SECONDS));
            await("guest connecting", () -> phase(guestView) == Phase.CONNECTING, Duration.ofSeconds(2));

            guest.leave();
            await("cancel returns to setup", () -> phase(guestView) == Phase.SETUP,
                Duration.ofMillis(500));

            guest.join("retry.test", port);
            await("retry reaches lobby while old resolver is blocked",
                () -> phase(hostView) == Phase.LOBBY && phase(guestView) == Phase.LOBBY,
                Duration.ofSeconds(2));

            releaseFirstResolution.countDown();
            Thread.sleep(100);
            assertEquals(Phase.LOBBY, phase(hostView));
            assertEquals(Phase.LOBBY, phase(guestView));
            assertEquals(2, resolutions.get());
        } finally {
            releaseFirstResolution.countDown();
        }
    }

    @Test
    void closingSessionDoesNotWaitForBlockedResolutionOrCreateALateConnection() throws Exception {
        CountDownLatch resolutionStarted = new CountDownLatch(1);
        CountDownLatch releaseResolution = new CountDownLatch(1);
        P2PNetworkManager.AddressResolver resolver = host -> {
            resolutionStarted.countDown();
            awaitLatch(releaseResolution);
            return new InetAddress[] { InetAddress.getLoopbackAddress() };
        };

        AtomicReference<View> hostView = new AtomicReference<>();
        P2PBattleSession host = new P2PBattleSession(Difficulty.NORMAL, 30, hostView::set);
        P2PBattleSession guest = new P2PBattleSession(Difficulty.NORMAL, 30, ignored -> {}, resolver);
        try {
            host.host(0);
            await("host listening", () -> phase(hostView) == Phase.LISTENING, Duration.ofSeconds(2));
            guest.join("blocked.test", host.listeningPort());
            assertTrue(resolutionStarted.await(2, TimeUnit.SECONDS));

            long started = System.nanoTime();
            guest.close();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMillis < 500, "close took " + elapsedMillis + " ms");

            releaseResolution.countDown();
            Thread.sleep(100);
            assertEquals(Phase.LISTENING, phase(hostView));
        } finally {
            releaseResolution.countDown();
            guest.close();
            host.close();
        }
    }

    private static Phase phase(AtomicReference<View> view) {
        View current = view.get();
        return current == null ? null : current.phase();
    }

    private static void await(String description, BooleanSupplier condition, Duration timeout)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), description);
    }

    private static void awaitLatch(CountDownLatch latch) throws UnknownHostException {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            UnknownHostException failure = new UnknownHostException("resolution interrupted");
            failure.initCause(ex);
            throw failure;
        }
    }
}
