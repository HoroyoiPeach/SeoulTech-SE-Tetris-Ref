package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.attack.AttackRow;
import seoultech.se.tetris.game.GameCommand;

class LocalBattleKeyBindingsTest {
    @Test
    void secondPlayerBindingsAvoidConfiguredFirstPlayerKeysAndExplainEveryCommand() {
        int[] original = {Maincontainer.ROTATE, Maincontainer.DOWN, Maincontainer.LEFT,
            Maincontainer.RIGHT, Maincontainer.PAUSE, Maincontainer.HARD_DROP};
        try {
            Maincontainer.ROTATE = KeyEvent.VK_W;
            Maincontainer.DOWN = KeyEvent.VK_S;
            Maincontainer.LEFT = KeyEvent.VK_A;
            Maincontainer.RIGHT = KeyEvent.VK_D;
            Maincontainer.PAUSE = KeyEvent.VK_P;
            Maincontainer.HARD_DROP = KeyEvent.VK_X;

            LocalBattleKeyBindings keys = LocalBattleKeyBindings.configured();
            HashSet<Integer> all = new HashSet<>(keys.first().keySet());
            all.add(keys.pauseKey());
            assertTrue(keys.second().keySet().stream().noneMatch(all::contains));
            assertEquals(5, new HashSet<>(keys.second().values()).size());
            assertTrue(keys.firstCommand(KeyEvent.VK_A).filter(GameCommand.LEFT::equals).isPresent());
            assertTrue(keys.isPause(KeyEvent.VK_P));
            assertFalse(keys.secondSummary().isBlank());
            assertFalse(keys.firstSummary().isBlank());
        } finally {
            Maincontainer.ROTATE = original[0];
            Maincontainer.DOWN = original[1];
            Maincontainer.LEFT = original[2];
            Maincontainer.RIGHT = original[3];
            Maincontainer.PAUSE = original[4];
            Maincontainer.HARD_DROP = original[5];
        }
    }

    @Test
    void pendingAttackPreviewPreservesRowOrderAndHoles() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            IncomingAttackPanel preview = new IncomingAttackPanel("player1");
            preview.showRows(java.util.List.of(
                new AttackRow(new boolean[] {true, false, true, true}),
                new AttackRow(new boolean[] {false, true, true, true})));

            assertEquals("INCOMING 2/10", preview.title().getText());
            assertEquals("G.GG\n.GGG", preview.rows().getText());
            assertEquals("player1Incoming", preview.title().getName());
            return null;
        });
    }

    @Test
    void fallbackPoolStillFindsKeysWhenAWholePreferredGroupIsReserved() {
        int[] original = {Maincontainer.ROTATE, Maincontainer.DOWN, Maincontainer.LEFT,
            Maincontainer.RIGHT, Maincontainer.PAUSE, Maincontainer.HARD_DROP};
        try {
            Maincontainer.ROTATE = KeyEvent.VK_W;
            Maincontainer.DOWN = KeyEvent.VK_R;
            Maincontainer.LEFT = KeyEvent.VK_U;
            Maincontainer.RIGHT = KeyEvent.VK_I;
            Maincontainer.PAUSE = KeyEvent.VK_P;
            Maincontainer.HARD_DROP = KeyEvent.VK_SPACE;
            LocalBattleKeyBindings keys = LocalBattleKeyBindings.configured();
            assertEquals(5, keys.second().size());
            assertTrue(keys.second().keySet().stream().noneMatch(keys.first().keySet()::contains));
        } finally {
            Maincontainer.ROTATE = original[0];
            Maincontainer.DOWN = original[1];
            Maincontainer.LEFT = original[2];
            Maincontainer.RIGHT = original[3];
            Maincontainer.PAUSE = original[4];
            Maincontainer.HARD_DROP = original[5];
        }
    }
}
