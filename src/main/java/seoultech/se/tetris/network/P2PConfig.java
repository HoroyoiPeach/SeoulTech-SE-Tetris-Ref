package seoultech.se.tetris.network;

/**
 * Configurable timing thresholds for {@link P2PNetworkManager}.
 *
 * <p>Defaults follow the Requirement 3 architecture specification:
 * <ul>
 *   <li>Polling interval: 17 ms (60 Hz)</li>
 *   <li>Heartbeat / RTT probe interval: 500 ms</li>
 *   <li>Pause threshold on silence: 999 ms</li>
 *   <li>Disconnect threshold on silence: 10,000 ms</li>
 *   <li>Socket read timeout: 1 ms (non-blocking channel used during polling)</li>
 * </ul>
 */
public record P2PConfig(
        long pollIntervalMs,
        long heartbeatIntervalMs,
        long pauseThresholdMs,
        long disconnectThresholdMs,
        int socketTimeoutMs) {

    public static final long DEFAULT_POLL_INTERVAL_MS = 17L;
    public static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 500L;
    public static final long DEFAULT_PAUSE_THRESHOLD_MS = 999L;
    public static final long DEFAULT_DISCONNECT_THRESHOLD_MS = 10_000L;
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 1;

    public P2PConfig {
        if (pollIntervalMs <= 0) {
            throw new IllegalArgumentException("pollIntervalMs must be positive");
        }
        if (heartbeatIntervalMs <= 0) {
            throw new IllegalArgumentException("heartbeatIntervalMs must be positive");
        }
        if (pauseThresholdMs <= 0) {
            throw new IllegalArgumentException("pauseThresholdMs must be positive");
        }
        if (disconnectThresholdMs <= pauseThresholdMs) {
            throw new IllegalArgumentException("disconnectThresholdMs must exceed pauseThresholdMs");
        }
        if (socketTimeoutMs < 0) {
            throw new IllegalArgumentException("socketTimeoutMs must be non-negative");
        }
    }

    public static P2PConfig defaults() {
        return new P2PConfig(
                DEFAULT_POLL_INTERVAL_MS,
                DEFAULT_HEARTBEAT_INTERVAL_MS,
                DEFAULT_PAUSE_THRESHOLD_MS,
                DEFAULT_DISCONNECT_THRESHOLD_MS,
                DEFAULT_SOCKET_TIMEOUT_MS);
    }
}
