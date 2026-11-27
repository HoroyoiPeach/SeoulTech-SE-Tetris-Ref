package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.BattleMatch;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.network.P2PBattleSession.Phase;
import seoultech.se.tetris.network.P2PBattleSession.View;

class P2PResultViewTest {
    @Test
    void presentsEachPerspectiveAndRequiresOneLocalAcknowledgement() {
        var match = new BattleMatch(new GameConfig(GameMode.CLASSIC, Difficulty.NORMAL),
            Duration.ofSeconds(3), System::nanoTime);
        match.start();
        var finished = match.handle(seoultech.se.tetris.game.PlayerSide.SECOND,
            seoultech.se.tetris.game.GameCommand.HARD_DROP);
        while (finished.snapshot().decision().isEmpty()) {
            finished = match.handle(seoultech.se.tetris.game.PlayerSide.SECOND,
                seoultech.se.tetris.game.GameCommand.HARD_DROP);
        }
        AtomicInteger acknowledgements = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        P2PResultView result = new P2PResultView(acknowledgements::incrementAndGet, disconnects::incrementAndGet);

        result.render(new View(Phase.RESULT, 1, true, GameMode.CLASSIC, false, false,
            finished.snapshot(), false, false, ""));
        assertEquals("YOU WIN", text(result, "matchOutcome"));
        assertEquals("Review the result, then continue", text(result, "matchResultReadiness"));
        AbstractButton button = button(result, "resultContinueButton");
        assertTrue(button.isEnabled());
        button.doClick();
        assertEquals(1, acknowledgements.get());
        AbstractButton disconnect = button(result, "resultDisconnectButton");
        assertTrue(button.isFocusable());
        assertTrue(button.isFocusPainted());
        assertTrue(disconnect.isFocusable());
        assertTrue(disconnect.isFocusPainted());
        assertTrue(disconnect.isEnabled());
        disconnect.doClick();
        assertEquals(1, disconnects.get());

        result.render(new View(Phase.RESULT, 1, false, GameMode.CLASSIC, true, false,
            finished.snapshot(), false, false, ""));
        assertEquals("YOU LOSE", text(result, "matchOutcome"));
        assertEquals("Opponent is ready", text(result, "matchResultReadiness"));
        assertTrue(button.isEnabled());

        result.render(new View(Phase.RESULT, 1, false, GameMode.CLASSIC, true, true,
            finished.snapshot(), false, false, ""));
        assertEquals("Waiting for opponent…", text(result, "matchResultReadiness"));
        assertFalse(button.isEnabled());
        assertTrue(disconnect.isEnabled());

        result.render(new View(Phase.RESULT, 1, false, GameMode.CLASSIC, false, false,
            finished.snapshot(), true, false, ""));
        assertEquals("Connection interrupted · continue or disconnect",
            text(result, "matchResultReadiness"));
        assertTrue(button.isEnabled());
        assertTrue(button.isFocusPainted());
    }

    private static String text(P2PResultView view, String name) {
        for (Component child : view.getComponents()) {
            if (child instanceof JLabel label && name.equals(label.getName())) return label.getText();
        }
        throw new AssertionError("Missing label " + name);
    }

    private static AbstractButton button(P2PResultView view, String name) {
        for (Component child : view.getComponents()) {
            if (child instanceof AbstractButton button && name.equals(button.getName())) return button;
        }
        throw new AssertionError("Missing button " + name);
    }
}
