package seoultech.se.tetris.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Single-threaded, non-blocking TCP P2P communication manager for Tetris multiplayer.
 *
 * <p>Implements the Requirement 3 transport contract without coupling to Swing UI
 * or {@code GameEngine}:
 * <ul>
 *   <li>TCP transport ({@code TCP_NODELAY=true}, non-blocking reads with &lt;1 ms response)</li>
 *   <li>Unencrypted JSON serialization with strict receiver-side validation</li>
 *   <li>60 Hz polling design (default 17 ms interval)</li>
 *   <li>Periodic RTT heartbeat probes (default 500 ms), reset on gameplay sends</li>
 *   <li>Bidirectional RTT computation by echoing sender timestamps</li>
 *   <li>Exception flow: pause request at &ge;999 ms silence, resume on packet before 10,000 ms,
 *       disconnect request at &ge;10,000 ms silence</li>
 * </ul>
 */
public final class P2PNetworkManager implements AutoCloseable {
    private static final int READ_BUFFER_CAPACITY = 8192;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    private static final int MAX_ZERO_WRITE_RETRIES = 64;

    private final P2PConfig config;
    private final P2PListener listener;
    private final LongSupplier nanoClock;

    private final ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_CAPACITY);
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);
    private final Deque<P2PPacket> receivedPackets = new ArrayDeque<>();

    private ServerSocketChannel serverChannel;
    private SocketChannel peerChannel;
    private P2PConnectionState connectionState = P2PConnectionState.DISCONNECTED;
    private P2PDisconnectReason lastDisconnectReason;

    private long sessionStartNanos;
    private long lastReceivedTimeMs;
    private long lastSendTimeMs;
    private long lastRttProbeTimeMs;
    private float lastRttMillis = -1.0f;

    private int sentPacketCount;
    private int heartbeatSentCount;
    private int validationErrorCount;

    private Long remoteSeed;
    private String proposedMode;
    private Boolean remoteAgreed;

    public P2PNetworkManager() {
        this(P2PConfig.defaults(), P2PListener.NONE, System::nanoTime);
    }

    public P2PNetworkManager(P2PConfig config, P2PListener listener) {
        this(config, listener, System::nanoTime);
    }

    public P2PNetworkManager(P2PConfig config, P2PListener listener, LongSupplier nanoClock) {
        this.config = Objects.requireNonNull(config, "config");
        this.listener = listener == null ? P2PListener.NONE : listener;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    /**
     * Starts listening for a single peer on the given TCP port (0 selects an ephemeral port).
     *
     * @return the bound local TCP port
     */
    public int startServer(int port) throws IOException {
        return startServer(null, port);
    }

    /**
     * Starts listening for a single peer bound to a specific local IP/host and TCP port.
     * Passing {@code null} or blank {@code bindHost} binds to the wildcard address.
     *
     * @return the bound local TCP port
     */
    public int startServer(String bindHost, int port) throws IOException {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid TCP port: " + port);
        }
        closeQuietly();

        InetSocketAddress bindAddress = (bindHost == null || bindHost.isBlank())
                ? new InetSocketAddress(port)
                : new InetSocketAddress(InetAddress.getByName(bindHost), port);

        ServerSocketChannel server = ServerSocketChannel.open();
        try {
            server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            server.bind(bindAddress);
            server.configureBlocking(false);
            this.serverChannel = server;
            this.connectionState = P2PConnectionState.LISTENING;
            this.lastDisconnectReason = null;
            return ((InetSocketAddress) server.getLocalAddress()).getPort();
        } catch (IOException e) {
            closeChannelQuietly(server);
            throw e;
        }
    }

    /**
     * Non-blocking check for an incoming client connection while in {@link P2PConnectionState#LISTENING}.
     *
     * @return {@code true} if a peer connection was accepted
     */
    public boolean acceptPendingConnection() throws IOException {
        if (connectionState != P2PConnectionState.LISTENING || serverChannel == null) {
            return false;
        }
        SocketChannel accepted = serverChannel.accept();
        if (accepted == null) {
            return false;
        }
        closeChannelQuietly(serverChannel);
        serverChannel = null;
        establishChannel(accepted);
        return true;
    }

    /**
     * Connects to a remote server peer over TCP.
     */
    public void connect(String host, int port) throws IOException {
        connect(host, port, DEFAULT_CONNECT_TIMEOUT_MS);
    }

    /**
     * Connects to a remote server peer over TCP with a custom connect timeout.
     */
    public void connect(String host, int port, int connectTimeoutMs) throws IOException {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid TCP port: " + port);
        }
        if (connectTimeoutMs < 0) {
            throw new IllegalArgumentException("connectTimeoutMs must be non-negative");
        }
        closeQuietly();

        SocketChannel channel = SocketChannel.open();
        try {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.socket().connect(new InetSocketAddress(host, port), connectTimeoutMs);
            establishChannel(channel);
        } catch (IOException e) {
            closeChannelQuietly(channel);
            throw e;
        }
    }

    /**
     * Sends a gameplay input / state packet and resets the idle RTT heartbeat timer.
     */
    public boolean sendGameplayInput(int keyCode, int stateFlags) {
        if (!isActive()) {
            return false;
        }
        long nowMs = nowMillis();
        int effectiveState = stateFlags;
        if ((effectiveState & P2PPacket.STATE_HEARTBEAT_PONG) == 0
                && nowMs - lastRttProbeTimeMs >= config.heartbeatIntervalMs()) {
            effectiveState |= P2PPacket.STATE_HEARTBEAT_PING;
            lastRttProbeTimeMs = nowMs;
        }
        P2PPacket packet = P2PPacket.gameplay(keyCode, effectiveState, currentRelativeTimeMillis());
        if (sendPacketInternal(packet)) {
            lastSendTimeMs = nowMs;
            sentPacketCount++;
            return true;
        }
        return false;
    }

    /**
     * Shares the random block generator seed with the peer before match start.
     */
    public boolean sendSeed(long seed) {
        if (!isActive()) {
            return false;
        }
        P2PPacket packet = P2PPacket.seedSync(seed, currentRelativeTimeMillis());
        if (sendPacketInternal(packet)) {
            lastSendTimeMs = nowMillis();
            sentPacketCount++;
            return true;
        }
        return false;
    }

    /**
     * Proposes a game mode (e.g., Classic, Item, Time Limit) to the peer.
     */
    public boolean sendModeProposal(String mode) {
        if (!isActive()) {
            return false;
        }
        P2PPacket packet = P2PPacket.modeProposal(mode, currentRelativeTimeMillis());
        if (sendPacketInternal(packet)) {
            lastSendTimeMs = nowMillis();
            sentPacketCount++;
            return true;
        }
        return false;
    }

    /**
     * Sends mode agreement or rejection to the peer.
     */
    public boolean sendModeAgreement(boolean agreed) {
        if (!isActive()) {
            return false;
        }
        P2PPacket packet = P2PPacket.modeAgreement(agreed, currentRelativeTimeMillis());
        if (sendPacketInternal(packet)) {
            lastSendTimeMs = nowMillis();
            sentPacketCount++;
            return true;
        }
        return false;
    }

    /**
     * Sends an empty heartbeat ping packet to measure RTT.
     */
    public boolean sendHeartbeatPing() {
        if (!isActive()) {
            return false;
        }
        long nowMs = nowMillis();
        P2PPacket ping = P2PPacket.heartbeatPing(currentRelativeTimeMillis());
        if (sendPacketInternal(ping)) {
            lastSendTimeMs = nowMs;
            lastRttProbeTimeMs = nowMs;
            heartbeatSentCount++;
            sentPacketCount++;
            return true;
        }
        return false;
    }

    /**
     * Performs a single non-blocking poll cycle (intended to be called every ~17 ms / 60 Hz):
     * accepts pending server connections, reads and validates incoming packets, updates RTT,
     * checks silence thresholds (pause / resume / disconnect), and emits periodic heartbeats.
     *
     * @return gameplay packets received during this poll cycle (also available via {@link #pollReceivedPacket()})
     */
    public List<P2PPacket> poll() {
        List<P2PPacket> newlyReceived = new ArrayList<>();

        if (connectionState == P2PConnectionState.LISTENING) {
            try {
                acceptPendingConnection();
            } catch (IOException e) {
                disconnectInternal(P2PDisconnectReason.IO_ERROR, false);
            }
            return newlyReceived;
        }

        if (!isActive() || peerChannel == null) {
            return newlyReceived;
        }

        readAvailablePackets(newlyReceived);

        if (!isActive()) {
            return newlyReceived;
        }

        long nowMs = nowMillis();
        long silenceMs = Math.max(0L, nowMs - lastReceivedTimeMs);

        if (silenceMs >= config.disconnectThresholdMs()) {
            disconnectInternal(P2PDisconnectReason.TIMEOUT, false);
            return newlyReceived;
        }

        if (silenceMs >= config.pauseThresholdMs() && connectionState == P2PConnectionState.CONNECTED) {
            connectionState = P2PConnectionState.PAUSED;
            listener.onPauseRequested(silenceMs);
        }

        if (isActive() && nowMs - lastSendTimeMs >= config.heartbeatIntervalMs()) {
            sendHeartbeatPing();
        }

        return newlyReceived;
    }

    /**
     * Dequeues the next received gameplay packet, or {@code null} if none is queued.
     */
    public P2PPacket pollReceivedPacket() {
        return receivedPackets.pollFirst();
    }

    /**
     * Drains all queued gameplay packets in FIFO order.
     */
    public List<P2PPacket> drainReceivedPackets() {
        List<P2PPacket> drained = new ArrayList<>(receivedPackets);
        receivedPackets.clear();
        return drained;
    }

    public P2PConnectionState getConnectionState() {
        return connectionState;
    }

    public boolean isConnected() {
        return connectionState == P2PConnectionState.CONNECTED;
    }

    public boolean isPaused() {
        return connectionState == P2PConnectionState.PAUSED;
    }

    public boolean isActive() {
        return connectionState == P2PConnectionState.CONNECTED
                || connectionState == P2PConnectionState.PAUSED;
    }

    public float getRttMillis() {
        return lastRttMillis;
    }

    public long getSilenceMillis() {
        if (!isActive()) {
            return 0L;
        }
        return Math.max(0L, nowMillis() - lastReceivedTimeMs);
    }

    public P2PDisconnectReason getLastDisconnectReason() {
        return lastDisconnectReason;
    }

    public int getSentPacketCount() {
        return sentPacketCount;
    }

    public int getHeartbeatSentCount() {
        return heartbeatSentCount;
    }

    public int getValidationErrorCount() {
        return validationErrorCount;
    }

    public P2PConfig getConfig() {
        return config;
    }

    public Optional<Long> getRemoteSeed() {
        return Optional.ofNullable(remoteSeed);
    }

    public Optional<String> getProposedMode() {
        return Optional.ofNullable(proposedMode);
    }

    public Optional<Boolean> getRemoteAgreed() {
        return Optional.ofNullable(remoteAgreed);
    }

    /**
     * Returns the bound local socket address (listening server socket or connected peer socket).
     */
    public Optional<InetSocketAddress> getLocalAddress() {
        try {
            if (peerChannel != null && peerChannel.isOpen()) {
                return Optional.ofNullable((InetSocketAddress) peerChannel.getLocalAddress());
            }
            if (serverChannel != null && serverChannel.isOpen()) {
                return Optional.ofNullable((InetSocketAddress) serverChannel.getLocalAddress());
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Returns the connected remote peer socket address, if connected.
     */
    public Optional<InetSocketAddress> getRemoteAddress() {
        try {
            if (peerChannel != null && peerChannel.isOpen()) {
                return Optional.ofNullable((InetSocketAddress) peerChannel.getRemoteAddress());
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Resolves an active local private IPv4 address (e.g., 192.168.x.x / 10.x.x.x / 172.16-31.x.x)
     * for LAN / internal P2P communication, falling back to {@code 127.0.0.1} when offline.
     */
    public static String resolveLocalInternalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address
                            && addr.isSiteLocalAddress()
                            && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
            // Fall back to loopback below.
        }
        return "127.0.0.1";
    }

    /**
     * Gracefully disconnects from the peer and releases all socket resources.
     */
    @Override
    public void close() {
        if (connectionState == P2PConnectionState.DISCONNECTED
                && serverChannel == null
                && peerChannel == null) {
            return;
        }
        disconnectInternal(P2PDisconnectReason.LOCAL_CLOSED, isActive());
    }

    /** Package-private helper for testing receiver-side validation over a real TCP socket. */
    boolean sendRawLineForTesting(String rawLine) {
        if (!isActive()) {
            return false;
        }
        return writeLine(rawLine);
    }

    private void establishChannel(SocketChannel channel) throws IOException {
        channel.configureBlocking(false);
        channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        channel.socket().setSoTimeout(config.socketTimeoutMs());

        this.peerChannel = channel;
        this.readBuffer.clear();
        this.lineBuffer.reset();
        this.receivedPackets.clear();
        this.lastDisconnectReason = null;
        this.lastRttMillis = -1.0f;
        this.sentPacketCount = 0;
        this.heartbeatSentCount = 0;
        this.validationErrorCount = 0;
        this.remoteSeed = null;
        this.proposedMode = null;
        this.remoteAgreed = null;

        this.sessionStartNanos = nanoClock.getAsLong();
        long nowMs = nowMillis();
        this.lastReceivedTimeMs = nowMs;
        this.lastSendTimeMs = nowMs;
        this.lastRttProbeTimeMs = nowMs;
        this.connectionState = P2PConnectionState.CONNECTED;

        listener.onConnected();
    }

    private void readAvailablePackets(List<P2PPacket> newlyReceived) {
        try {
            while (isActive() && peerChannel != null) {
                readBuffer.clear();
                int bytesRead = peerChannel.read(readBuffer);
                if (bytesRead == 0) {
                    break;
                }
                if (bytesRead < 0) {
                    disconnectInternal(P2PDisconnectReason.PEER_DISCONNECTED, false);
                    break;
                }
                readBuffer.flip();
                while (readBuffer.hasRemaining() && isActive()) {
                    byte b = readBuffer.get();
                    if (b == '\n') {
                        String line = lineBuffer.toString(StandardCharsets.UTF_8);
                        lineBuffer.reset();
                        handleIncomingLine(line, newlyReceived);
                    } else if (b != '\r') {
                        if (lineBuffer.size() <= P2PPacket.MAX_PACKET_LENGTH + 1) {
                            lineBuffer.write(b);
                        }
                    }
                }
            }
        } catch (IOException e) {
            disconnectInternal(P2PDisconnectReason.IO_ERROR, false);
        }
    }

    private void handleIncomingLine(String rawLine, List<P2PPacket> newlyReceived) {
        final P2PPacket packet;
        try {
            packet = P2PPacket.deserialize(rawLine);
        } catch (IllegalArgumentException e) {
            validationErrorCount++;
            listener.onValidationError(rawLine, e.getMessage());
            return;
        }

        lastReceivedTimeMs = nowMillis();

        if (connectionState == P2PConnectionState.PAUSED) {
            connectionState = P2PConnectionState.CONNECTED;
            listener.onResumeRequested();
        }

        if (packet.hasState(P2PPacket.STATE_DISCONNECT)) {
            disconnectInternal(P2PDisconnectReason.PEER_DISCONNECTED, false);
            return;
        }

        if (packet.hasState(P2PPacket.STATE_HEARTBEAT_PONG)) {
            float rtt = Math.max(0.0f, currentRelativeTimeMillis() - packet.timestamp());
            lastRttMillis = rtt;
            listener.onRttUpdated(rtt);
        }

        if (packet.hasState(P2PPacket.STATE_HEARTBEAT_PING)) {
            P2PPacket pong = P2PPacket.heartbeatPong(packet.timestamp());
            sendPacketInternal(pong);
        }

        if (packet.hasState(P2PPacket.STATE_SEED_SYNC) && packet.seed() != null) {
            remoteSeed = packet.seed();
            listener.onSeedReceived(packet.seed());
        }

        if (packet.hasState(P2PPacket.STATE_MODE_PROPOSE) && packet.mode() != null) {
            proposedMode = packet.mode();
            listener.onModeProposed(packet.mode());
        }

        if (packet.hasState(P2PPacket.STATE_MODE_AGREE) && packet.agreed() != null) {
            remoteAgreed = packet.agreed();
            listener.onModeAgreed(packet.agreed());
        }

        if (packet.isGameplayPacket()) {
            receivedPackets.addLast(packet);
            newlyReceived.add(packet);
            listener.onPacketReceived(packet);
        }
    }

    private boolean sendPacketInternal(P2PPacket packet) {
        return writeLine(packet.serialize());
    }

    private boolean writeLine(String line) {
        if (peerChannel == null || !peerChannel.isOpen()) {
            return false;
        }
        byte[] bytes = (line + '\n').getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int zeroWriteRetries = 0;
        try {
            while (buffer.hasRemaining()) {
                int written = peerChannel.write(buffer);
                if (written == 0) {
                    if (++zeroWriteRetries >= MAX_ZERO_WRITE_RETRIES) {
                        disconnectInternal(P2PDisconnectReason.IO_ERROR, false);
                        return false;
                    }
                    Thread.yield();
                } else {
                    zeroWriteRetries = 0;
                }
            }
            return true;
        } catch (IOException e) {
            disconnectInternal(P2PDisconnectReason.IO_ERROR, false);
            return false;
        }
    }

    private void disconnectInternal(P2PDisconnectReason reason, boolean notifyPeer) {
        boolean wasActiveOrListening = connectionState != P2PConnectionState.DISCONNECTED;
        if (notifyPeer && peerChannel != null && peerChannel.isOpen()) {
            try {
                byte[] bytes = (P2PPacket.disconnect(currentRelativeTimeMillis()).serialize() + '\n')
                        .getBytes(StandardCharsets.UTF_8);
                peerChannel.write(ByteBuffer.wrap(bytes));
            } catch (IOException ignored) {
                // Best-effort disconnect notification.
            }
        }

        closeQuietly();
        connectionState = P2PConnectionState.DISCONNECTED;
        lastDisconnectReason = reason;

        if (wasActiveOrListening) {
            listener.onDisconnectRequested(reason);
        }
    }

    private void closeQuietly() {
        closeChannelQuietly(peerChannel);
        peerChannel = null;
        closeChannelQuietly(serverChannel);
        serverChannel = null;
        lineBuffer.reset();
        readBuffer.clear();
    }

    private static void closeChannelQuietly(java.nio.channels.Channel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // Ignore close failures during cleanup.
            }
        }
    }

    private long nowMillis() {
        return nanoClock.getAsLong() / 1_000_000L;
    }

    private float currentRelativeTimeMillis() {
        long elapsedNanos = Math.max(0L, nanoClock.getAsLong() - sessionStartNanos);
        return elapsedNanos / 1_000_000.0f;
    }
}
