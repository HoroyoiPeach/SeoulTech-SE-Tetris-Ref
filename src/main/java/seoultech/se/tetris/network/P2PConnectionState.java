package seoultech.se.tetris.network;

/**
 * Lifecycle states for a P2P TCP connection managed by {@link P2PNetworkManager}.
 */
public enum P2PConnectionState {
    /** No active socket or server listener. */
    DISCONNECTED,
    /** Server socket is bound and waiting for a client connection. */
    LISTENING,
    /** Peer connection is established and healthy. */
    CONNECTED,
    /** Peer silence exceeded the pause threshold; waiting for recovery or timeout. */
    PAUSED
}
