package seoultech.se.tetris.network;

/**
 * Callback boundary between {@link P2PNetworkManager} and game/UI layers.
 * Game code can react to connection, gameplay input, handshake, pause/resume,
 * disconnect, validation errors, and RTT updates without coupling the transport
 * to Swing or {@code GameEngine}.
 */
public interface P2PListener {
    P2PListener NONE = new P2PListener() {};

    default void onConnected() {}

    default void onPacketReceived(P2PPacket packet) {}

    default void onSeedReceived(long seed) {}

    default void onModeProposed(String mode) {}

    default void onModeAgreed(boolean agreed) {}

    default void onPauseRequested(long silenceMillis) {}

    default void onResumeRequested() {}

    default void onDisconnectRequested(P2PDisconnectReason reason) {}

    default void onValidationError(String rawPayload, String reason) {}

    default void onRttUpdated(float rttMillis) {}
}
