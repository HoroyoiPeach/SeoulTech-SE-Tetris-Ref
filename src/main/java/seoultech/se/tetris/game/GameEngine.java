package seoultech.se.tetris.game;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import seoultech.se.tetris.blocks.AnchorBlock;
import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.blocks.BombBlock;
import seoultech.se.tetris.rules.CollisionRules;

/** UI-independent owner of one Tetris game's rules and mutable state. */
public final class GameEngine {
    public static final long CLEAR_HIGHLIGHT_NANOS = 210_000_000L;
    private static final long HIGHLIGHT_REPAINT_NANOS = 30_000_000L;

    private final GameConfig config;
    private final BlockSource blocks;
    final GameState state = new GameState();

    public GameEngine(GameConfig config, BlockSource blocks) {
        this.config = Objects.requireNonNull(config, "config");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
    }

    public GameUpdate start() {
        if (state.phase != GamePhase.NEW) return update(null, null);
        state.current = requireFresh(blocks.nextBlock(config.difficulty()));
        state.next = requireFresh(blocks.nextBlock(config.difficulty()));
        state.phase = GamePhase.ACTIVE;
        return update(null, null);
    }

    public GameUpdate handle(GameCommand command) {
        Objects.requireNonNull(command, "command");
        if (state.phase == GamePhase.ACTIVE && command == GameCommand.TOGGLE_PAUSE) {
            state.paused = !state.paused;
            state.gravityElapsedNanos = 0;
            return update(null, null);
        }
        if (state.phase != GamePhase.ACTIVE || state.paused) return update(null, null);
        return switch (command) {
            case LEFT -> { moveHorizontal(-1); yield update(null, null); }
            case RIGHT -> { moveHorizontal(1); yield update(null, null); }
            case ROTATE -> { rotate(); yield update(null, null); }
            case SOFT_DROP -> dropOne();
            case HARD_DROP -> hardDrop();
            case TOGGLE_PAUSE -> throw new AssertionError("handled above");
        };
    }

    public GameUpdate advance(long elapsedNanos) {
        if (elapsedNanos < 0) throw new IllegalArgumentException("elapsedNanos must be nonnegative");
        if (state.phase == GamePhase.GAME_OVER || state.phase == GamePhase.STOPPED
                || state.phase == GamePhase.NEW) return update(null, null);

        if (!state.highlightedRows.isEmpty()) {
            long highlightRemaining = CLEAR_HIGHLIGHT_NANOS - state.highlightElapsedNanos;
            if (elapsedNanos >= highlightRemaining) {
                state.highlightedRows.clear();
                state.highlightElapsedNanos = 0;
            } else {
                state.highlightElapsedNanos += elapsedNanos;
            }
        }
        if (elapsedNanos == 0 || state.phase != GamePhase.ACTIVE || state.paused) {
            return update(null, null);
        }
        long gravityRemaining = dropIntervalNanos() - state.gravityElapsedNanos;
        if (elapsedNanos < gravityRemaining) {
            state.gravityElapsedNanos += elapsedNanos;
            return update(null, null);
        }
        state.gravityElapsedNanos = 0;
        return dropOne();
    }

    public GameUpdate continueAfterLock(long turnId) {
        if (state.phase != GamePhase.AWAITING_SPAWN || state.pendingLock == null
                || state.pendingLock.turnId() != turnId) return update(null, null);
        state.pendingLock = null;
        if (queuedBlockCollidesAtSpawn()) {
            state.phase = GamePhase.GAME_OVER;
            state.highlightedRows.clear();
            state.highlightElapsedNanos = 0;
            state.gravityElapsedNanos = 0;
            GameEnd end = new GameEnd(state.score, state.clearedLines, state.level);
            return update(null, end);
        }
        state.current = state.next;
        state.next = requireFresh(blocks.nextBlock(config.difficulty()));
        state.x = 3;
        state.y = 0;
        state.phase = GamePhase.ACTIVE;
        return update(null, null);
    }

    public GameUpdate stop() {
        if (state.phase == GamePhase.STOPPED || state.phase == GamePhase.GAME_OVER) {
            return update(null, null);
        }
        state.phase = GamePhase.STOPPED;
        state.pendingLock = null;
        state.highlightedRows.clear();
        state.gravityElapsedNanos = 0;
        state.highlightElapsedNanos = 0;
        return update(null, null);
    }

    public GameSnapshot snapshot() {
        List<List<GameSnapshot.Cell>> fixedRows = new ArrayList<>(GameState.HEIGHT);
        for (int row = 0; row < GameState.HEIGHT; row++) {
            ArrayList<GameSnapshot.Cell> cells = new ArrayList<>(GameState.WIDTH);
            for (int column = 0; column < GameState.WIDTH; column++) {
                cells.add(toSnapshotCell(state.fixed[row][column]));
            }
            fixedRows.add(cells);
        }
        boolean showsActive = state.phase == GamePhase.ACTIVE && state.current != null;
        return new GameSnapshot(
            GameState.WIDTH, GameState.HEIGHT, config.mode(), config.difficulty(), state.phase,
            state.paused, fixedRows, showsActive ? piece(state.current) : null,
            state.next == null ? null : piece(state.next), showsActive ? state.x : null,
            showsActive ? state.y : null, showsActive ? ghostY() : null, state.score,
            state.clearedLines, state.level, dropIntervalNanos(), bitSetValues(state.highlightedRows), null);
    }

    private GameUpdate dropOne() {
        if (canDown()) {
            state.y++;
            state.score += state.level;
            return update(null, null);
        }
        return lockCurrent();
    }

    private GameUpdate hardDrop() {
        while (canDown()) {
            state.y++;
            state.score += state.level;
        }
        return lockCurrent();
    }

    private GameUpdate lockCurrent() {
        long turnId = state.nextTurnId++;
        Block block = state.current;
        for (int row = 0; row < block.height(); row++) {
            for (int column = 0; column < block.width(); column++) {
                int value = block.getShape(column, row);
                if (value == 0) continue;
                String marker = block.getText(column, row);
                state.fixed[state.y + row][state.x + column] = new GameState.FixedCell(
                    value, marker, displayArgb(marker, block.getColor().getRGB()), turnId);
            }
        }
        List<ClearBatch> batches = clearCompletedRows(turnId);
        LockResolution resolution = new LockResolution(turnId, batches, state.score,
            state.clearedLines, state.level);
        state.pendingLock = resolution;
        state.current = null;
        state.phase = GamePhase.AWAITING_SPAWN;
        return update(resolution, null);
    }

    private List<ClearBatch> clearCompletedRows(long turnId) {
        BitSet rows = completedRows();
        if (rows.isEmpty()) return List.of();
        ArrayList<Integer> indices = new ArrayList<>(rows.cardinality());
        ArrayList<List<GameSnapshot.Cell>> before = new ArrayList<>(rows.cardinality());
        ArrayList<List<Boolean>> origins = new ArrayList<>(rows.cardinality());
        for (int row = rows.nextSetBit(0); row >= 0; row = rows.nextSetBit(row + 1)) {
            indices.add(row);
            ArrayList<GameSnapshot.Cell> cells = new ArrayList<>(GameState.WIDTH);
            ArrayList<Boolean> mask = new ArrayList<>(GameState.WIDTH);
            for (GameState.FixedCell cell : state.fixed[row]) {
                cells.add(toSnapshotCell(cell));
                mask.add(cell != null && cell.originTurnId() == turnId);
            }
            before.add(cells);
            origins.add(mask);
        }
        removeRows(rows);
        state.highlightedRows.clear();
        state.highlightedRows.or(rows);
        state.highlightElapsedNanos = 0;
        return List.of(new ClearBatch(ClearBatch.Cause.COMPLETED_ROWS, indices, before, origins));
    }

    private void removeRows(BitSet rows) {
        int destination = GameState.HEIGHT - 1;
        for (int source = GameState.HEIGHT - 1; source >= 0; source--) {
            if (rows.get(source)) continue;
            if (destination != source) state.fixed[destination] = state.fixed[source];
            destination--;
        }
        while (destination >= 0) state.fixed[destination--] = new GameState.FixedCell[GameState.WIDTH];
        for (int combo = 1; combo <= rows.cardinality(); combo++) {
            state.score += 100 * combo;
            state.clearedLines++;
            if (state.clearedLines % 10 == 0 && state.level < 19) state.level++;
        }
    }

    private BitSet completedRows() {
        BitSet rows = new BitSet(GameState.HEIGHT);
        for (int row = 0; row < GameState.HEIGHT; row++) {
            boolean complete = true;
            for (GameState.FixedCell cell : state.fixed[row]) {
                if (cell == null) { complete = false; break; }
            }
            if (complete) rows.set(row);
        }
        return rows;
    }

    private boolean queuedBlockCollidesAtSpawn() {
        for (int row = 0; row < state.next.height(); row++) {
            for (int column = 0; column < state.next.width(); column++) {
                int fixedValue = state.fixed[row][column + 3] == null ? 0
                    : state.fixed[row][column + 3].value();
                if (fixedValue + state.next.getShape(column, row) > 1) return true;
            }
        }
        return false;
    }

    private void moveHorizontal(int delta) {
        if (delta < 0 && CollisionRules.canLeft(occupancy(), state.current, state.x, state.y)) state.x--;
        if (delta > 0 && CollisionRules.canRight(occupancy(), state.current, state.x, state.y)) state.x++;
    }

    private void rotate() {
        if (state.current instanceof AnchorBlock || state.current instanceof BombBlock) return;
        int[] position = CollisionRules.rotationPosition(occupancy(), state.current, state.x, state.y);
        if (position == null) return;
        state.x = position[0];
        state.y = position[1];
        state.current.rotate();
    }

    private boolean canDown() {
        return CollisionRules.canDown(occupancy(), state.current, state.x, state.y);
    }

    private int ghostY() {
        int ghost = state.y;
        int[][] grid = occupancy();
        while (CollisionRules.canDown(grid, state.current, state.x, ghost)) ghost++;
        return ghost;
    }

    private int[][] occupancy() {
        int[][] grid = new int[GameState.HEIGHT][GameState.WIDTH];
        for (int row = 0; row < GameState.HEIGHT; row++) {
            for (int column = 0; column < GameState.WIDTH; column++) {
                if (state.fixed[row][column] != null) grid[row][column] = state.fixed[row][column].value();
            }
        }
        return grid;
    }

    private GameUpdate update(LockResolution lock, GameEnd end) {
        return new GameUpdate(snapshot(), Optional.ofNullable(lock), Optional.ofNullable(end), nextWakeup());
    }

    private OptionalLong nextWakeup() {
        long wakeup = Long.MAX_VALUE;
        if (!state.highlightedRows.isEmpty()) {
            wakeup = Math.min(HIGHLIGHT_REPAINT_NANOS,
                Math.max(1, CLEAR_HIGHLIGHT_NANOS - state.highlightElapsedNanos));
        }
        if (state.phase == GamePhase.ACTIVE && !state.paused) {
            wakeup = Math.min(wakeup, Math.max(1, dropIntervalNanos() - state.gravityElapsedNanos));
        }
        return wakeup == Long.MAX_VALUE ? OptionalLong.empty() : OptionalLong.of(wakeup);
    }

    private long dropIntervalNanos() {
        return config.difficulty().dropIntervalMillis(state.level) * 1_000_000L;
    }

    private static Block requireFresh(Block block) {
        return Objects.requireNonNull(block, "BlockSource returned null");
    }

    private static GameSnapshot.Piece piece(Block block) {
        ArrayList<List<GameSnapshot.Cell>> rows = new ArrayList<>(block.height());
        for (int row = 0; row < block.height(); row++) {
            ArrayList<GameSnapshot.Cell> cells = new ArrayList<>(block.width());
            for (int column = 0; column < block.width(); column++) {
                String marker = block.getText(column, row);
                cells.add(block.getShape(column, row) == 0 ? null : new GameSnapshot.Cell(
                    block.getShape(column, row), marker, displayArgb(marker, block.getColor().getRGB())));
            }
            rows.add(cells);
        }
        String name = block.getClass().getSimpleName();
        return new GameSnapshot.Piece(name.endsWith("Block")
            ? name.substring(0, name.length() - "Block".length()) : name, rows);
    }

    private static GameSnapshot.Cell toSnapshotCell(GameState.FixedCell cell) {
        return cell == null ? null : new GameSnapshot.Cell(cell.value(), cell.text(), cell.argb());
    }

    private static int displayArgb(String marker, int blockArgb) {
        return marker.equals("O") || marker.equals("P") ? blockArgb : 0xffffffff;
    }

    private static List<Integer> bitSetValues(BitSet bits) {
        ArrayList<Integer> result = new ArrayList<>(bits.cardinality());
        for (int value = bits.nextSetBit(0); value >= 0; value = bits.nextSetBit(value + 1)) {
            result.add(value);
        }
        return result;
    }
}
