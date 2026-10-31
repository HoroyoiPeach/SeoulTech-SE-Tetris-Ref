package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import javax.swing.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.blocks.ColumnBlock;
import seoultech.se.tetris.blocks.CrossBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.RowBlock;
import sun.misc.Unsafe;

/** Behavior lock for the boundaries that the game-engine extraction will replace. */
class RefactorBaselineCharacterizationTest {
    private static final String DATA_DIR_PROPERTY = "tetris.dataDir";

    @TempDir
    Path dataDir;

    private String previousDataDir;
    private int previousRight;

    @BeforeEach
    void isolateGlobalState() {
        previousDataDir = System.getProperty(DATA_DIR_PROPERTY);
        previousRight = Maincontainer.RIGHT;
        System.setProperty(DATA_DIR_PROPERTY, dataDir.toString());
    }

    @AfterEach
    void restoreGlobalState() {
        Maincontainer.RIGHT = previousRight;
        if (previousDataDir == null) {
            System.clearProperty(DATA_DIR_PROPERTY);
        } else {
            System.setProperty(DATA_DIR_PROPERTY, previousDataDir);
        }
    }

    @Test
    @Timeout(3)
    @DisplayName("일반 줄 삭제 강조는 pause 중에도 끝나고 블록과 중력은 멈춤")
    void normalClearHighlightExpiresWhilePausedWithoutMovingActiveBlock() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ExistingFeatureFixture fixture =
                    new ExistingFeatureFixture(Difficulty.NORMAL, GameMode.CLASSIC)) {
                int[][] grid = new int[20][10];
                grid[18][2] = 1;
                Arrays.fill(grid[19], 1);
                fixture.prepare(new OBlock(), new OBlock(), grid, 3, 0);
                fixture.board.timer.setDelay(10_000);
                fixture.board.timer.start();

                fixture.board.eraseLine();
                assertTrue(fixture.board.timer.isRunning(),
                    "pause 직전에는 중력 타이머가 실제로 실행 중이어야 합니다.");
                fixture.board.togglePause();
                int pausedX = fixture.board.x;
                int pausedY = fixture.board.y;
                Timer highlight = get(fixture.board, "clearAnimationTimer", Timer.class);
                assertNotNull(highlight);
                assertFalse(get(fixture.board, "clearingRows", BitSet.class).isEmpty());

                set(fixture.board, "clearAnimationStarted", System.nanoTime() - 211_000_000L);
                fire(highlight);

                assertTrue(fixture.board.getIsPaused());
                assertFalse(fixture.board.timer.isRunning());
                assertEquals(pausedX, fixture.board.x);
                assertEquals(pausedY, fixture.board.y);
                assertTrue(get(fixture.board, "clearingRows", BitSet.class).isEmpty());
                assertNull(get(fixture.board, "clearAnimationTimer", Timer.class));
            }
            return null;
        });
    }

    @Test
    @Timeout(3)
    @DisplayName("ESC는 배정된 게임 키를 우선하고 미배정일 때만 메뉴로 종료")
    void escapeKeyUsesConfiguredActionBeforeMenuExit() throws Exception {
        InputFixture fixture = ExistingFeatureFixture.onEdt(InputFixture::new);
        try (fixture) {
            ExistingFeatureFixture.onEdt(() -> {
                fixture.prepare(new OBlock(), new OBlock(), new int[20][10], 3, 0);
                Maincontainer.RIGHT = KeyEvent.VK_ESCAPE;

                press(fixture.game, KeyEvent.VK_ESCAPE);

                assertEquals(4, fixture.board.x);
                assertFalse(fixture.owner.exitCalled);
                assertNotNull(fixture.board.timer);

                Maincontainer.RIGHT = KeyEvent.VK_RIGHT;
                press(fixture.game, KeyEvent.VK_ESCAPE);

                assertTrue(fixture.owner.exitCalled);
                assertNull(fixture.board.timer);
                return null;
            });
        }
    }

    @ParameterizedTest(name = "{0} completion boundary")
    @EnumSource(AnimatedItem.class)
    @Timeout(3)
    @DisplayName("아이템은 마지막 표시 프레임 뒤에만 삭제·점수·next를 한 번 확정")
    void animatedItemCommitsOnlyAfterCompletionTick(AnimatedItem item) throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ExistingFeatureFixture fixture = new ExistingFeatureFixture(Difficulty.NORMAL)) {
                Block active = item.createBlock();
                Block queued = new OBlock();
                int landingY = 20 - active.height();
                Sentinel sentinel = item.sentinel(active, landingY);
                int[][] grid = new int[20][10];
                grid[sentinel.row][sentinel.column] = 1;
                fixture.prepare(active, queued, grid, 3, landingY);
                assertEquals(1, get(fixture.board, "board", int[][].class)
                    [sentinel.row][sentinel.column]);
                AnimationDriver driver = new AnimationDriver(fixture.board, active, queued,
                    item.frames, sentinel);
                Timer trigger = new Timer(0, event -> driver.completeAnimation());
                trigger.setRepeats(false);
                trigger.start();

                fixture.board.hardDrop();

                assertNull(driver.failure, "마지막 프레임 전 검증에 실패하면 안 됩니다.");
                assertNotNull(driver.animation);
                assertNotNull(driver.listener);
                assertSame(queued, get(fixture.board, "curr", Block.class));
                assertEquals(item.expectedScore, fixture.board.getScore());
                assertEquals(item.expectedClearedLines, intField(fixture.board, "erasedLineNum"));
                assertEquals(0, get(fixture.board, "board", int[][].class)
                    [sentinel.row][sentinel.column], "완료 tick에서 실제 아이템 효과가 적용되어야 합니다.");
                Block nextAfterCompletion = get(fixture.board, "next", Block.class);
                assertFalse(nextAfterCompletion == queued);

                driver.fireLateCompletion();

                assertSame(queued, get(fixture.board, "curr", Block.class));
                assertSame(nextAfterCompletion, get(fixture.board, "next", Block.class));
                assertEquals(item.expectedScore, fixture.board.getScore());
                assertEquals(item.expectedClearedLines, intField(fixture.board, "erasedLineNum"));
            }
            return null;
        });
    }

    private static void press(Gamepanel game, int keyCode) {
        KeyEvent event = new KeyEvent(game, KeyEvent.KEY_PRESSED, 0L, 0, keyCode,
            KeyEvent.CHAR_UNDEFINED);
        Arrays.stream(game.getKeyListeners()).forEach(listener -> listener.keyPressed(event));
    }

    private static void fire(Timer timer) {
        ActionEvent event = new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, "test-tick");
        for (ActionListener listener : timer.getActionListeners()) listener.actionPerformed(event);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T get(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private enum AnimatedItem {
        ROW(5, 100, 1) {
            @Override Block createBlock() { return new RowBlock(); }

            @Override Sentinel sentinel(Block active, int landingY) {
                return new Sentinel(landingY + ((RowBlock) active).getItemY(), 0);
            }
        },
        COLUMN(10, 0, 0) {
            @Override Block createBlock() { return new ColumnBlock(); }

            @Override Sentinel sentinel(Block active, int landingY) {
                return new Sentinel(8, 3 + ((ColumnBlock) active).getItemX());
            }
        },
        CROSS(10, 300, 2) {
            @Override Block createBlock() { return new CrossBlock(); }

            @Override Sentinel sentinel(Block active, int landingY) {
                return new Sentinel(landingY, 0);
            }
        };

        final int frames;
        final int expectedScore;
        final int expectedClearedLines;

        AnimatedItem(int frames, int expectedScore, int expectedClearedLines) {
            this.frames = frames;
            this.expectedScore = expectedScore;
            this.expectedClearedLines = expectedClearedLines;
        }

        abstract Block createBlock();

        abstract Sentinel sentinel(Block active, int landingY);
    }

    private record Sentinel(int row, int column) {}

    private static final class AnimationDriver {
        private final Board board;
        private final Block active;
        private final Block queued;
        private final int frames;
        private final Sentinel sentinel;
        Timer animation;
        ActionListener listener;
        Throwable failure;

        AnimationDriver(Board board, Block active, Block queued, int frames, Sentinel sentinel) {
            this.board = board;
            this.active = active;
            this.queued = queued;
            this.frames = frames;
            this.sentinel = sentinel;
        }

        void completeAnimation() {
            try {
                animation = get(board, "itemAnimationTimer", Timer.class);
                assertNotNull(animation);
                int[][] gridBeforeFrames = copyGrid(board);
                animation.stop();
                ActionListener[] listeners = animation.getActionListeners();
                assertEquals(1, listeners.length);
                listener = listeners[0];
                ActionEvent tick = new ActionEvent(animation, ActionEvent.ACTION_PERFORMED, "test-tick");
                for (int frame = 0; frame < frames; frame++) listener.actionPerformed(tick);

                assertSame(active, get(board, "curr", Block.class));
                assertSame(queued, get(board, "next", Block.class));
                assertEquals(0, board.getScore());
                assertEquals(0, intField(board, "erasedLineNum"));
                int[][] gridAfterFrames = get(board, "board", int[][].class);
                assertEquals(1, gridAfterFrames[sentinel.row][sentinel.column],
                    "마지막 표시 프레임만으로 실제 아이템 효과가 적용되면 안 됩니다.");
                for (int row = 0; row < gridBeforeFrames.length; row++) {
                    assertArrayEquals(gridBeforeFrames[row], gridAfterFrames[row],
                        "표시 프레임만 진행한 동안 격자는 바뀌면 안 됩니다: row=" + row);
                }

                listener.actionPerformed(tick);
            } catch (Throwable thrown) {
                failure = thrown;
                board.stopAnimations();
            }
        }

        void fireLateCompletion() {
            listener.actionPerformed(new ActionEvent(animation, ActionEvent.ACTION_PERFORMED,
                "late-test-tick"));
        }
    }

    private static int[][] copyGrid(Board board) throws Exception {
        return Arrays.stream(get(board, "board", int[][].class))
            .map(int[]::clone)
            .toArray(int[][]::new);
    }

    private static final class InputFixture implements AutoCloseable {
        final TestMaincontainer owner;
        final Gamepanel game;
        final Board board;
        final Timer initialTimer;

        InputFixture() throws Exception {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            owner = (TestMaincontainer) ((Unsafe) unsafeField.get(null))
                .allocateInstance(TestMaincontainer.class);
            owner.setting = owner.new Setting();
            game = new Gamepanel(owner, GameMode.CLASSIC);
            board = get(game, "board", Board.class);
            initialTimer = board.timer;
            initialTimer.stop();
        }

        void prepare(Block current, Block next, int[][] grid, int x, int y) throws Exception {
            board.stopAnimations();
            board.timer.stop();
            set(board, "board", grid);
            set(board, "curr", current);
            set(board, "next", next);
            set(board, "score", 0);
            set(board, "erasedLineNum", 0);
            set(board, "level", 1);
            set(board, "isPaused", false);
            set(board, "anchorTouched", false);
            board.x = x;
            board.y = y;
            board.setText("");
            board.setText("XXXXXXXXXXXX\n" + "X          X\n".repeat(20) + "XXXXXXXXXXXX");
            ExistingFeatureFixture.invoke(board, "placeBlock");
        }

        @Override
        public void close() {
            board.stopAnimations();
            initialTimer.stop();
            if (board.timer != null) board.timer.stop();
        }
    }

    private static final class TestMaincontainer extends Maincontainer {
        private static final long serialVersionUID = 1L;
        boolean exitCalled;

        @Override
        protected void exitGameEnterStart() {
            exitCalled = true;
        }
    }
}
