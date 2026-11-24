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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
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
import seoultech.se.tetris.network.P2PNetworkManager;
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
    private String joiningAddress;
    private long joinAttempt;
    private boolean saveFailed;
    private final CardLayout layout = new CardLayout();
    private final JPanel cards = new JPanel(layout);
    private final JLabel title = label("P2P BATTLE");
    private final JLabel status = label(" ");
    private final JTextField address = new JTextField(18);
    private final JLabel connection = label(" ");
    private final JLabel localAddress = label(" ");
    private final JLabel readiness = label(" ");
    private final JComboBox<String> mode = new JComboBox<>();
    private final BattlePlayerView own = new BattlePlayerView("YOU", "own");
    private final BattlePlayerView opponent = new BattlePlayerView("OPPONENT", "opponent");
    private final JLabel clock = label(" ");
    private final JLabel outcome = label(" ");
    private final JButton hostButton;
    private final JButton joinButton;
    private final JButton readyButton;
    private View current;
    private volatile boolean closed;

    P2PPanel(Difficulty difficulty, int timeLimitSeconds, int port, Runnable back) {
        this(difficulty, timeLimitSeconds, port, back, PEER_ADDRESS_IO);
    }

    P2PPanel(Difficulty difficulty, int timeLimitSeconds, int port, Runnable back, Executor addressIo) {
        super(new BorderLayout(4, 4));
        this.back = back;
        this.port = port;
        this.addressIo = addressIo;
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
        cards.add(centered(rows(hostButton, label("Server IP"), address,
            joinButton, button("MAIN MENU", this::exit))), "setup");
        cards.add(centered(rows(connection, localAddress, button("CANCEL", session::leave))), "wait");

        for (GameMode choice : GameMode.values()) mode.addItem(choice.displayName());
        mode.setName("battleMode");
        mode.addActionListener(event -> {
            GameMode choice = GameMode.values()[mode.getSelectedIndex()];
            if (current != null && current.phase() == Phase.LOBBY && current.host() && choice != current.mode()) {
                session.selectMode(choice, current.round());
            }
        });
        readyButton = button("START", () -> session.ready(current.round()));
        outcome.setName("matchOutcome");
        cards.add(centered(rows(outcome, label("Battle mode"), mode, readiness, readyButton,
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
                localAddress.setText(listening ? "Checking local IP…" : joiningAddress);
                if (listening) {
                    CompletableFuture.supplyAsync(P2PNetworkManager::resolveLocalInternalIp)
                        .thenAccept(ip -> SwingUtilities.invokeLater(() -> {
                            if (!closed && current.phase() == Phase.LISTENING) localAddress.setText(ip + " : " + port);
                        }));
                }
                layout.show(cards, "wait");
                cards.transferFocus();
            }
            case LOBBY -> {
                if (!view.host() && joiningAddress != null) {
                    saveConnectedAddress(joiningAddress, joinAttempt);
                    joiningAddress = null;
                }
                title.setText(view.host() ? "LOBBY · HOST" : "LOBBY · CLIENT");
                outcome.setText(winner(view));
                mode.setSelectedItem(view.mode().displayName());
                mode.setEnabled(view.host() && !view.hostReady() && !view.delayed());
                readiness.setText("Host " + readyState(view.hostReady()) + "   ·   Client " + readyState(view.guestReady()));
                readyButton.setEnabled(!view.delayed() && !(view.host() ? view.hostReady() : view.guestReady()));
                if (previous != Phase.LOBBY) {
                    layout.show(cards, "lobby");
                    readyButton.requestFocusInWindow();
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
