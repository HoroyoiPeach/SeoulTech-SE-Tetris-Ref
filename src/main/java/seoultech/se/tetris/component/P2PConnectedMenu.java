package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import seoultech.se.tetris.GameMode;

/** Start-menu-shaped navigation shown while the P2P connection remains open. */
final class P2PConnectedMenu extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int MODE_COUNT = GameMode.values().length;

    private final List<Choice> choices = new ArrayList<>();
    private final JLabel connection = new JLabel(" ", SwingConstants.CENTER);
    private final Consumer<GameMode> selectMode;
    private final Runnable reopenReadiness;
    private boolean host;
    private GameMode selectedMode = GameMode.CLASSIC;
    private boolean modeSelected;
    private int selected;

    P2PConnectedMenu(Consumer<GameMode> selectMode,
            Runnable reopenReadiness, Runnable openSettings, Runnable openScore, Runnable exit) {
        super(new GridBagLayout());
        this.selectMode = selectMode;
        this.reopenReadiness = reopenReadiness;
        setName("p2pConnectedMenu");
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel heading = new JPanel(new GridBagLayout());
        heading.setOpaque(false);
        GridBagConstraints headingPosition = new GridBagConstraints();
        headingPosition.gridx = 0;
        headingPosition.gridy = 0;
        JLabel title = new JLabel("TETRIS", SwingConstants.CENTER);
        title.setFont(new Font("Courier", Font.BOLD, 56));
        title.setForeground(changeColor(Color.WHITE));
        heading.add(title, headingPosition);
        connection.setName("p2pConnectedStatus");
        connection.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        headingPosition.gridy = 1;
        heading.add(connection, headingPosition);

        GridBagConstraints position = new GridBagConstraints();
        position.gridx = 0;
        position.gridy = 0;
        position.fill = GridBagConstraints.NONE;
        position.weighty = 0.3;
        add(heading, position);

        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setName("p2pConnectedMenuChoices");
        buttons.setOpaque(false);
        buttons.setPreferredSize(new Dimension(200, 300));
        addChoice(buttons, "CLASSIC", () -> choose(GameMode.CLASSIC));
        addChoice(buttons, "ITEM", () -> choose(GameMode.ITEM));
        addChoice(buttons, "TIME LIMIT", () -> choose(GameMode.TIME_LIMIT));
        addChoice(buttons, "MENU", openSettings);
        addChoice(buttons, "SCORE", openScore);
        addChoice(buttons, "EXIT", exit);

        position.gridy = 1;
        position.weighty = 0.7;
        position.insets = new Insets(10, 10, 10, 10);
        add(buttons, position);
        select(0);
        bind(KeyEvent.VK_UP, () -> move(-1));
        bind(KeyEvent.VK_DOWN, () -> move(1));
        bind(KeyEvent.VK_ENTER, this::activateSelected);
        refreshTheme();
    }

    private void choose(GameMode mode) {
        if (host) selectMode.accept(mode);
        else if (modeSelected && mode == selectedMode) reopenReadiness.run();
    }

    private void addChoice(JPanel parent, String text, Runnable action) {
        Choice choice = new Choice(text, action, choices.size());
        GridBagConstraints position = new GridBagConstraints();
        position.gridx = 0;
        position.gridy = choices.size();
        position.insets = new Insets(2, 2, 2, 2);
        parent.add(choice, position);
        choices.add(choice);
    }

    private void bind(int key, Runnable action) {
        KeyStroke stroke = KeyStroke.getKeyStroke(key, 0);
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, stroke);
        getActionMap().put(stroke, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void move(int direction) {
        int next = selected;
        do {
            next = Math.floorMod(next + direction, choices.size());
        } while (!choices.get(next).isChoiceEnabled());
        select(next);
    }

    private void select(int index) {
        choices.get(selected).setOpaque(false);
        selected = index;
        choices.get(selected).setOpaque(true);
        revalidate();
        repaint();
    }

    private void activateSelected() {
        choices.get(selected).activate();
    }

    void render(boolean isHost, GameMode mode, boolean modeSelected) {
        host = isHost;
        selectedMode = mode;
        this.modeSelected = modeSelected;
        String role = isHost ? "HOST" : "CLIENT";
        String state = modeSelected
            ? "SELECTED · " + mode.displayName().toUpperCase()
            : isHost ? "SELECT GAME" : "HOST SELECTS GAME";
        connection.setText("CONNECTED · " + role + "   ·   " + state);
        for (int index = 0; index < MODE_COUNT; index++) {
            choices.get(index).setChoiceEnabled(isHost || modeSelected && index == mode.ordinal());
        }
        if (!isHost && selected < MODE_COUNT && !choices.get(selected).isChoiceEnabled()) select(MODE_COUNT);
    }

    void focusMenu() {
        requestFocusInWindow();
    }

    void selectCurrentMode() {
        if (modeSelected && choices.get(selectedMode.ordinal()).isChoiceEnabled()) {
            select(selectedMode.ordinal());
        }
    }

    void dispatchKey(int key) {
        KeyStroke stroke = KeyStroke.getKeyStroke(key, 0);
        Object actionKey = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(stroke);
        if (actionKey == null) return;
        javax.swing.Action action = getActionMap().get(actionKey);
        if (action != null) action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
            actionKey.toString()));
    }

    void refreshTheme() {
        setBackground(changeColor(Color.LIGHT_GRAY));
        connection.setForeground(changeColor(Color.DARK_GRAY));
        for (Choice choice : choices) choice.refreshTheme();
        repaint();
    }

    private final class Choice extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Runnable action;
        private final JLabel label;
        private boolean enabled = true;

        private Choice(String text, Runnable action, int index) {
            super(new GridBagLayout());
            this.action = action;
            setName("p2pMenu." + text.replace(' ', '_'));
            setOpaque(false);
            label = new JLabel(text, SwingConstants.CENTER);
            label.setFont(new Font("Courier", Font.BOLD, 16));
            add(label);
            Dimension labelSize = label.getPreferredSize();
            setPreferredSize(new Dimension(labelSize.width + 20, labelSize.height + 20));
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { select(index); }
                @Override public void mouseClicked(MouseEvent event) {
                    select(index);
                    activate();
                }
            };
            addMouseListener(mouse);
            label.addMouseListener(mouse);
        }

        private void setChoiceEnabled(boolean value) {
            enabled = value;
            label.setForeground(changeColor(value ? Color.BLACK : Color.DARK_GRAY));
        }

        private boolean isChoiceEnabled() {
            return enabled;
        }

        private void activate() {
            if (enabled) action.run();
        }

        private void refreshTheme() {
            setBackground(changeColor(Color.GRAY));
            label.setForeground(changeColor(enabled ? Color.BLACK : Color.DARK_GRAY));
        }
    }
}
