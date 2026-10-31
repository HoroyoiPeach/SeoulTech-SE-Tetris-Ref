package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.Block;
import sun.misc.Unsafe;

class GameControllerLifecycleTest {
    @Test
    @Timeout(3)
    void constructionDoesNotStartAndStartIsIdempotent() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            GameFixture fixture = new GameFixture();
            try (fixture) {
                assertNull(field(fixture.board, "curr", Block.class));
                assertNull(field(fixture.board, "next", Block.class));
                assertFalse(fixture.initialTimer.isRunning());

                fixture.game.startGame();
                Block current = field(fixture.board, "curr", Block.class);
                Block next = field(fixture.board, "next", Block.class);
                assertTrue(fixture.initialTimer.isRunning());

                fixture.game.startGame();
                assertSame(current, field(fixture.board, "curr", Block.class));
                assertSame(next, field(fixture.board, "next", Block.class));
                assertSame(fixture.initialTimer, fixture.board.timer);
            }
            return null;
        });
    }

    @Test
    @Timeout(3)
    void stopIsIdempotentAndRejectsLateInputOrRestart() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            GameFixture fixture = new GameFixture();
            try (fixture) {
                fixture.game.startGame();
                int x = fixture.board.x;
                Block current = field(fixture.board, "curr", Block.class);
                fixture.game.stopGame();
                fixture.game.stopGame();

                assertNull(fixture.board.timer);
                assertFalse(fixture.initialTimer.isRunning());
                fixture.controller.handleKey(Maincontainer.RIGHT);
                fixture.game.startGame();

                assertSame(current, field(fixture.board, "curr", Block.class));
                assertEquals(x, fixture.board.x);
                assertNull(fixture.board.timer);
                assertFalse(fixture.owner.exitCalled);
            }
            return null;
        });
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static final class GameFixture implements AutoCloseable {
        final TestMaincontainer owner;
        final Gamepanel game;
        final Board board;
        final GameController controller;
        final Timer initialTimer;

        GameFixture() throws Exception {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            owner = (TestMaincontainer) ((Unsafe) unsafeField.get(null))
                .allocateInstance(TestMaincontainer.class);
            owner.setting = owner.new Setting();
            game = new Gamepanel(owner, GameMode.CLASSIC);
            board = field(game, "board", Board.class);
            controller = field(game, "controller", GameController.class);
            initialTimer = board.timer;
        }

        @Override
        public void close() {
            game.stopGame();
            initialTimer.stop();
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
