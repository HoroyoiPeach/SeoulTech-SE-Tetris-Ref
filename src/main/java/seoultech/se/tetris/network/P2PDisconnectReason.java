package seoultech.se.tetris.network;

/**
 * Reason why a P2P connection transitioned to {@link P2PConnectionState#DISCONNECTED}.
 */
public enum P2PDisconnectReason {
    /** Closed intentionally by the local peer. */
    LOCAL_CLOSED,
    /** Closed gracefully or EOF received from the remote peer. */
    PEER_DISCONNECTED,
    /** Remote peer stayed silent past the disconnect threshold (default 10,000 ms). */
    TIMEOUT,
    /** An unexpected socket I/O error occurred. */
    IO_ERROR
}
