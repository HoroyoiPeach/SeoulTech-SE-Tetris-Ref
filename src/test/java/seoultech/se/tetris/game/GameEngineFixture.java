package seoultech.se.tetris.game;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.Block;

public final class GameEngineFixture {
    public final QueueBlockSource source;
    public final GameEngine engine;

    public GameEngineFixture(Block... blocks) {
        source = new QueueBlockSource(blocks);
        engine = new GameEngine(new GameConfig(GameMode.CLASSIC, Difficulty.NORMAL), source);
    }

    public void seed(Block current, Block next, int x, int y) {
        GameState state = engine.state;
        for (GameState.FixedCell[] row : state.fixed) Arrays.fill(row, null);
        state.current = current;
        state.next = next;
        state.x = x;
        state.y = y;
        state.score = 0;
        state.clearedLines = 0;
        state.level = 1;
        state.paused = false;
        state.phase = GamePhase.ACTIVE;
        state.pendingLock = null;
        state.highlightedRows.clear();
        state.gravityElapsedNanos = 0;
        state.highlightElapsedNanos = 0;
    }

    public void fixed(int x, int y) {
        engine.state.fixed[y][x] = new GameState.FixedCell(1, "O", 0xff808080, 0);
    }

    public static final class QueueBlockSource implements BlockSource {
        private final Queue<Block> queue = new ArrayDeque<>();
        public int normalCalls;
        public int itemCalls;

        public QueueBlockSource(Block... blocks) {
            queue.addAll(Arrays.asList(blocks));
        }

        @Override
        public Block nextBlock(Difficulty difficulty) {
            normalCalls++;
            Block block = queue.poll();
            if (block == null) throw new AssertionError("No scripted normal block remains");
            return block;
        }

        @Override
        public Block nextItemBlock() {
            itemCalls++;
            Block block = queue.poll();
            if (block == null) throw new AssertionError("No scripted item block remains");
            return block;
        }
    }
}
