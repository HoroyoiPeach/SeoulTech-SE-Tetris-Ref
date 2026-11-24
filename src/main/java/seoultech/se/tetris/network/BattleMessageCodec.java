package seoultech.se.tetris.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.attack.AttackQueue;
import seoultech.se.tetris.attack.AttackRow;
import seoultech.se.tetris.game.BattleMatchSnapshot;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GamePhase;
import seoultech.se.tetris.game.GameSnapshot;
import seoultech.se.tetris.game.MatchDecision;
import seoultech.se.tetris.game.MatchEndReason;
import seoultech.se.tetris.game.MatchOutcome;

/** Version 1 match payload: big-endian primitives and named enums inside a single Base64 line. */
public final class BattleMessageCodec {
    private static final int VERSION = 1;
    private static final int MAX_ENCODED_LENGTH = 65_536;
    private static final int WIDTH = 10;
    private static final int HEIGHT = 20;
    private static final int PIECE_LIMIT = 4;

    private BattleMessageCodec() {}

    public static String encode(BattleMessage message) {
        var buffer = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(buffer)) {
            out.writeByte(VERSION);
            out.writeLong(message.round());
            switch (message) {
                case BattleMessage.Lobby lobby -> {
                    out.writeByte('L');
                    out.writeUTF(lobby.mode().name());
                    out.writeBoolean(lobby.hostReady());
                    out.writeBoolean(lobby.guestReady());
                }
                case BattleMessage.Ready ignored -> out.writeByte('R');
                case BattleMessage.Link link -> {
                    out.writeByte('D');
                    out.writeBoolean(link.delayed());
                }
                case BattleMessage.LinkAck ignored -> out.writeByte('A');
                case BattleMessage.Input input -> {
                    out.writeByte('I');
                    out.writeUTF(input.command().name());
                }
                case BattleMessage.State state -> {
                    out.writeByte('S');
                    writeMatch(out, state.snapshot());
                }
                case BattleMessage.Rematch ignored -> out.writeByte('N');
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot encode match message", e);
        }
        String encoded = Base64.getEncoder().encodeToString(buffer.toByteArray());
        range(encoded.length(), 1, MAX_ENCODED_LENGTH);
        return encoded;
    }

    public static BattleMessage decode(String payload) {
        if (payload == null) throw new IllegalArgumentException("Missing match payload");
        range(payload.length(), 1, MAX_ENCODED_LENGTH);
        byte[] bytes = Base64.getDecoder().decode(payload);
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Unknown match version");
            long round = in.readLong();
            BattleMessage message = switch (in.readUnsignedByte()) {
                case 'L' -> new BattleMessage.Lobby(round, GameMode.valueOf(in.readUTF()), flag(in), flag(in));
                case 'R' -> new BattleMessage.Ready(round);
                case 'D' -> new BattleMessage.Link(round, flag(in));
                case 'A' -> new BattleMessage.LinkAck(round);
                case 'I' -> new BattleMessage.Input(round, GameCommand.valueOf(in.readUTF()));
                case 'S' -> new BattleMessage.State(round, readMatch(in));
                case 'N' -> new BattleMessage.Rematch(round);
                default -> throw new IllegalArgumentException("Unknown match message");
            };
            if (in.read() != -1) throw new IllegalArgumentException("Trailing match data");
            return message;
        } catch (IOException e) {
            throw new IllegalArgumentException("Incomplete or invalid match message", e);
        }
    }

    private static void writeMatch(DataOutputStream out, BattleMatchSnapshot state) throws IOException {
        writeBoard(out, state.first());
        writeBoard(out, state.second());
        writeAttacks(out, state.firstPendingAttackRows());
        writeAttacks(out, state.secondPendingAttackRows());
        out.writeLong(state.remainingSeconds());
        out.writeBoolean(state.started());
        out.writeBoolean(state.stopped());
        out.writeBoolean(state.decision().isPresent());
        if (state.decision().isPresent()) {
            MatchDecision decision = state.decision().orElseThrow();
            out.writeUTF(decision.outcome().name());
            out.writeUTF(decision.reason().name());
            out.writeInt(decision.localScore());
            out.writeInt(decision.remoteScore());
        }
    }

    private static BattleMatchSnapshot readMatch(DataInputStream in) throws IOException {
        GameSnapshot first = readBoard(in);
        GameSnapshot second = readBoard(in);
        List<AttackRow> firstPending = readAttacks(in);
        List<AttackRow> secondPending = readAttacks(in);
        long seconds = in.readLong();
        boolean started = flag(in);
        boolean stopped = flag(in);
        Optional<MatchDecision> decision = flag(in) ? Optional.of(new MatchDecision(
            MatchOutcome.valueOf(in.readUTF()), MatchEndReason.valueOf(in.readUTF()), in.readInt(),
            in.readInt())) : Optional.empty();
        return new BattleMatchSnapshot(first, second, firstPending, secondPending, seconds, started, stopped, decision);
    }

    private static void writeBoard(DataOutputStream out, GameSnapshot board) throws IOException {
        if (board.width() != WIDTH || board.height() != HEIGHT) throw new IllegalArgumentException("Board dimensions");
        out.writeUTF(board.mode().name());
        out.writeUTF(board.difficulty().name());
        out.writeUTF(board.phase().name());
        out.writeBoolean(board.paused());
        writeCells(out, board.fixedCells(), WIDTH, HEIGHT);
        writePiece(out, board.current());
        writePiece(out, board.next());
        if (board.current() != null) {
            if (board.x() == null || board.y() == null || board.ghostY() == null) {
                throw new IllegalArgumentException("Missing current piece coordinates");
            }
            out.writeByte(range(board.x(), -PIECE_LIMIT, WIDTH - 1));
            out.writeByte(range(board.y(), -PIECE_LIMIT, HEIGHT - 1));
            out.writeByte(range(board.ghostY(), -PIECE_LIMIT, HEIGHT - 1));
        } else if (board.x() != null || board.y() != null || board.ghostY() != null) {
            throw new IllegalArgumentException("Piece coordinates without a current piece");
        }
        out.writeInt(range(board.score(), 0, Integer.MAX_VALUE));
        out.writeInt(range(board.clearedLines(), 0, Integer.MAX_VALUE));
        out.writeByte(range(board.level(), 1, 19));
        out.writeLong(positive(board.dropIntervalNanos()));
        out.writeByte(range(board.highlightedRows().size(), 0, HEIGHT));
        for (int row : board.highlightedRows()) out.writeByte(range(row, 0, HEIGHT - 1));
        writeEffect(out, board.effect());
    }

    private static GameSnapshot readBoard(DataInputStream in) throws IOException {
        GameMode mode = GameMode.valueOf(in.readUTF());
        Difficulty difficulty = Difficulty.valueOf(in.readUTF());
        GamePhase phase = GamePhase.valueOf(in.readUTF());
        boolean paused = flag(in);
        var fixed = readCells(in, WIDTH, HEIGHT);
        var current = readPiece(in);
        var next = readPiece(in);
        Integer x = null, y = null, ghost = null;
        if (current != null) {
            x = range(in.readByte(), -PIECE_LIMIT, WIDTH - 1);
            y = range(in.readByte(), -PIECE_LIMIT, HEIGHT - 1);
            ghost = range(in.readByte(), -PIECE_LIMIT, HEIGHT - 1);
        }
        int score = range(in.readInt(), 0, Integer.MAX_VALUE);
        int lines = range(in.readInt(), 0, Integer.MAX_VALUE);
        int level = range(in.readUnsignedByte(), 1, 19);
        long interval = positive(in.readLong());
        int count = range(in.readUnsignedByte(), 0, HEIGHT);
        List<Integer> highlighted = new ArrayList<>(count);
        for (int i = 0; i < count; i++) highlighted.add(range(in.readUnsignedByte(), 0, HEIGHT - 1));
        return new GameSnapshot(WIDTH, HEIGHT, mode, difficulty, phase, paused, fixed, current, next,
            x, y, ghost, score, lines, level, interval, highlighted, readEffect(in));
    }

    private static void writePiece(DataOutputStream out, GameSnapshot.Piece piece) throws IOException {
        out.writeBoolean(piece != null);
        if (piece == null) return;
        out.writeUTF(label(piece.kind()));
        int height = range(piece.cells().size(), 1, PIECE_LIMIT);
        int width = range(piece.cells().getFirst().size(), 1, PIECE_LIMIT);
        out.writeByte(width);
        out.writeByte(height);
        writeCells(out, piece.cells(), width, height);
    }

    private static GameSnapshot.Piece readPiece(DataInputStream in) throws IOException {
        if (!flag(in)) return null;
        String kind = label(in.readUTF());
        int width = range(in.readUnsignedByte(), 1, PIECE_LIMIT);
        int height = range(in.readUnsignedByte(), 1, PIECE_LIMIT);
        return new GameSnapshot.Piece(kind, readCells(in, width, height));
    }

    private static void writeCells(DataOutputStream out, List<List<GameSnapshot.Cell>> rows, int width, int height)
            throws IOException {
        if (rows.size() != height) throw new IllegalArgumentException("Cell matrix height");
        for (var row : rows) {
            if (row.size() != width) throw new IllegalArgumentException("Cell matrix width");
            for (var cell : row) {
                out.writeBoolean(cell != null);
                if (cell != null) {
                    out.writeInt(range(cell.value(), 1, Integer.MAX_VALUE));
                    out.writeUTF(label(cell.text()));
                    out.writeInt(cell.argb());
                }
            }
        }
    }

    private static List<List<GameSnapshot.Cell>> readCells(DataInputStream in, int width, int height)
            throws IOException {
        List<List<GameSnapshot.Cell>> rows = new ArrayList<>(height);
        for (int y = 0; y < height; y++) {
            List<GameSnapshot.Cell> row = new ArrayList<>(width);
            for (int x = 0; x < width; x++) {
                row.add(flag(in) ? new GameSnapshot.Cell(range(in.readInt(), 1, Integer.MAX_VALUE),
                    label(in.readUTF()), in.readInt()) : null);
            }
            rows.add(row);
        }
        return rows;
    }

    private static void writeEffect(DataOutputStream out, GameSnapshot.Effect effect) throws IOException {
        out.writeBoolean(effect != null);
        if (effect == null) return;
        out.writeUTF(label(effect.kind()));
        out.writeByte(range(effect.frame(), 0, HEIGHT));
        out.writeByte(range(effect.overlayCells().size(), 0, WIDTH * HEIGHT));
        for (var cell : effect.overlayCells()) {
            out.writeByte(range(cell.x(), 0, WIDTH - 1));
            out.writeByte(range(cell.y(), 0, HEIGHT - 1));
            out.writeUTF(label(cell.text()));
            out.writeInt(cell.argb());
        }
    }

    private static GameSnapshot.Effect readEffect(DataInputStream in) throws IOException {
        if (!flag(in)) return null;
        String kind = label(in.readUTF());
        int frame = range(in.readUnsignedByte(), 0, HEIGHT);
        int count = range(in.readUnsignedByte(), 0, WIDTH * HEIGHT);
        List<GameSnapshot.OverlayCell> overlay = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            overlay.add(new GameSnapshot.OverlayCell(range(in.readUnsignedByte(), 0, WIDTH - 1),
                range(in.readUnsignedByte(), 0, HEIGHT - 1), label(in.readUTF()), in.readInt()));
        }
        return new GameSnapshot.Effect(kind, frame, overlay);
    }

    private static void writeAttacks(DataOutputStream out, List<AttackRow> rows) throws IOException {
        out.writeByte(range(rows.size(), 0, AttackQueue.MAX_ROWS));
        for (AttackRow row : rows) {
            if (row.width() != WIDTH) throw new IllegalArgumentException("Attack row width");
            int mask = 0;
            for (int col = 0; col < WIDTH; col++) {
                if (row.occupiedAt(col)) mask |= 1 << col;
            }
            out.writeShort(mask);
        }
    }

    private static List<AttackRow> readAttacks(DataInputStream in) throws IOException {
        int count = range(in.readUnsignedByte(), 0, AttackQueue.MAX_ROWS);
        List<AttackRow> attacks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int mask = range(in.readUnsignedShort(), 0, (1 << WIDTH) - 1);
            boolean[] occupied = new boolean[WIDTH];
            for (int col = 0; col < WIDTH; col++) occupied[col] = (mask & (1 << col)) != 0;
            attacks.add(new AttackRow(occupied));
        }
        return attacks;
    }

    private static String label(String value) {
        range(value.length(), 1, 32);
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) throw new IllegalArgumentException("Control in label");
        }
        return value;
    }

    private static boolean flag(DataInputStream in) throws IOException {
        return range(in.readUnsignedByte(), 0, 1) == 1;
    }

    private static long positive(long value) {
        if (value <= 0) throw new IllegalArgumentException("Nonpositive drop interval");
        return value;
    }

    private static int range(int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException("Match value outside range: " + value);
        return value;
    }
}
