package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.attack.AttackRow;
import seoultech.se.tetris.game.MatchOutcome;

class LocalBattlePanelTest {
    @Test
    void remappedEnterEscapeAndTabRemainGameplayKeysBeforeUiShortcuts() throws Exception {
        int[] original = currentKeys();
        try {
            Maincontainer.ROTATE = KeyEvent.VK_TAB;
            Maincontainer.DOWN = KeyEvent.VK_DOWN;
            Maincontainer.LEFT = KeyEvent.VK_ESCAPE;
            Maincontainer.RIGHT = KeyEvent.VK_RIGHT;
            Maincontainer.PAUSE = KeyEvent.VK_P;
            Maincontainer.HARD_DROP = KeyEvent.VK_ENTER;
            AtomicBoolean menu = new AtomicBoolean();

            ExistingFeatureFixture.onEdt(() -> {
                LocalBattlePanel panel = new LocalBattlePanel(Difficulty.NORMAL, 180,
                    GameMode.CLASSIC, () -> menu.set(true), System::nanoTime);
                panel.startMatch();
                int initialX = panel.snapshot().first().x();

                panel.dispatchKey(KeyEvent.VK_ESCAPE);
                assertEquals(initialX - 1, panel.snapshot().first().x());
                assertFalse(menu.get(), "configured Escape must move before it can leave the match");

                panel.dispatchKey(KeyEvent.VK_TAB);
                assertTrue(panel.isRunning(), "configured Tab must remain a gameplay key");
                panel.dispatchKey(KeyEvent.VK_ENTER);
                assertTrue(panel.snapshot().first().score() > 0,
                    "configured Enter must hard-drop instead of restarting the live match");
                panel.dispatchKey(Maincontainer.PAUSE);
                panel.dispatchKey(KeyEvent.VK_ENTER);
                assertTrue(menu.get(), "paused players must be able to leave with the keyboard even when Escape and Enter are rebound");
                assertFalse(panel.isRunning());
                return null;
            });
        } finally {
            restoreKeys(original);
        }
    }

    @Test
    void smallestWindowKeepsBoardsScoresAndAllTenIncomingRowsVisible() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            LocalBattlePanel panel = new LocalBattlePanel(Difficulty.NORMAL, 180,
                GameMode.CLASSIC, () -> {}, System::nanoTime);
            panel.setSize(500, 600);
            layoutTree(panel);

            assertVisible(panel, "player1Board");
            assertVisible(panel, "player2Board");
            assertVisible(panel, "player1Score");
            assertVisible(panel, "player2Score");

            JTextArea rows = (JTextArea) findNamed(panel, "player1IncomingRows");
            assertNotNull(rows);
            IncomingAttackPanel incoming = (IncomingAttackPanel) rows.getParent();
            incoming.showRows(java.util.stream.IntStream.range(0, 10)
                .mapToObj(row -> new AttackRow(new boolean[] {
                    true, true, true, true, false, true, true, true, true, true
                })).toList());
            layoutTree(panel);
            FontMetrics metrics = rows.getFontMetrics(rows.getFont());
            assertTrue(metrics.getHeight() * 10 <= rows.getHeight(),
                "the default 500x600 layout must have room for all ten attack rows");
            assertTrue(incoming.title().getPreferredSize().width <= incoming.getWidth(),
                "the incoming count must remain readable at the minimum width");
            return null;
        });
    }

    @Test
    void detachingTheBattleStopsItsTimerAndBothEngines() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            LocalBattlePanel panel = new LocalBattlePanel(Difficulty.NORMAL, 180,
                GameMode.ITEM, () -> {}, System::nanoTime);
            panel.startMatch();
            assertTrue(panel.isRunning());
            panel.removeNotify();
            assertFalse(panel.isRunning());
            assertTrue(panel.snapshot().stopped());
            return null;
        });
    }

    @Test
    void timeLimitUsesThePanelsClockAndNewMatchGetsAFreshDeadline() throws Exception {
        AtomicLong clock = new AtomicLong();
        ExistingFeatureFixture.onEdt(() -> {
            LocalBattlePanel panel = new LocalBattlePanel(Difficulty.NORMAL, 1,
                GameMode.TIME_LIMIT, () -> {}, clock::get);
            panel.startMatch();
            assertEquals(1, panel.snapshot().remainingSeconds());

            clock.set(1_000_000_000L);
            panel.dispatchKey(KeyEvent.VK_F12);
            assertFalse(panel.isRunning());
            assertEquals(MatchOutcome.DRAW,
                panel.snapshot().decision().orElseThrow().outcome());
            JLabel result = (JLabel) findNamed(panel, "matchResult");
            assertNotNull(result);
            assertTrue(result.getText().startsWith("DRAW"));

            JButton newMatch = (JButton) findNamed(panel, "newMatchButton");
            assertNotNull(newMatch);
            newMatch.doClick();
            assertTrue(panel.isRunning());
            assertTrue(panel.snapshot().decision().isEmpty());
            assertEquals(1, panel.snapshot().remainingSeconds(),
                "the rematch deadline must start from the current monotonic time");
            panel.stopMatch();
            return null;
        });
    }

    @Test
    void deadlineDuringPauseShowsAFinalResultWithoutAPausePrompt() throws Exception {
        AtomicLong clock = new AtomicLong();
        ExistingFeatureFixture.onEdt(() -> {
            LocalBattlePanel panel = new LocalBattlePanel(Difficulty.NORMAL, 1,
                GameMode.TIME_LIMIT, () -> {}, clock::get);
            panel.startMatch();
            panel.dispatchKey(Maincontainer.PAUSE);
            JLabel time = (JLabel) findNamed(panel, "matchTime");
            assertTrue(time.getText().contains("PAUSED"));
            clock.set(1_000_000_000L);
            panel.dispatchKey(Maincontainer.PAUSE);
            assertEquals(MatchOutcome.DRAW, panel.snapshot().decision().orElseThrow().outcome());
            assertEquals("TIME 00:00", time.getText());
            return null;
        });
    }

    @Test
    void repeatedAlternatingInputAlwaysStaysOnItsOwnBoard() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            LocalBattlePanel panel = new LocalBattlePanel(Difficulty.NORMAL, 180,
                GameMode.CLASSIC, () -> {}, System::nanoTime);
            panel.startMatch();
            int firstX = panel.snapshot().first().x();
            int secondX = panel.snapshot().second().x();
            for (int repeat = 0; repeat < 20; repeat++) {
                panel.dispatchKey(Maincontainer.LEFT);
                panel.dispatchKey(Maincontainer.RIGHT);
            }
            assertEquals(firstX, panel.snapshot().first().x());
            assertEquals(secondX, panel.snapshot().second().x());

            LocalBattleKeyBindings bindings = LocalBattleKeyBindings.configured();
            int secondLeft = keyFor(bindings, seoultech.se.tetris.game.GameCommand.LEFT);
            int secondRight = keyFor(bindings, seoultech.se.tetris.game.GameCommand.RIGHT);
            for (int repeat = 0; repeat < 20; repeat++) {
                panel.dispatchKey(secondLeft);
                panel.dispatchKey(secondRight);
            }
            assertEquals(firstX, panel.snapshot().first().x());
            assertEquals(secondX, panel.snapshot().second().x());
            panel.stopMatch();
            return null;
        });
    }

    private static int[] currentKeys() {
        return new int[] {Maincontainer.ROTATE, Maincontainer.DOWN, Maincontainer.LEFT,
            Maincontainer.RIGHT, Maincontainer.PAUSE, Maincontainer.HARD_DROP};
    }

    private static void restoreKeys(int[] keys) {
        Maincontainer.ROTATE = keys[0];
        Maincontainer.DOWN = keys[1];
        Maincontainer.LEFT = keys[2];
        Maincontainer.RIGHT = keys[3];
        Maincontainer.PAUSE = keys[4];
        Maincontainer.HARD_DROP = keys[5];
    }

    private static int keyFor(LocalBattleKeyBindings bindings,
            seoultech.se.tetris.game.GameCommand command) {
        return bindings.second().entrySet().stream()
            .filter(entry -> entry.getValue() == command)
            .map(java.util.Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container container) layoutTree(container);
        }
    }

    private static void assertVisible(Container root, String name) {
        Component component = findNamed(root, name);
        assertNotNull(component, name);
        assertTrue(component.getWidth() > 0 && component.getHeight() > 0,
            name + " must have visible layout space");
    }

    private static Component findNamed(Component root, String name) {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = findNamed(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
