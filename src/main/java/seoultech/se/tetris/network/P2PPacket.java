package seoultech.se.tetris.network;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Unencrypted, validated TCP packet exchanged between P2P Tetris peers.
 *
 * <p>Core wire format:
 * {@code {"KeyCode":<int>,"State":<int>,"Client/Server_time":<float>}}
 * with optional session handshake fields ({@code Seed}, {@code Mode}, {@code Agreed})
 * when the corresponding state bit flags are set.
 */
public record P2PPacket(
        int keyCode,
        int state,
        float timestamp,
        Long seed,
        String mode,
        Boolean agreed) {

    public static final int MAX_PACKET_LENGTH = 4096;

    // Bit-flag state definitions
    public static final int STATE_NONE = 0;
    public static final int STATE_CURR_CHANGED = 1 << 0; // LockAndAdvanceBlock
    public static final int STATE_LEVEL_UP = 1 << 1;
    public static final int STATE_GAME_OVER = 1 << 2;
    public static final int STATE_HEARTBEAT_PING = 1 << 3;
    public static final int STATE_HEARTBEAT_PONG = 1 << 4;
    public static final int STATE_SEED_SYNC = 1 << 5;
    public static final int STATE_MODE_PROPOSE = 1 << 6;
    public static final int STATE_MODE_AGREE = 1 << 7;
    public static final int STATE_PAUSE = 1 << 8;
    public static final int STATE_RESUME = 1 << 9;
    public static final int STATE_DISCONNECT = 1 << 10;

    public static final int VALID_STATE_MASK =
            STATE_CURR_CHANGED
                    | STATE_LEVEL_UP
                    | STATE_GAME_OVER
                    | STATE_HEARTBEAT_PING
                    | STATE_HEARTBEAT_PONG
                    | STATE_SEED_SYNC
                    | STATE_MODE_PROPOSE
                    | STATE_MODE_AGREE
                    | STATE_PAUSE
                    | STATE_RESUME
                    | STATE_DISCONNECT;

    private static final int HEARTBEAT_FLAGS = STATE_HEARTBEAT_PING | STATE_HEARTBEAT_PONG;
    private static final int CONTROL_ONLY_FLAGS =
            HEARTBEAT_FLAGS
                    | STATE_SEED_SYNC
                    | STATE_MODE_PROPOSE
                    | STATE_MODE_AGREE
                    | STATE_DISCONNECT;

    public P2PPacket {
        if ((state & ~VALID_STATE_MASK) != 0) {
            throw new IllegalArgumentException("Invalid state bit flags: " + state);
        }
        if (!Float.isFinite(timestamp) || timestamp < 0.0f) {
            throw new IllegalArgumentException("Timestamp must be finite and non-negative: " + timestamp);
        }
        if ((state & STATE_SEED_SYNC) != 0 && seed == null) {
            throw new IllegalArgumentException("STATE_SEED_SYNC requires a seed value");
        }
        if ((state & STATE_MODE_PROPOSE) != 0) {
            if (mode == null || mode.isBlank() || !isSafeModeToken(mode)) {
                throw new IllegalArgumentException("STATE_MODE_PROPOSE requires a valid mode string");
            }
        } else if (mode != null && !isSafeModeToken(mode)) {
            throw new IllegalArgumentException("Invalid mode string: " + mode);
        }
        if ((state & STATE_MODE_AGREE) != 0 && agreed == null) {
            throw new IllegalArgumentException("STATE_MODE_AGREE requires an agreed boolean");
        }
    }

    public static P2PPacket gameplay(int keyCode, int state, float timestamp) {
        return new P2PPacket(keyCode, state, timestamp, null, null, null);
    }

    public static P2PPacket heartbeatPing(float timestamp) {
        return new P2PPacket(0, STATE_HEARTBEAT_PING, timestamp, null, null, null);
    }

    public static P2PPacket heartbeatPong(float echoedTimestamp) {
        return new P2PPacket(0, STATE_HEARTBEAT_PONG, echoedTimestamp, null, null, null);
    }

    public static P2PPacket seedSync(long seed, float timestamp) {
        return new P2PPacket(0, STATE_SEED_SYNC, timestamp, seed, null, null);
    }

    public static P2PPacket modeProposal(String mode, float timestamp) {
        return new P2PPacket(0, STATE_MODE_PROPOSE, timestamp, null, mode, null);
    }

    public static P2PPacket modeAgreement(boolean agreed, float timestamp) {
        return new P2PPacket(0, STATE_MODE_AGREE, timestamp, null, null, agreed);
    }

    public static P2PPacket disconnect(float timestamp) {
        return new P2PPacket(0, STATE_DISCONNECT, timestamp, null, null, null);
    }

    public boolean hasState(int flag) {
        if (flag == STATE_NONE) {
            return state == STATE_NONE;
        }
        return (state & flag) == flag;
    }

    /** True if this packet carries only heartbeat ping/pong metadata and no gameplay input. */
    public boolean isHeartbeatOnly() {
        return keyCode == 0 && state != 0 && (state & ~HEARTBEAT_FLAGS) == 0;
    }

    /** True if this packet carries gameplay keyCode or gameplay state flags for the board. */
    public boolean isGameplayPacket() {
        if (keyCode != 0) {
            return true;
        }
        return state == STATE_NONE || (state & ~CONTROL_ONLY_FLAGS) != 0;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder(96);
        sb.append("{\"KeyCode\":").append(keyCode)
                .append(",\"State\":").append(state)
                .append(",\"Client/Server_time\":").append(String.format(Locale.ROOT, "%.3f", timestamp));
        if (seed != null) {
            sb.append(",\"Seed\":").append(seed);
        }
        if (mode != null) {
            sb.append(",\"Mode\":\"").append(mode).append('"');
        }
        if (agreed != null) {
            sb.append(",\"Agreed\":").append(agreed);
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Parses and validates a raw JSON packet line received over TCP.
     *
     * @throws IllegalArgumentException if the payload is malformed, missing required keys,
     *                                  contains unknown/duplicate keys, or violates field constraints
     */
    public static P2PPacket deserialize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Packet payload must not be null");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Packet payload must not be empty");
        }
        if (trimmed.length() > MAX_PACKET_LENGTH) {
            throw new IllegalArgumentException("Packet exceeds max length: " + trimmed.length());
        }
        if (trimmed.charAt(0) != '{' || trimmed.charAt(trimmed.length() - 1) != '}') {
            throw new IllegalArgumentException("Packet must be a JSON object");
        }

        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Packet JSON object is empty");
        }

        Map<String, String> fields = parseFlatJsonObject(body);

        String keyCodeRaw = fields.remove("KeyCode");
        String stateRaw = fields.remove("State");
        String timeRaw = fields.remove("Client/Server_time");
        if (keyCodeRaw == null || stateRaw == null || timeRaw == null) {
            throw new IllegalArgumentException("Missing required packet fields (KeyCode, State, Client/Server_time)");
        }

        int keyCode = parseStrictInt(keyCodeRaw, "KeyCode");
        int state = parseStrictInt(stateRaw, "State");
        float timestamp = parseStrictFloat(timeRaw, "Client/Server_time");

        Long seed = null;
        String seedRaw = fields.remove("Seed");
        if (seedRaw != null) {
            seed = parseStrictLong(seedRaw, "Seed");
        }

        String mode = null;
        String modeRaw = fields.remove("Mode");
        if (modeRaw != null) {
            mode = parseStrictString(modeRaw, "Mode");
        }

        Boolean agreed = null;
        String agreedRaw = fields.remove("Agreed");
        if (agreedRaw != null) {
            agreed = parseStrictBoolean(agreedRaw, "Agreed");
        }

        if (!fields.isEmpty()) {
            throw new IllegalArgumentException("Unknown packet fields: " + fields.keySet());
        }

        return new P2PPacket(keyCode, state, timestamp, seed, mode, agreed);
    }

    private static Map<String, String> parseFlatJsonObject(String body) {
        Map<String, String> map = new HashMap<>();
        int index = 0;
        int len = body.length();

        while (index < len) {
            index = skipWhitespace(body, index);
            if (index >= len || body.charAt(index) != '"') {
                throw new IllegalArgumentException("Expected quoted key at offset " + index);
            }
            int keyEnd = body.indexOf('"', index + 1);
            if (keyEnd < 0) {
                throw new IllegalArgumentException("Unterminated key string");
            }
            String key = body.substring(index + 1, keyEnd);
            index = skipWhitespace(body, keyEnd + 1);
            if (index >= len || body.charAt(index) != ':') {
                throw new IllegalArgumentException("Expected ':' after key " + key);
            }
            index = skipWhitespace(body, index + 1);
            if (index >= len) {
                throw new IllegalArgumentException("Missing value for key " + key);
            }

            String value;
            if (body.charAt(index) == '"') {
                int valEnd = body.indexOf('"', index + 1);
                if (valEnd < 0) {
                    throw new IllegalArgumentException("Unterminated string value for key " + key);
                }
                value = body.substring(index, valEnd + 1);
                index = valEnd + 1;
            } else {
                int comma = body.indexOf(',', index);
                int valEnd = comma < 0 ? len : comma;
                value = body.substring(index, valEnd).trim();
                index = valEnd;
            }

            if (value.isEmpty()) {
                throw new IllegalArgumentException("Empty value for key " + key);
            }
            if (map.put(key, value) != null) {
                throw new IllegalArgumentException("Duplicate key: " + key);
            }

            index = skipWhitespace(body, index);
            if (index < len) {
                if (body.charAt(index) != ',') {
                    throw new IllegalArgumentException("Expected ',' at offset " + index);
                }
                index++;
                if (skipWhitespace(body, index) >= len) {
                    throw new IllegalArgumentException("Trailing comma in JSON object");
                }
            }
        }
        return map;
    }

    private static int skipWhitespace(String s, int index) {
        int i = index;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int parseStrictInt(String raw, String field) {
        if (raw.startsWith("\"")) {
            throw new IllegalArgumentException(field + " must be an integer, got string");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + field + ": " + raw, e);
        }
    }

    private static long parseStrictLong(String raw, String field) {
        if (raw.startsWith("\"")) {
            throw new IllegalArgumentException(field + " must be a long, got string");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long for " + field + ": " + raw, e);
        }
    }

    private static float parseStrictFloat(String raw, String field) {
        if (raw.startsWith("\"")) {
            throw new IllegalArgumentException(field + " must be a float, got string");
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid float for " + field + ": " + raw, e);
        }
    }

    private static String parseStrictString(String raw, String field) {
        if (raw.length() < 2 || raw.charAt(0) != '"' || raw.charAt(raw.length() - 1) != '"') {
            throw new IllegalArgumentException(field + " must be a quoted string");
        }
        return raw.substring(1, raw.length() - 1);
    }

    private static boolean parseStrictBoolean(String raw, String field) {
        if ("true".equals(raw)) {
            return true;
        }
        if ("false".equals(raw)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean for " + field + ": " + raw);
    }

    private static boolean isSafeModeToken(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '"' || c == '\\' || c < 0x20 || c == 0x7F) {
                return false;
            }
        }
        return true;
    }
}
