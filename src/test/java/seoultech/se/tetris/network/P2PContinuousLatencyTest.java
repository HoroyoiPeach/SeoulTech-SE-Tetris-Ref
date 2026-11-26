package seoultech.se.tetris.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.network.P2PBattleSession.Phase;
import seoultech.se.tetris.network.P2PBattleSession.View;

class P2PContinuousLatencyTest {
    @Test
    void bothPeersWarnAboutSlowDeliveryKeepPlayingAndClearTheWarningOnRecovery() throws Exception {
        var hostView = new AtomicReference<View>();
        var guestView = new AtomicReference<View>();
        var transitMillis = new AtomicLong();
        var loopback = InetAddress.getByName("127.0.0.1");
        try (var host = new P2PBattleSession(Difficulty.NORMAL, 60, hostView::set);
             var guest = new P2PBattleSession(Difficulty.NORMAL, 60, guestView::set);
             var relayThreads = Executors.newVirtualThreadPerTaskExecutor();
             var relay = new ServerSocket(0, 1, loopback)) {
            host.host(0);
            await("host listening", () -> host.listeningPort() > 0);
            guest.join(loopback.getHostAddress(), relay.getLocalPort());
            relay.setSoTimeout(3_000);
            try (var guestSocket = relay.accept(); var hostSocket = new Socket(loopback, host.listeningPort())) {
                guestSocket.setTcpNoDelay(true);
                hostSocket.setTcpNoDelay(true);
                forward(guestSocket, hostSocket, transitMillis, relayThreads);
                forward(hostSocket, guestSocket, transitMillis, relayThreads);
                try {
                    await("both in lobby", () -> inPhase(hostView, Phase.LOBBY) && inPhase(guestView, Phase.LOBBY));
                    host.ready();
                    guest.ready();
                    await("both playing", () -> inPhase(hostView, Phase.PLAYING) && inPhase(guestView, Phase.PLAYING));

                    transitMillis.set(550); // Every byte in each direction takes 550 ms, including game inputs.
                    await("both peers show sustained-latency warning", () ->
                        hostView.get().highLatency() && guestView.get().highLatency(), () -> {
                            host.input(GameCommand.ROTATE);
                            guest.input(GameCommand.ROTATE);
                        });
                    assertFalse(hostView.get().delayed());
                    assertFalse(guestView.get().delayed());
                    int expectedX = guestView.get().match().second().x() - 1;
                    guest.input(GameCommand.LEFT);
                    await("guest can still move and see the authoritative result", () ->
                        hostView.get().match().second().x() == expectedX && guestView.get().match().second().x() == expectedX);

                    transitMillis.set(0);
                    await("both warnings clear", () -> !hostView.get().highLatency() && !guestView.get().highLatency());
                    assertTrue(inPhase(hostView, Phase.PLAYING) && inPhase(guestView, Phase.PLAYING));
                } finally {
                    relayThreads.shutdownNow();
                }
            }
        }
    }

    private record Chunk(byte[] bytes, long dueNanos) {}

    // One FIFO per direction keeps the original TCP byte order when the delay is shortened.
    private static void forward(Socket source, Socket destination, AtomicLong transitMillis,
            ExecutorService relayThreads) throws IOException {
        var input = source.getInputStream();
        var output = destination.getOutputStream();
        var queue = new LinkedBlockingQueue<Chunk>();
        relayThreads.submit(() -> {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0; ) {
                long due = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(transitMillis.get());
                queue.add(new Chunk(Arrays.copyOf(buffer, count), due));
            }
            return null;
        });
        relayThreads.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Chunk next = queue.take();
                TimeUnit.NANOSECONDS.sleep(next.dueNanos() - System.nanoTime());
                output.write(next.bytes());
            }
            return null;
        });
    }

    private static boolean inPhase(AtomicReference<View> current, Phase phase) {
        View view = current.get();
        return view != null && view.phase() == phase;
    }

    private static void await(String description, BooleanSupplier condition) throws InterruptedException {
        await(description, condition, () -> {});
    }

    private static void await(String description, BooleanSupplier condition, Runnable keepPlaying) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            keepPlaying.run();
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), description);
    }
}
