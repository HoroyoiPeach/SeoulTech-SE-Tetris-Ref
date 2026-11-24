package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.GameMode;

class LocalBattleModePanelTest {
    @Test
    void mouseAndKeyboardSelectTheSameModesAndEscapeReturns() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            List<GameMode> selected = new ArrayList<>();
            List<String> exits = new ArrayList<>();
            var panel = new LocalBattleModePanel(selected::add, () -> exits.add("back"));
            List<JButton> choices = Arrays.stream(panel.getComponents())
                .filter(JButton.class::isInstance).map(JButton.class::cast).toList();
            assertEquals(List.of("CLASSIC", "ITEM", "TIME LIMIT", "BACK"),
                choices.stream().map(JButton::getText).toList());
            choices.get(0).doClick(0);
            chooseWithKey(choices.get(1), JComponent.WHEN_FOCUSED, KeyEvent.VK_ENTER);
            choices.get(2).doClick(0);
            assertEquals(List.of(GameMode.CLASSIC, GameMode.ITEM, GameMode.TIME_LIMIT), selected);
            choices.get(3).doClick(0);
            chooseWithKey(panel, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_ESCAPE);
            assertEquals(List.of("back", "back"), exits);
            return null;
        });
    }

    private static void chooseWithKey(JComponent component, int scope, int key) {
        var binding = component.getInputMap(scope).get(KeyStroke.getKeyStroke(key, 0));
        component.getActionMap().get(binding).actionPerformed(
            new ActionEvent(component, ActionEvent.ACTION_PERFORMED, ""));
    }
}
