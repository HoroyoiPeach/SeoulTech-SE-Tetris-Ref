package seoultech.se.tetris.component;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.BattleMatchSnapshot;
import seoultech.se.tetris.score.ScoreStore;

/**
 * Real-window acceptance path for local battle using only synthetic Swing input.
 * It never takes control of the user's mouse or keyboard.
 */
public final class LocalBattleDesktopSmoke {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void key(Component component, int code) {
        KeyEvent event = new KeyEvent(component, KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
        Arrays.stream(component.getKeyListeners()).forEach(listener -> listener.keyPressed(event));
    }

    private static <T extends Component> T find(Component root, Class<T> type,
            Predicate<T> predicate) {
        if (type.isInstance(root)) {
            T candidate = type.cast(root);
            if (predicate.test(candidate)) return candidate;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type, predicate);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Component named(Component root, String name) {
        return find(root, Component.class, component -> name.equals(component.getName()));
    }

    static JButton button(Component root, String text) {
        return find(root, JButton.class, candidate -> text.equals(candidate.getText()));
    }

    static <T> T onEdt(Callable<T> task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return task.call();
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                value.set(task.call());
            } catch (Throwable caught) {
                failure.set(caught);
            }
        });
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() != null) throw new RuntimeException(failure.get());
        return value.get();
    }

    static void capture(Maincontainer frame, Path file) throws Exception {
        frame.validate();
        BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            frame.paint(graphics);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", file.toFile());
    }

    private static LocalBattlePanel enter(Maincontainer frame, GameMode mode) throws Exception {
        return onEdt(() -> {
            Startpanel start = find(frame.getContentPane(), Startpanel.class,
                candidate -> candidate.isShowing());
            check(start != null, "the actual application must show its start menu");
            key(start, KeyEvent.VK_DOWN);
            key(start, KeyEvent.VK_DOWN);
            key(start, KeyEvent.VK_ENTER);

            check(button(frame.getContentPane(), "CLASSIC") != null,
                "local battle mode selection must offer Classic");
            check(button(frame.getContentPane(), "ITEM") != null,
                "local battle mode selection must offer Item");
            check(button(frame.getContentPane(), "TIME LIMIT") != null,
                "local battle mode selection must offer Time Limit");
            check(button(frame.getContentPane(), "BACK") != null,
                "local battle mode selection must offer a return path");

            button(frame.getContentPane(), mode.displayName().toUpperCase()).doClick();
            Component selected = named(frame.getContentPane(), "localBattlePanel");
            check(selected instanceof LocalBattlePanel && selected.isShowing(),
                mode + " selection must show the local battle screen");
            LocalBattlePanel battle = (LocalBattlePanel) selected;
            check(battle.isRunning(),
                mode + " local battle must start through the normal menu entry point");
            check(battle.snapshot().first().mode() == mode
                    && battle.snapshot().second().mode() == mode,
                mode + " selection must configure both engines with the selected mode");
            return battle;
        });
    }

    static void verifyDisplay(Maincontainer frame, String size, String palette) throws Exception {
        LocalBattlePanel battle = enter(frame, GameMode.CLASSIC);
        frame.validate();
        Board first = (Board) named(battle, "player1Board");
        Board second = (Board) named(battle, "player2Board");
        String label = size + "/" + palette;
        check(first != null && second != null && first.isShowing() && second.isShowing()
                && first.getWidth() > 0 && first.getHeight() > 0
                && first.getSize().equals(second.getSize()),
            label + " must show two equally sized usable boards");
        java.awt.Rectangle firstBounds = SwingUtilities.convertRectangle(
            first.getParent(), first.getBounds(), battle);
        java.awt.Rectangle secondBounds = SwingUtilities.convertRectangle(
            second.getParent(), second.getBounds(), battle);
        check(!firstBounds.intersects(secondBounds)
                && new java.awt.Rectangle(battle.getSize()).contains(firstBounds)
                && new java.awt.Rectangle(battle.getSize()).contains(secondBounds),
            label + " boards must not overlap or leave the configured canvas");
        check(usesCurrentPieceColor(first) && usesCurrentPieceColor(second),
            label + " must paint both active pieces with the selected palette");

        button(battle, "MENU").doClick();
        check(!battle.isRunning(), label + " display check must stop the match on exit");
    }

    private static boolean usesCurrentPieceColor(Board board) {
        var sample = board.snapshot().current().cells().stream()
            .flatMap(java.util.List::stream)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElseThrow();
        int expected = Maincontainer.changeColor(new java.awt.Color(sample.argb(), true)).getRGB();
        BufferedImage image = new BufferedImage(board.getWidth(), board.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            board.paint(graphics);
        } finally {
            graphics.dispose();
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == expected) return true;
            }
        }
        return false;
    }

    private static void verifyStructureAndIndependentInput(LocalBattlePanel battle,
            GameMode mode) throws Exception {
        onEdt(() -> {
            Board player1 = (Board) named(battle, "player1Board");
            Board player2 = (Board) named(battle, "player2Board");
            check(player1 != null && player2 != null,
                mode + " battle must render both player boards");
            check(player1.snapshot() != null && player2.snapshot() != null,
                mode + " battle must publish both immutable snapshots");
            check(named(battle, "player1Score") != null
                    && named(battle, "player2Score") != null,
                mode + " battle must show both scores");
            check(named(battle, "player1Incoming") != null
                    && named(battle, "player2Incoming") != null,
                mode + " battle must show both incoming-attack previews");

            int player1X = player1.snapshot().x();
            int player2X = player2.snapshot().x();
            key(battle, Maincontainer.LEFT);
            check(player1.snapshot().x() == player1X - 1,
                mode + " configured Player 1 key must move only Player 1");
            check(player2.snapshot().x() == player2X,
                mode + " configured Player 1 key must leave Player 2 unchanged");

            key(battle, KeyEvent.VK_A);
            check(player2.snapshot().x() == player2X - 1,
                mode + " displayed Player 2 key must move only Player 2");
            check(player1.snapshot().x() == player1X - 1,
                mode + " displayed Player 2 key must leave Player 1 unchanged");
            return null;
        });
    }

    private static void finishWithSyntheticHardDrops(LocalBattlePanel battle, GameMode mode)
            throws Exception {
        for (int attempt = 0; attempt < 2_000; attempt++) {
            boolean finished = onEdt(() -> battle.snapshot().decision().isPresent());
            if (finished) return;
            onEdt(() -> {
                key(battle, Maincontainer.HARD_DROP);
                return null;
            });
            Thread.sleep(5);
        }
        throw new AssertionError(mode + " must reach a normal battle result from repeated hard drops");
    }

    private static void verifyResultRematchAndExit(Maincontainer frame, LocalBattlePanel battle,
            GameMode mode, Path evidence, List<ScoreStore.Score> baselineScores) throws Exception {
        onEdt(() -> {
            BattleMatchSnapshot result = battle.snapshot();
            check(result.decision().isPresent() && result.stopped() && !battle.isRunning(),
                mode + " must finalize exactly one shared match result");
            JLabel resultLabel = (JLabel) named(battle, "matchResult");
            check(resultLabel != null && !resultLabel.getText().isBlank(),
                mode + " result must be visible to both players");
            JButton newMatch = (JButton) named(battle, "newMatchButton");
            check(newMatch != null && newMatch.isEnabled(),
                mode + " result must enable a rematch");
            check(ScoreStore.readRanked().equals(baselineScores),
                mode + " local battle result must preserve the existing single-player scoreboard");
            capture(frame, evidence.resolve(mode.name().toLowerCase() + ".png"));

            newMatch.doClick();
            BattleMatchSnapshot restarted = battle.snapshot();
            check(battle.isRunning() && restarted.decision().isEmpty(),
                mode + " rematch must start a fresh running match");
            check(restarted.first().score() == 0 && restarted.second().score() == 0,
                mode + " rematch must reset both scores");
            check(restarted.firstPendingAttackRows().isEmpty()
                    && restarted.secondPendingAttackRows().isEmpty(),
                mode + " rematch must reset both attack queues");

            JButton menu = (JButton) named(battle, "menuButton");
            check(menu != null, mode + " local battle must have an in-game menu exit");
            menu.doClick();
            check(find(frame.getContentPane(), Startpanel.class,
                    candidate -> candidate.isShowing()) != null,
                mode + " menu exit must restore the start menu");
            check(!battle.isRunning(), mode + " menu exit must stop both game loops");
            check(ScoreStore.readRanked().equals(baselineScores),
                mode + " rematch and exit must leave the existing single-player scoreboard untouched");
            return null;
        });
    }

    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("tetris-local-battle-data-");
        Path evidence = Path.of("build/reports/desktop/local-battle");
        Files.createDirectories(evidence);
        System.setProperty("tetris.dataDir", data.toString());
        ScoreStore.clear();
        ScoreStore.append("BASELINE", 777, Difficulty.HARD, GameMode.ITEM);
        List<ScoreStore.Score> baselineScores = List.copyOf(ScoreStore.readRanked());
        check(baselineScores.size() == 1,
            "the smoke fixture must start with one existing single-player score");
        Maincontainer frame = onEdt(() -> {
            Maincontainer created = new Maincontainer();
            created.enterStart();
            return created;
        });
        try {
            for (GameMode mode : GameMode.values()) {
                LocalBattlePanel battle = enter(frame, mode);
                verifyStructureAndIndependentInput(battle, mode);
                finishWithSyntheticHardDrops(battle, mode);
                verifyResultRematchAndExit(frame, battle, mode, evidence, baselineScores);
            }
        } finally {
            onEdt(() -> {
                frame.dispose();
                return null;
            });
        }
        System.out.println("PASS all local battle modes, dual boards, independent controls, "
            + "normal results, rematches, score isolation and clean exits");
    }
}
