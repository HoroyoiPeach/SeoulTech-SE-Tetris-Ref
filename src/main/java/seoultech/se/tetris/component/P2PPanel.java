package seoultech.se.tetris.component;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.BattleMatchSnapshot;
import seoultech.se.tetris.game.GameCommand;
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
    private LocalBattleKeyBindings keys = LocalBattleKeyBindings.configured();
    private final Executor addressIo;
    private final Executor networkAddressIo;
    private final IntFunction<String> hostAddressProvider;
    private String joiningAddress;
    private long joinAttempt;
    private boolean saveFailed;
    private final CardLayout layout = new CardLayout();
    private final JPanel cards = new JPanel(layout);
    private final JPanel match = new JPanel(new BorderLayout(4, 4));
    private final JLabel title = label("P2P BATTLE");
    private final JLabel status = label(" ");
    private final JTextField address = new JTextField(18);
    private final JLabel connection = label(" ");
    private final JTextArea localAddress = new JTextArea(5, 36);
    private final P2PConnectedMenu connectedMenu;
    private final JLabel selectedMode = label(" ");
    private final JLabel readiness = label(" ");
    private final BattlePlayerView own = new BattlePlayerView("YOU", "own");
    private final BattlePlayerView opponent = new BattlePlayerView("OPPONENT", "opponent");
    private final JLabel clock = label(" ");
    private final JLabel controls = label(" ");
    private final P2PResultView result;
    private final JButton hostButton;
    private final JButton joinButton;
    private final JButton cancelButton;
    private final JButton readyButton;
    private final JButton backToConnectedMenu;
    private View current;
    private LobbyCard lobbyCard = LobbyCard.MENU;
    private long chosenRound = -1;
    private boolean awaitingModeRound;
    private GameMode pendingMode;
    private long pageGeneration;
    private boolean externalPageOpen;
    private BiConsumer<String, Runnable> lobbyPageOpener;
    private Consumer<Phase> phaseListener = phase -> {};
    private long addressLookupGeneration;
    private volatile boolean closed;

    private enum LobbyCard { MENU, READY }

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
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.CENTER);
        header.add(status, BorderLayout.SOUTH);
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

        connectedMenu = new P2PConnectedMenu(this::selectMode, this::showReadiness,
            () -> openLobbyPage("MENU"), () -> openLobbyPage("SCORE"), session::leave);
        cards.add(connectedMenu, "connectedMenu");

        readyButton = button("START", this::ready);
        backToConnectedMenu = button("BACK", this::showConnectedMenu);
        bindVertical(readyButton, backToConnectedMenu, backToConnectedMenu);
        bindVertical(backToConnectedMenu, readyButton, readyButton);
        selectedMode.setName("selectedBattleMode");
        cards.add(centered(rows(label("BATTLE MODE"), selectedMode, readiness,
            readyButton, backToConnectedMenu)), "readiness");

        result = new P2PResultView(session::acknowledgeResult, session::leave);
        cards.add(centered(result), "result");

        match.setName("p2pMatch");
        match.setFocusable(true);
        match.setFocusTraversalKeysEnabled(false);
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
        updateControlSummary();
        actions.add(controls);
        actions.add(leaveMatch);
        match.add(actions, BorderLayout.SOUTH);
        cards.add(match, "match");
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
        bindConfiguredGameplayKeys();
        bind(match, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_ENTER, () -> handleKey(KeyEvent.VK_ENTER));
        bind(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_ESCAPE, () -> {
            if (current != null && current.phase() == Phase.SETUP) exit();
            else if (current != null && current.phase() == Phase.PLAYING) handleKey(KeyEvent.VK_ESCAPE);
            else session.leave();
        });
    }

    private void bindConfiguredGameplayKeys() {
        for (int key : keys.first().keySet()) {
            bind(match, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, key, () -> handleKey(key));
        }
        bind(match, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keys.pauseKey(), () -> handleKey(keys.pauseKey()));
    }

    private void unbindConfiguredGameplayKeys(LocalBattleKeyBindings bindings) {
        for (int key : bindings.first().keySet()) unbindMatchKey(key);
        unbindMatchKey(bindings.pauseKey());
    }

    private void unbindMatchKey(int key) {
        KeyStroke stroke = KeyStroke.getKeyStroke(key, 0);
        Object actionKey = match.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(stroke);
        match.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).remove(stroke);
        if (actionKey != null) match.getActionMap().remove(actionKey);
    }

    private void selectMode(GameMode choice) {
        if (current != null && current.phase() == Phase.LOBBY && current.host()
                && !current.hostReady() && !current.delayed() && !awaitingModeRound) {
            lobbyCard = LobbyCard.READY;
            awaitingModeRound = true;
            pendingMode = choice;
            selectedMode.setText(choice.displayName().toUpperCase(Locale.ROOT));
            readiness.setText("Confirming mode with opponent…");
            readyButton.setEnabled(false);
            backToConnectedMenu.setEnabled(false);
            showLobbyCard(true);
            session.selectMode(choice, current.round());
        }
    }

    private void ready() {
        if (current == null || current.phase() != Phase.LOBBY || awaitingModeRound
                || chosenRound != current.round() || current.delayed()) return;
        session.ready(current.round());
    }

    private void showReadiness() {
        if (current == null || current.phase() != Phase.LOBBY || awaitingModeRound
                || chosenRound != current.round()) return;
        lobbyCard = LobbyCard.READY;
        showLobbyCard(true);
    }

    private void openLobbyPage(String page) {
        if (current == null || current.phase() != Phase.LOBBY || externalPageOpen
                || lobbyPageOpener == null) return;
        externalPageOpen = true;
        long generation = ++pageGeneration;
        lobbyPageOpener.accept(page, () -> SwingUtilities.invokeLater(() -> returnFromLobbyPage(generation)));
    }

    private void returnFromLobbyPage(long generation) {
        if (generation != pageGeneration) return;
        externalPageOpen = false;
        if (current != null && current.phase() == Phase.LOBBY) showLobbyCard(true);
    }

    private void showConnectedMenu() {
        if (current == null || current.phase() != Phase.LOBBY) return;
        lobbyCard = LobbyCard.MENU;
        connectedMenu.selectCurrentMode();
        showLobbyCard(true);
    }

    private void showLobbyCard(boolean focus) {
        if (externalPageOpen || current == null || current.phase() != Phase.LOBBY) return;
        if (lobbyCard == LobbyCard.READY) {
            title.setVisible(true);
            layout.show(cards, "readiness");
            if (focus) readyButton.requestFocusInWindow();
        } else {
            title.setVisible(false);
            layout.show(cards, "connectedMenu");
            if (focus) connectedMenu.focusMenu();
        }
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
        View previousView = current;
        Phase previous = previousView == null ? null : previousView.phase();
        current = view;
        if (previous != view.phase()) {
            phaseListener.accept(view.phase());
        }
        if (previous == Phase.LISTENING && view.phase() != Phase.LISTENING) addressLookupGeneration++;
        if (view.phase() != Phase.LOBBY && externalPageOpen) {
            externalPageOpen = false;
            pageGeneration++;
        }
        if (view.phase() == Phase.SETUP) {
            saveFailed = false;
            lobbyCard = LobbyCard.MENU;
            chosenRound = -1;
            awaitingModeRound = false;
            pendingMode = null;
        }
        showStatus(view);
        status.setVisible(!status.getText().isBlank());
        switch (view.phase()) {
            case SETUP -> {
                title.setText("P2P BATTLE");
                title.setVisible(true);
                setSetupEnabled(true);
                if (previous != Phase.SETUP) {
                    layout.show(cards, "setup");
                    focusSetup();
                }
            }
            case CONNECTING, LISTENING -> {
                title.setVisible(true);
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
                boolean enteredLobby = previous != Phase.LOBBY;
                boolean selectedNewRound = previous == Phase.LOBBY
                    && previousView != null && previousView.round() != view.round();
                if (enteredLobby) {
                    lobbyCard = LobbyCard.MENU;
                    chosenRound = -1;
                } else if (selectedNewRound) {
                    lobbyCard = LobbyCard.READY;
                    chosenRound = view.round();
                    awaitingModeRound = false;
                    pendingMode = null;
                }
                if (view.hostReady() || view.guestReady()) {
                    lobbyCard = LobbyCard.READY;
                    chosenRound = view.round();
                }
                connectedMenu.render(view.host(), view.mode(), chosenRound == view.round());
                GameMode shownMode = awaitingModeRound && pendingMode != null ? pendingMode : view.mode();
                selectedMode.setText(shownMode.displayName().toUpperCase(Locale.ROOT));
                readiness.setText("Host " + readyState(view.hostReady()) + "   ·   Client " + readyState(view.guestReady()));
                boolean localReady = view.host() ? view.hostReady() : view.guestReady();
                readyButton.setEnabled(!awaitingModeRound && chosenRound == view.round()
                    && !view.delayed() && !localReady);
                backToConnectedMenu.setEnabled(!awaitingModeRound && !localReady);
                title.setText("P2P BATTLE");
                showLobbyCard(previous != Phase.LOBBY || selectedNewRound);
            }
            case RESULT -> {
                title.setText("MATCH RESULT");
                title.setVisible(true);
                result.render(view);
                if (previous != Phase.RESULT) {
                    layout.show(cards, "result");
                    result.requestContinueFocus();
                }
            }
            case PLAYING -> {
                title.setVisible(true);
                if (view.match() == null) return;
                showMatch(view);
                if (previous != view.phase()) {
                    layout.show(cards, "match");
                    match.requestFocusInWindow();
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
        clock.setVisible(!time.isBlank());
    }

    private void handleKey(int key) {
        if (current == null || current.phase() != Phase.PLAYING) return;
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

    void setLobbyPageOpener(BiConsumer<String, Runnable> opener) {
        lobbyPageOpener = Objects.requireNonNull(opener, "opener");
    }

    void setPhaseListener(Consumer<Phase> listener) {
        phaseListener = Objects.requireNonNull(listener, "listener");
        if (current != null) {
            phaseListener.accept(current.phase());
        }
    }

    void applySettings(Difficulty difficulty, int timeLimitSeconds) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> applySettings(difficulty, timeLimitSeconds));
            return;
        }
        LocalBattleKeyBindings previous = keys;
        unbindConfiguredGameplayKeys(previous);
        keys = LocalBattleKeyBindings.configured();
        bindConfiguredGameplayKeys();
        updateControlSummary();
        session.updateMatchSettings(difficulty, timeLimitSeconds);
        setBackground(Maincontainer.changeColor(Color.LIGHT_GRAY));
        Color text = Maincontainer.changeColor(Color.DARK_GRAY);
        title.setForeground(text);
        status.setForeground(text);
        connection.setForeground(text);
        localAddress.setForeground(text);
        selectedMode.setForeground(text);
        readiness.setForeground(text);
        clock.setForeground(text);
        controls.setForeground(text);
        connectedMenu.refreshTheme();
        result.refreshTheme();
        own.repaint();
        opponent.repaint();
        revalidate();
        repaint();
    }

    private void updateControlSummary() {
        controls.setText(keys.firstSummary() + "   PAUSE " + KeyEvent.getKeyText(keys.pauseKey()));
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
