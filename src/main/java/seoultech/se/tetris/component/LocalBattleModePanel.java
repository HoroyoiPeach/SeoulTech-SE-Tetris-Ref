package seoultech.se.tetris.component;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import seoultech.se.tetris.GameMode;

final class LocalBattleModePanel extends JPanel {
    private final List<JButton> choices = new ArrayList<>();

    LocalBattleModePanel(Consumer<GameMode> start, Runnable back) {
        super(new GridBagLayout());
        setBackground(Maincontainer.changeColor(Color.LIGHT_GRAY));
        GridBagConstraints position = new GridBagConstraints();
        position.gridx = 0;
        position.gridy = 0;
        position.fill = GridBagConstraints.HORIZONTAL;
        position.insets = new Insets(4, 4, 4, 4);

        JLabel title = new JLabel("LOCAL BATTLE", JLabel.CENTER);
        title.setFont(new Font("Courier", Font.BOLD, 28));
        add(title, position);
        for (GameMode mode : GameMode.values()) {
            addChoice(mode.displayName().toUpperCase(Locale.ROOT), () -> start.accept(mode), position);
        }
        addChoice("BACK", back, position);
        bind(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_ESCAPE, back);
    }

    private void addChoice(String label, Runnable action, GridBagConstraints position) {
        JButton button = new JButton(label);
        int index = choices.size();
        choices.add(button);
        button.addActionListener(event -> action.run());
        bind(button, WHEN_FOCUSED, KeyEvent.VK_ENTER, () -> button.doClick(0));
        bind(button, WHEN_FOCUSED, KeyEvent.VK_UP, () -> focusChoice(index - 1));
        bind(button, WHEN_FOCUSED, KeyEvent.VK_DOWN, () -> focusChoice(index + 1));
        position.gridy++;
        add(button, position);
    }

    private static void bind(JComponent component, int scope, int key, Runnable action) {
        KeyStroke stroke = KeyStroke.getKeyStroke(key, 0);
        component.getInputMap(scope).put(stroke, stroke);
        component.getActionMap().put(stroke, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    void focusChoice(int index) {
        choices.get(Math.floorMod(index, choices.size())).requestFocusInWindow();
    }
}
