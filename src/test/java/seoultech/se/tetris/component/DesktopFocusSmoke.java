package seoultech.se.tetris.component;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import javax.swing.JTextPane;
import seoultech.se.tetris.blocks.TBlock;

import static seoultech.se.tetris.component.ExistingFeatureFixture.get;
import static seoultech.se.tetris.component.ExistingFeatureFixture.invoke;
import static seoultech.se.tetris.component.ExistingFeatureFixture.onEdt;
import static seoultech.se.tetris.component.ExistingFeatureFixture.set;

/** Native input regression; run separately from headless CI in an active desktop session. */
public final class DesktopFocusSmoke {
    private static void await(Robot robot, Callable<Boolean> condition, String message) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        do {
            robot.waitForIdle();
            if (onEdt(condition)) return;
            robot.delay(50);
        } while (System.nanoTime() < deadline);
        String focus = onEdt(() -> {
            Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            return owner == null ? "none" : owner.getClass().getName();
        });
        throw new AssertionError(message + "; focus owner=" + focus);
    }

    private static void key(Robot robot, int code) {
        robot.keyPress(code);
        robot.keyRelease(code);
        robot.waitForIdle();
    }

    private static void click(Robot robot, Component component) throws Exception {
        Point center = onEdt(() -> {
            Point point = component.getLocationOnScreen();
            point.translate(component.getWidth() / 2, component.getHeight() / 2);
            return point;
        });
        robot.mouseMove(center.x, center.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }

    private static void checkMode(Robot robot, Maincontainer frame, String mode) throws Exception {
        Gamepanel game = onEdt(() -> {
            frame.exitStartEnterGame(mode);
            Gamepanel panel = get(frame, "gamePanel", Gamepanel.class);
            Board board = get(panel, "board", Board.class);
            board.timer.stop();
            invoke(board, "eraseCurr");
            set(board, "curr", new TBlock());
            board.x = 4;
            board.y = 4;
            invoke(board, "placeBlock");
            board.drawBoard();
            frame.toFront();
            panel.requestFocusInWindow();
            return panel;
        });
        Board board = onEdt(() -> get(game, "board", Board.class));
        JTextPane score = onEdt(() -> get(game, "scorepanel", JTextPane.class));
        int[] scoreClicks = {0};
        onEdt(() -> {
            score.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) { scoreClicks[0]++; }
            });
            return null;
        });
        // Activate the native window before testing. No focus repair occurs after score clicks.
        click(robot, game);
        onEdt(() -> game.requestFocusInWindow());
        await(robot, game::isFocusOwner, mode + ": game must initially own keyboard focus");
        key(robot, Maincontainer.DOWN);
        await(robot, () -> board.y == 5, mode + ": native keyboard must work before clicking score");

        click(robot, score);
        await(robot, () -> scoreClicks[0] == 1, mode + ": native click must reach the score pane");
        await(robot, game::isFocusOwner, mode + ": score click must preserve game focus");
        key(robot, Maincontainer.LEFT);
        await(robot, () -> board.x == 3, mode + ": LEFT must still move after clicking score");

        key(robot, Maincontainer.PAUSE);
        await(robot, board::getIsPaused, mode + ": pause must still work after clicking score");
        click(robot, score);
        await(robot, () -> scoreClicks[0] == 2, mode + ": paused score pane must receive the second native click");
        await(robot, game::isFocusOwner, mode + ": paused score click must preserve game focus");
        key(robot, Maincontainer.PAUSE);
        await(robot, () -> !board.getIsPaused(), mode + ": resume must work after clicking score while paused");
        onEdt(() -> {
            board.timer.stop();
            board.stopAnimations();
            frame.exitGameEnterStart();
            return null;
        });
        System.out.println("PASS " + mode + ": native score clicks preserve game focus, movement and pause/resume");
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("tetris.dataDir", Files.createTempDirectory("tetris-focus-data-").toString());
        Path result = Path.of("build/reports/desktop-focus/result.txt");
        Files.createDirectories(result.getParent());
        Files.deleteIfExists(result);
        Robot robot = new Robot();
        robot.setAutoDelay(60);
        Maincontainer frame = onEdt(() -> {
            Maincontainer created = new Maincontainer();
            created.setLocationRelativeTo(null);
            created.setAlwaysOnTop(true);
            created.enterStart();
            return created;
        });
        try {
            checkMode(robot, frame, "Classic");
            checkMode(robot, frame, "Item");
            String report = "PASS Classic and Item: native AWT Robot mouse clicks and keyboard input; "
                + "game focus, movement and pause/resume after score clicks.\n"
                + "OS=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " Java=" + System.getProperty("java.version") + "\n";
            Files.writeString(result, report);
            System.out.print(report);
        } finally {
            onEdt(() -> {
                Gamepanel game = get(frame, "gamePanel", Gamepanel.class);
                if (game != null) {
                    Board board = get(game, "board", Board.class);
                    if (board.timer != null) board.timer.stop();
                    board.stopAnimations();
                }
                frame.dispose();
                return null;
            });
        }
    }
}
