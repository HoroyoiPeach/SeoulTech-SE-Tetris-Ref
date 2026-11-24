package seoultech.se.tetris.network;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.BattleMatch;
import seoultech.se.tetris.game.BattleMatchSnapshot;
import seoultech.se.tetris.game.BattleMatchUpdate;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.PlayerSide;

/** One connection, owned by one event loop. Only the host runs the two game engines. */
public final class P2PBattleSession implements AutoCloseable {
    public enum Phase { SETUP, LISTENING, CONNECTING, LOBBY, PLAYING, RESULT }

    public record View(Phase phase, boolean host, GameMode mode, boolean hostReady,
            boolean guestReady, BattleMatchSnapshot match, boolean delayed, String notice) { }

    private final Difficulty difficulty;
    private final Duration limit;
    private final Consumer<View> render;
    private final ScheduledThreadPoolExecutor loop;
    private final P2PNetworkManager network;
    private volatile boolean closed;
    private volatile int listeningPort;
    private Phase phase = Phase.SETUP;
    private boolean host;
    private boolean hostReady;
    private boolean guestReady;
    private boolean localDelay;
    private boolean peerDelay;
    private boolean delayed;
    private int pendingDelayAcks;
    private String notice = "";
    private GameMode mode = GameMode.CLASSIC;
    private volatile long round;
    private BattleMatch engine;
    private BattleMatchSnapshot state;
    // Real time spent waiting for a stalled connection is excluded from the match clock.
    private long logicalNanos;
    private long lastRealNanos;
    private long lastAdvance;
    private long nextWakeup;

    public P2PBattleSession(Difficulty difficulty, int limitSeconds, Consumer<View> render) {
        this.difficulty = Objects.requireNonNull(difficulty);
        if (limitSeconds <= 0) throw new IllegalArgumentException("Time limit must be positive");
        limit = Duration.ofSeconds(limitSeconds);
        this.render = Objects.requireNonNull(render);
        network = new P2PNetworkManager(P2PConfig.defaults(), new ConnectionEvents());
        loop = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "tetris-p2p-session");
            thread.setDaemon(true);
            return thread;
        });
        loop.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        loop.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        loop.scheduleWithFixedDelay(this::tick, 0, network.getConfig().pollIntervalMs(), TimeUnit.MILLISECONDS);
    }

    public int listeningPort() { return listeningPort; }

    public void host(int port) {
        submit(() -> {
            if (phase != Phase.SETUP) return;
            host = true;
            try {
                listeningPort = network.startServer(port);
                phase = Phase.LISTENING;
                notice = "Waiting for a player";
                publish();
            } catch (IOException | IllegalArgumentException ex) {
                reset("Could not host: " + ex.getMessage());
            }
        });
    }

    public void join(String ip, int port) {
        submit(() -> {
            if (phase != Phase.SETUP) return;
            host = false;
            phase = Phase.CONNECTING;
            notice = "Connecting";
            publish();
            try {
                network.connect(ip, port);
            } catch (IOException | IllegalArgumentException ex) {
                reset("Could not connect: " + ex.getMessage());
            }
        });
    }

    public void selectMode(GameMode selected) {
        Objects.requireNonNull(selected);
        submit(() -> {
            if (host && phase == Phase.LOBBY && !delayed && mode != selected) {
                mode = selected;
                openLobby();
            }
        });
    }

    public void ready() {
        long requestedRound = round;
        submit(() -> {
            if (phase != Phase.LOBBY || delayed || requestedRound != round) return;
            if (host) {
                hostReady = true;
                updateReadiness();
            } else if (!guestReady) {
                send(new BattleMessage.Ready(round));
            }
        });
    }

    public void input(GameCommand command) {
        Objects.requireNonNull(command);
        long requestedRound = round;
        submit(() -> {
            if (phase != Phase.PLAYING || delayed || requestedRound != round) return;
            if (host) play(PlayerSide.FIRST, command);
            else send(new BattleMessage.Input(round, command));
        });
    }

    public void rematch() {
        long requestedRound = round;
        submit(() -> {
            if (phase != Phase.RESULT || delayed || requestedRound != round) return;
            if (host) openLobby();
            else send(new BattleMessage.Rematch(round));
        });
    }

    public void leave() { submit(network::close); }

    private synchronized void submit(Runnable action) {
        if (!closed) loop.execute(action);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        loop.execute(network::close);
        loop.shutdown();
    }

    private void tick() {
        network.poll();
        // Legacy key packets have a separate consumer API. This session accepts typed inputs only.
        while (network.pollReceivedPacket() != null) { }
        if (host && phase == Phase.PLAYING && !delayed) {
            sampleClock();
            if (logicalNanos >= nextWakeup) advance();
        }
    }

    private void sampleClock() {
        long now = System.nanoTime();
        if (!delayed) logicalNanos += Math.max(0, now - lastRealNanos);
        lastRealNanos = now;
    }

    private void play(PlayerSide side, GameCommand command) {
        sampleClock();
        advance();
        if (phase == Phase.PLAYING) apply(engine.handle(side, command));
    }

    private void advance() {
        long elapsed = logicalNanos - lastAdvance;
        lastAdvance = logicalNanos;
        apply(engine.advance(elapsed));
    }

    private void apply(BattleMatchUpdate update) {
        nextWakeup = update.nextWakeupNanos().isPresent()
            ? logicalNanos + update.nextWakeupNanos().getAsLong() : Long.MAX_VALUE;
        if (update.snapshot().equals(state)) return;
        state = update.snapshot();
        phase = state.decision().isPresent() ? Phase.RESULT : Phase.PLAYING;
        if (send(new BattleMessage.State(round, state))) {
            if (phase == Phase.RESULT) openLobby();
            else publish();
        }
    }

    private void openLobby() {
        round++;
        pendingDelayAcks = 0;
        hostReady = false;
        guestReady = false;
        engine = null;
        phase = Phase.LOBBY;
        notice = "";
        sendLobby();
    }

    private void sendLobby() {
        if (send(new BattleMessage.Lobby(round, mode, hostReady, guestReady))) publish();
    }

    private void updateReadiness() {
        if (!hostReady || !guestReady || delayed) {
            sendLobby();
            return;
        }
        logicalNanos = 0;
        lastAdvance = 0;
        lastRealNanos = System.nanoTime();
        engine = new BattleMatch(new GameConfig(mode, difficulty), limit, () -> logicalNanos);
        apply(engine.start());
    }

    private boolean send(BattleMessage message) {
        return network.sendMatchFrame(BattleMessageCodec.encode(message));
    }

    private void receive(BattleMessage message) {
        if (message instanceof BattleMessage.Link link) {
            if (link.round() != round) return;
            peerDelay = link.delayed();
            updateDelay();
            if (link.delayed()) send(new BattleMessage.LinkAck(round));
        } else if (message instanceof BattleMessage.LinkAck ack) {
            if (ack.round() == round && pendingDelayAcks > 0) pendingDelayAcks--;
        } else if (host) {
            if (message instanceof BattleMessage.Lobby || message instanceof BattleMessage.State) {
                throw new IllegalArgumentException("Host owns lobby and game state");
            }
            if (message.round() != round) return;
            if (message instanceof BattleMessage.Ready && phase == Phase.LOBBY) {
                guestReady = true;
                updateReadiness();
            } else if (message instanceof BattleMessage.Input input && phase == Phase.PLAYING
                    && !delayed && pendingDelayAcks == 0) {
                play(PlayerSide.SECOND, input.command());
            } else if (message instanceof BattleMessage.Rematch && phase == Phase.RESULT && !delayed) {
                openLobby();
            }
        } else if (message instanceof BattleMessage.Lobby lobby) {
            if (lobby.round() < round || (lobby.round() == round && phase != Phase.LOBBY)) return;
            round = lobby.round();
            mode = lobby.mode();
            phase = Phase.LOBBY;
            hostReady = lobby.hostReady();
            guestReady = lobby.guestReady();
            notice = "";
            publish();
        } else if (message instanceof BattleMessage.State frame) {
            if (frame.round() != round || (phase != Phase.LOBBY && phase != Phase.PLAYING)) return;
            BattleMatchSnapshot next = frame.snapshot();
            if (!next.started() || next.first().mode() != mode || next.second().mode() != mode) {
                throw new IllegalArgumentException("Unexpected round state");
            }
            hostReady = true;
            guestReady = true;
            state = next;
            phase = state.decision().isPresent() ? Phase.RESULT : Phase.PLAYING;
            if (phase == Phase.RESULT) send(new BattleMessage.Rematch(round));
            else publish();
        } else {
            throw new IllegalArgumentException("Client expects authoritative lobby or state");
        }
    }

    private void updateDelay() {
        boolean next = localDelay || peerDelay;
        if (delayed == next) return;
        if (host && phase == Phase.PLAYING) sampleClock();
        delayed = next;
        publish();
        if (!delayed && host && phase == Phase.LOBBY && hostReady && guestReady) updateReadiness();
    }

    private void localLinkChanged(boolean value) {
        localDelay = value;
        if (round > 0) {
            // TCP delivers the peer's ack after all inputs it queued before seeing our warning.
            if (host && value) pendingDelayAcks++;
            if (!send(new BattleMessage.Link(round, value))) return;
        }
        updateDelay();
    }

    private void reset(String message) {
        if (engine != null) engine.stop();
        engine = null;
        state = null;
        host = false;
        hostReady = false;
        guestReady = false;
        localDelay = false;
        peerDelay = false;
        delayed = false;
        pendingDelayAcks = 0;
        mode = GameMode.CLASSIC;
        listeningPort = 0;
        round = 0;
        phase = Phase.SETUP;
        notice = message;
        publish();
    }

    private void publish() {
        if (!closed) render.accept(new View(phase, host, mode, hostReady, guestReady, state, delayed, notice));
    }

    private final class ConnectionEvents implements P2PListener {
        @Override public void onConnected() {
            if (host) openLobby();
        }

        @Override public void onMatchFrame(String payload) {
            BattleMessage message = BattleMessageCodec.decode(payload);
            receive(message);
        }

        @Override public void onPauseRequested(long silenceMillis) { localLinkChanged(true); }
        @Override public void onResumeRequested() { localLinkChanged(false); }

        @Override public void onDisconnectRequested(P2PDisconnectReason reason) {
            reset(switch (reason) {
                case LOCAL_CLOSED -> "";
                case PEER_DISCONNECTED -> "The other player disconnected";
                case TIMEOUT -> "Connection timed out";
                case IO_ERROR -> "Connection lost";
            });
        }
    }
}
