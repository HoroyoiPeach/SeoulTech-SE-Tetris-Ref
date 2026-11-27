package seoultech.se.tetris.component;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.IntFunction;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.BattleMatchSnapshot;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.MatchOutcome;
import seoultech.se.tetris.network.P2PBattleSession;
import seoultech.se.tetris.network.P2PBattleSession.Phase;
import seoultech.se.tetris.network.P2PBattleSession.View;
import seoultech.se.tetris.network.LocalNetworkAddresses;
import seoultech.se.tetris.storage.LastPeerAddress;

/** All screens for a single network connection. Only the session thread touches the transport. */
final class P2PPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    static final int PORT = 30000;
    private static final Executor PEER_ADDRESS_IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "tetris-peer-address");
        thread.setDaemon(true);
        return thread;
    });

    private final P2PBattleSession session;
    private final Runnable back;
    private final int port;
    private final LocalBattleKeyBindings keys = LocalBattleKeyBindings.configured();
    private final Executor addressIo;
    private final Executor networkAddressIo;
    private final IntFunction<String> hostAddressProvider;
    private String joiningAddress;
    private long joinAttempt;
    private boolean saveFailed;
    private final CardLayout layout = new CardLayout();
    private final JPanel cards = new JPanel(layout);
    private final JLabel title = label("P2P BATTLE");
    private final JLabel status = label(" ");
    private final JTextField address = new JTextField(18);
    private final JLabel connection = label(" ");
    private final JTextArea localAddress = new JTextArea(5, 36);
    private final JLabel readiness = label(" ");
    private final JToggleButton[] modeButtons = new JToggleButton[GameMode.values().length];
    private final BattlePlayerView own = new BattlePlayerView("YOU", "own");
    private final BattlePlayerView opponent = new BattlePlayerView("OPPONENT", "opponent");
    private final JLabel clock = label(" ");
    private final JLabel outcome = label(" ");
    private final JButton hostButton;
    private final JButton joinButton;
    private final JButton cancelButton;
    private final JButton readyButton;
    private View current;
    private long addressLookupGeneration;
    private volatile boolean closed;

    P2PPanel(Difficulty difficulty, int timeLimitSeconds, int port, Runnable back) {
        this(difficulty, timeLimitSeconds, port, back, PEER_ADDRESS_IO);
    }

    P2PPanel(Difficulty difficulty, int timeLimitSeconds, int port, Runnable back, Executor addressIo) {
        this(difficulty, timeLimitSeconds, port, back, addressIo, ForkJoinPool.commonPool(), P2PPanel::hostAddressText);
    }

    P2PPanel(Difficulty difficulty, int timeLimitSeconds, int port, Runnable back, Executor addressIo,
            Executor networkAddressIo, IntFunction<String> hostAddressProvider) {
        super(new BorderLayout(4, 4));
        this.back = back;
        this.port = port;
        this.addressIo = addressIo;
        this.networkAddressIo = networkAddressIo;
        this.hostAddressProvider = hostAddressProvider;
        setName("p2pPanel");
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setBackground(Maincontainer.changeColor(Color.LIGHT_GRAY));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        session = new P2PBattleSession(difficulty, timeLimitSeconds, this::receiveView);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        status.setName("p2pStatus");
        JPanel header = rows(title, status);
        add(header, BorderLayout.NORTH);
        cards.setOpaque(false);
        add(cards, BorderLayout.CENTER);

        hostButton = button("HOST GAME", () -> {
            setSetupEnabled(false);
            session.host(port);
        });
        joinButton = button("JOIN GAME", this::join);
        address.setName("peerAddress");
        address.addActionListener(event -> joinButton.doClick());
        JButton mainMenuButton = button("MAIN MENU", this::exit);
        cards.add(centered(rows(hostButton, label("Server IP"), address,
            joinButton, mainMenuButton)), "setup");
        bindVertical(hostButton, mainMenuButton, address);
        bindVertical(address, hostButton, joinButton);
        bindVertical(joinButton, address, mainMenuButton);
        bindVertical(mainMenuButton, joinButton, hostButton);
        localAddress.setName("hostAddresses");
        localAddress.setEditable(false);
        localAddress.setFocusable(false);
        localAddress.setOpaque(false);
        localAddress.setForeground(Maincontainer.changeColor(Color.DARK_GRAY));
        JScrollPane addressScroll = new JScrollPane(localAddress);
        addressScroll.setOpaque(false);
        addressScroll.getViewport().setOpaque(false);
        cancelButton = button("CANCEL", session::leave);
        cards.add(centered(rows(connection, addressScroll, cancelButton)), "wait");

        JPanel modeChoices = new JPanel(new GridLayout(1, modeButtons.length, 6, 0));
        modeChoices.setName("battleModes");
        modeChoices.setOpaque(false);
        ButtonGroup modeGroup = new ButtonGroup();
        for (GameMode choice : GameMode.values()) {
            JToggleButton choiceButton = new JToggleButton(choice.displayName());
            choiceButton.setName("battleMode." + choice.name());
            MenuButtons.styleActionButton(choiceButton);
            choiceButton.setFocusable(true);
            choiceButton.addItemListener(event -> {
                choiceButton.setOpaque(choiceButton.isSelected());
                choiceButton.repaint();
            });
            choiceButton.addActionListener(event -> selectMode(choice));
            int index = choice.ordinal();
            bind(choiceButton, WHEN_FOCUSED, KeyEvent.VK_ENTER, () -> choiceButton.doClick(0));
            bind(choiceButton, WHEN_FOCUSED, KeyEvent.VK_LEFT, () -> focusMode(index - 1));
            bind(choiceButton, WHEN_FOCUSED, KeyEvent.VK_RIGHT, () -> focusMode(index + 1));
            modeButtons[index] = choiceButton;
            modeGroup.add(choiceButton);
            modeChoices.add(choiceButton);
        }
        readyButton = button("START", () -> session.ready(current.round()));
        for (JToggleButton choiceButton : modeButtons) {
            bind(choiceButton, WHEN_FOCUSED, KeyEvent.VK_UP, readyButton::requestFocusInWindow);
            bind(choiceButton, WHEN_FOCUSED, KeyEvent.VK_DOWN, readyButton::requestFocusInWindow);
        }
        bind(readyButton, WHEN_FOCUSED, KeyEvent.VK_UP, this::focusSelectedMode);
        bind(readyButton, WHEN_FOCUSED, KeyEvent.VK_DOWN, this::focusSelectedMode);
        outcome.setName("matchOutcome");
        cards.add(centered(rows(outcome, label("Battle mode"), modeChoices, readiness, readyButton,
            button("DISCONNECT", session::leave))), "lobby");

        JPanel match = new JPanel(new BorderLayout(4, 4));
        match.setOpaque(false);
        JPanel players = new JPanel(new GridLayout(1, 2, 6, 0));
        players.setOpaque(false);
        players.add(own);
        players.add(opponent);
        match.add(players, BorderLayout.CENTER);
        clock.setName("matchClock");
        match.add(clock, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER));
        actions.setOpaque(false);
        JButton leaveMatch = button("DISCONNECT", session::leave);
        leaveMatch.setFocusable(false);
        actions.add(leaveMatch);
        match.add(rows(label(keys.firstSummary() + "   PAUSE " + KeyEvent.getKeyText(keys.pauseKey())), actions),
            BorderLayout.SOUTH);
        cards.add(match, "match");
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                handleKey(event.getKeyCode());
            }
        });
        bindGameplayKeys();
        CompletableFuture.supplyAsync(() -> {
            try { return LastPeerAddress.load(); }
            catch (IOException | IllegalStateException ignored) { return ""; }
        }, addressIo).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
            if (!closed && address.getText().isEmpty()) address.setText(saved);
        }));
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setForeground(Maincontainer.changeColor(Color.DARK_GRAY));
        return label;
    }

    private static JPanel rows(JComponent... components) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setOpaque(false);
        for (JComponent component : components) panel.add(component);
        return panel;
    }

    private static JPanel centered(JPanel content) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.add(content);
        return panel;
    }

    private static JButton button(String caption, Runnable action) {
        JButton button = new JButton(caption);
        button.addActionListener(event -> action.run());
        button.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate");
        button.getActionMap().put("activate", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { button.doClick(0); }
        });
        return button;
    }

    private static void bind(JComponent component, int scope, int key, Runnable action) {
        KeyStroke stroke = KeyStroke.getKeyStroke(key, 0);
        component.getInputMap(scope).put(stroke, stroke);
        component.getActionMap().put(stroke, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private static void bindVertical(JComponent component, JComponent up, JComponent down) {
        bind(component, WHEN_FOCUSED, KeyEvent.VK_UP, up::requestFocusInWindow);
        bind(component, WHEN_FOCUSED, KeyEvent.VK_DOWN, down::requestFocusInWindow);
    }

    private void bindGameplayKeys() {
        for (int key : keys.first().keySet()) {
            bind(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, key, () -> handleKey(key));
        }
        bind(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keys.pauseKey(), () -> handleKey(keys.pauseKey()));
        bind(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_ESCAPE,
            () -> handleKey(KeyEvent.VK_ESCAPE));
        bind(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_ENTER,
            () -> handleKey(KeyEvent.VK_ENTER));
    }

    private void selectMode(GameMode choice) {
        if (current != null && current.phase() == Phase.LOBBY && current.host()
                && !current.hostReady() && !current.delayed() && choice != current.mode()) {
            session.selectMode(choice, current.round());
        }
    }

    private void focusMode(int index) {
        if (!modeButtons[0].isEnabled()) return;
        int target = Math.floorMod(index, modeButtons.length);
        JToggleButton button = modeButtons[target];
        button.requestFocusInWindow();
        if (!button.isSelected()) button.doClick(0);
    }

    private void focusSelectedMode() {
        if (current == null || !current.host() || current.hostReady() || current.delayed()) return;
        modeButtons[current.mode().ordinal()].requestFocusInWindow();
    }

    private void join() {
        String host = address.getText().strip();
        if (host.isEmpty() || host.length() > 253 || host.chars().anyMatch(Character::isWhitespace)) {
            status.setText("Enter the server IP.");
            address.requestFocusInWindow();
            return;
        }
        joiningAddress = host;
        joinAttempt++;
        setSetupEnabled(false);
        session.join(host, port);
    }

    private void setSetupEnabled(boolean enabled) {
        hostButton.setEnabled(enabled);
        joinButton.setEnabled(enabled);
        address.setEnabled(enabled);
    }

    private void receiveView(View view) {
        SwingUtilities.invokeLater(() -> {
            if (!closed) render(view);
        });
    }

    private void saveConnectedAddress(String host, long attempt) {
        addressIo.execute(() -> {
            try {
                LastPeerAddress.save(host);
            } catch (IOException | IllegalStateException e) {
                SwingUtilities.invokeLater(() -> {
                    if (!closed && attempt == joinAttempt && current.phase() == Phase.LOBBY && !current.host()) {
                        saveFailed = true;
                        showStatus(current);
                    }
                });
            }
        });
    }

    private void showStatus(View view) {
        String notice = view.notice() == null ? "" : view.notice();
        status.setText(view.delayed() ? "Network delay · waiting for peer" :
            view.highLatency() ? "Network delay · slow connection" :
            saveFailed && view.phase() == Phase.LOBBY ? "Could not save the server IP." :
            notice.isBlank() ? " " : notice);
    }

    private void render(View view) {
        Phase previous = current == null ? null : current.phase();
        current = view;
        if (previous == Phase.LISTENING && view.phase() != Phase.LISTENING) addressLookupGeneration++;
        if (view.phase() == Phase.SETUP) saveFailed = false;
        showStatus(view);
        switch (view.phase()) {
            case SETUP -> {
                title.setText("P2P BATTLE");
                setSetupEnabled(true);
                if (previous != Phase.SETUP) {
                    layout.show(cards, "setup");
                    focusSetup();
                }
            }
            case CONNECTING, LISTENING -> {
                if (previous == view.phase()) return;
                boolean listening = view.phase() == Phase.LISTENING;
                connection.setText(listening ? "Waiting for a player…" : "Connecting to server…");
                localAddress.setText(listening ? "Checking local network addresses…" : joiningAddress);
                if (listening) {
                    long lookup = ++addressLookupGeneration;
                    CompletableFuture.supplyAsync(() -> hostAddressProvider.apply(port), networkAddressIo)
                            .thenAccept(text -> SwingUtilities.invokeLater(() -> {
                                if (!closed && current != null && current.phase() == Phase.LISTENING
                                        && lookup == addressLookupGeneration) {
                                    localAddress.setText(text);
                                    localAddress.setCaretPosition(0);
                                }
                            }));
                }
                layout.show(cards, "wait");
                cancelButton.requestFocusInWindow();
            }
            case LOBBY -> {
                if (!view.host() && joiningAddress != null) {
                    saveConnectedAddress(joiningAddress, joinAttempt);
                    joiningAddress = null;
                }
                title.setText(view.host() ? "LOBBY · HOST" : "LOBBY · CLIENT");
                outcome.setText(winner(view));
                boolean canSelectMode = view.host() && !view.hostReady() && !view.delayed();
                for (JToggleButton button : modeButtons) button.setEnabled(canSelectMode);
                modeButtons[view.mode().ordinal()].setSelected(true);
                readiness.setText("Host " + readyState(view.hostReady()) + "   ·   Client " + readyState(view.guestReady()));
                readyButton.setEnabled(!view.delayed() && !(view.host() ? view.hostReady() : view.guestReady()));
                if (previous != Phase.LOBBY) {
                    layout.show(cards, "lobby");
                    if (canSelectMode) focusSelectedMode();
                    else readyButton.requestFocusInWindow();
                }
            }
            case PLAYING, RESULT -> {
                if (view.match() == null) return;
                showMatch(view);
                if (previous != view.phase()) {
                    layout.show(cards, "match");
                    requestFocusInWindow();
                }
            }
        }
    }

    private static String hostAddressText(int port) {
        try {
            return LocalNetworkAddresses.discoverConnectionText(port);
        } catch (IOException | RuntimeException ignored) {
            return LocalNetworkAddresses.connectionText(List.of(), port);
        }
    }

    private static String readyState(boolean ready) {
        return ready ? "ready" : "waiting";
    }

    private void showMatch(View view) {
        BattleMatchSnapshot match = view.match();
        boolean host = view.host();
        own.show(host ? match.first() : match.second(),
            host ? match.firstPendingAttackRows() : match.secondPendingAttackRows());
        opponent.show(host ? match.second() : match.first(),
            host ? match.secondPendingAttackRows() : match.firstPendingAttackRows());
        title.setText("P2P · " + view.mode().displayName().toUpperCase(Locale.ROOT));
        String time = view.mode() == GameMode.TIME_LIMIT
            ? String.format("TIME %02d:%02d", match.remainingSeconds() / 60, match.remainingSeconds() % 60) : "";
        if (!match.stopped() && match.first().paused()) time += "  PAUSED · ENTER TO DISCONNECT";
        clock.setText(time);
    }

    private static String winner(View view) {
        if (view.match() == null) return " ";
        return view.match().decision().map(decision -> {
            if (decision.outcome() == MatchOutcome.DRAW) return "DRAW";
            return (decision.outcome() == MatchOutcome.LOCAL_WIN) == view.host() ? "YOU WIN" : "YOU LOSE";
        }).orElse(" ");
    }

    private void handleKey(int key) {
        if (current == null) return;
        if (key == KeyEvent.VK_ESCAPE) {
            if (current.phase() == Phase.SETUP) exit();
            else session.leave();
            return;
        }
        if (current.phase() != Phase.PLAYING) return;
        if (key == KeyEvent.VK_ENTER && current.match() != null && current.match().first().paused()) {
            session.leave();
        } else if (keys.isPause(key)) {
            sendVisibleInput(GameCommand.TOGGLE_PAUSE);
        } else {
            keys.firstCommand(key).ifPresentOrElse(this::sendVisibleInput, () -> {
                if (key == KeyEvent.VK_ESCAPE) session.leave();
            });
        }
    }

    private void sendVisibleInput(GameCommand command) {
        if (!current.delayed()) session.input(command, current.round());
    }

    void focusSetup() {
        hostButton.requestFocusInWindow();
    }

    private void exit() {
        shutdown();
        back.run();
    }

    void shutdown() {
        closed = true;
        session.close();
    }

    @Override public void removeNotify() {
        if (!closed) shutdown();
        super.removeNotify();
    }
}
