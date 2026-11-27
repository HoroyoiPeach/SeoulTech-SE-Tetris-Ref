package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.BattleMatch;
import seoultech.se.tetris.game.BattleMatchSnapshot;
import seoultech.se.tetris.game.BattleMatchUpdate;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.MatchDecision;
import seoultech.se.tetris.game.PlayerSide;

/** One-window local battle screen backed by the transport-neutral match model. */
public final class LocalBattlePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final long ONE_SECOND_NANOS = 1_000_000_000L;

    private final GameConfig matchConfig;
    private final Duration timeLimit;
    private final LongSupplier nanoTime;
    private final Runnable onMenu;
    private final LocalBattleKeyBindings keys;
    private final BattlePlayerView firstView = BattlePlayerView.local("PLAYER 1", "player1");
    private final BattlePlayerView secondView = BattlePlayerView.local("PLAYER 2", "player2");
    private final JLabel time = new JLabel("", SwingConstants.CENTER);
    private final JLabel result = new JLabel("", SwingConstants.CENTER);
    private final JButton newMatch = new JButton("NEW MATCH");
    private final JButton menu = new JButton("MENU");
    private final Timer timer;

    private BattleMatch match;
    private long lastAdvanceNanos;
    private long timerGeneration;
    private boolean running;

    public LocalBattlePanel(Maincontainer owner, GameMode mode, Runnable onMenu) {
        this(Objects.requireNonNull(owner, "owner").getDifficulty(), owner.getTimeLimitSeconds(),
            mode, onMenu, System::nanoTime);
    }

    LocalBattlePanel(Difficulty difficulty, int timeLimitSeconds, GameMode mode, Runnable onMenu,
            LongSupplier nanoTime) {
        super(new BorderLayout(6, 6));
        setName("localBattlePanel");
        setBackground(changeColor(Color.LIGHT_GRAY));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.onMenu = Objects.requireNonNull(onMenu, "onMenu");
        GameMode safeMode = Objects.requireNonNull(mode, "mode");
        Difficulty safeDifficulty = Objects.requireNonNull(difficulty, "difficulty");
        if (timeLimitSeconds <= 0) throw new IllegalArgumentException("timeLimitSeconds must be positive");
        matchConfig = new GameConfig(safeMode, safeDifficulty);
        timeLimit = Duration.ofSeconds(timeLimitSeconds);
        keys = LocalBattleKeyBindings.configured();
        timer = new Timer(1, event -> timerFired(event.getActionCommand()));
        timer.setRepeats(false);

        add(header(safeMode), BorderLayout.NORTH);
        JPanel players = new JPanel(new GridLayout(1, 2, 6, 0));
        players.setOpaque(false);
        players.add(firstView);
        players.add(secondView);
        add(players, BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) { dispatchKey(event.getKeyCode()); }
        });
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        resetMatch();
    }

    private JPanel header(GameMode mode) {
        JPanel header = new JPanel(new GridLayout(3, 1, 0, 1));
        header.setOpaque(false);
        JLabel title = new JLabel("LOCAL BATTLE · " + mode.displayName().toUpperCase(),
            SwingConstants.CENTER);
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        title.setForeground(changeColor(Color.DARK_GRAY));
        time.setName("matchTime");
        time.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        time.setForeground(changeColor(Color.RED));
        result.setName("matchResult");
        result.setFont(new Font(Font.MONOSPACED, Font.BOLD, 17));
        result.setForeground(changeColor(Color.RED));
        header.add(title);
        header.add(time);
        header.add(result);
        return header;
    }

    private JPanel footer() {
        JPanel footer = new JPanel(new BorderLayout(4, 2));
        footer.setOpaque(false);
        JPanel controls = new JPanel(new GridLayout(2, 1));
        controls.setOpaque(false);
        controls.add(controlLabel("P1  " + keys.firstSummary()));
        controls.add(controlLabel("P2  " + keys.secondSummary()
            + "  ·  PAUSE " + KeyEvent.getKeyText(keys.pauseKey())));
        footer.add(controls, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 2, 4, 0));
        actions.setOpaque(false);
        newMatch.setName("newMatchButton");
        menu.setName("menuButton");
        MenuButtons.styleActionButton(newMatch);
        MenuButtons.styleActionButton(menu);
        newMatch.setFocusable(false);
        menu.setFocusable(false);
        newMatch.addActionListener(event -> restartMatch());
        menu.addActionListener(event -> returnToMenu());
        actions.add(newMatch);
        actions.add(menu);
        footer.add(actions, BorderLayout.SOUTH);
        return footer;
    }

    private static JLabel controlLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        label.setForeground(changeColor(Color.DARK_GRAY));
        return label;
    }

    public void startMatch() {
        if (running || match.snapshot().stopped()) return;
        running = true;
        lastAdvanceNanos = nanoTime.getAsLong();
        consume(match.start());
        requestFocusInWindow();
    }

    public void stopMatch() {
        if (!running && match.snapshot().stopped()) return;
        running = false;
        cancelTimer();
        publish(match.stop().snapshot());
    }

    void dispatchKey(int keyCode) {
        BattleMatchSnapshot current = match.snapshot();
        if (current.decision().isPresent()) {
            if (keyCode == KeyEvent.VK_ENTER) restartMatch();
            else if (keyCode == KeyEvent.VK_ESCAPE) returnToMenu();
            return;
        }
        if (!running) return;
        advanceToNow();
        if (!running) return;
        if (current.first().paused() && keyCode == KeyEvent.VK_ENTER) {
            returnToMenu();
            return;
        }
        if (keys.isPause(keyCode)) {
            consume(match.handle(PlayerSide.FIRST, GameCommand.TOGGLE_PAUSE));
            return;
        }
        var firstCommand = keys.firstCommand(keyCode);
        if (firstCommand.isPresent()) {
            consume(match.handle(PlayerSide.FIRST, firstCommand.orElseThrow()));
            return;
        }
        var secondCommand = keys.secondCommand(keyCode);
        if (secondCommand.isPresent()) {
            consume(match.handle(PlayerSide.SECOND, secondCommand.orElseThrow()));
            return;
        }
        if (keyCode == KeyEvent.VK_ESCAPE) returnToMenu();
    }

    BattleMatchSnapshot snapshot() {
        return match.snapshot();
    }

    boolean isRunning() {
        return running;
    }

    private void restartMatch() {
        if (running) stopMatch();
        resetMatch();
        startMatch();
    }

    private void resetMatch() {
        match = new BattleMatch(matchConfig, timeLimit, nanoTime);
        result.setText(" ");
        newMatch.setEnabled(false);
        publish(match.snapshot());
    }

    private void returnToMenu() {
        stopMatch();
        onMenu.run();
    }

    private void timerFired(String command) {
        long generation;
        try {
            generation = Long.parseLong(command);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (!running || generation != timerGeneration) return;
        advanceToNow();
    }

    private void advanceToNow() {
        long now = nanoTime.getAsLong();
        long elapsed = Math.max(0, now - lastAdvanceNanos);
        lastAdvanceNanos = now;
        consume(match.advance(elapsed));
    }

    private void consume(BattleMatchUpdate update) {
        publish(update.snapshot());
        if (update.snapshot().decision().isPresent() || update.snapshot().stopped()) {
            running = false;
            cancelTimer();
            return;
        }
        schedule(update.nextWakeupNanos());
    }

    private void publish(BattleMatchSnapshot snapshot) {
        firstView.show(snapshot.first(), snapshot.firstPendingAttackRows());
        secondView.show(snapshot.second(), snapshot.secondPendingAttackRows());
        String pauseHint = !snapshot.stopped() && snapshot.first().paused() ? "PAUSED · ENTER FOR MENU" : "";
        if (snapshot.first().mode() == GameMode.TIME_LIMIT) {
            time.setText(String.format("TIME %02d:%02d%s",
                snapshot.remainingSeconds() / 60, snapshot.remainingSeconds() % 60,
                pauseHint.isEmpty() ? "" : " · " + pauseHint));
        } else {
            time.setText(pauseHint);
        }
        snapshot.decision().ifPresent(this::showDecision);
        revalidate();
        repaint();
    }

    private void showDecision(MatchDecision decision) {
        result.setText(switch (decision.outcome()) {
            case LOCAL_WIN -> "PLAYER 1 WINS · ENTER OR NEW MATCH";
            case REMOTE_WIN -> "PLAYER 2 WINS · ENTER OR NEW MATCH";
            case DRAW -> "DRAW · ENTER OR NEW MATCH";
        });
        newMatch.setEnabled(true);
    }

    private void schedule(OptionalLong wakeupNanos) {
        timer.stop();
        long generation = ++timerGeneration;
        if (!running || wakeupNanos.isEmpty()) return;
        long nanos = Math.min(wakeupNanos.getAsLong(), ONE_SECOND_NANOS);
        long millis = Math.max(1, (nanos + 999_999L) / 1_000_000L);
        timer.setInitialDelay(millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis);
        timer.setActionCommand(Long.toString(generation));
        timer.restart();
    }

    private void cancelTimer() {
        timer.stop();
        timerGeneration++;
        timer.setActionCommand(Long.toString(timerGeneration));
    }

    @Override
    public void removeNotify() {
        stopMatch();
        super.removeNotify();
    }
}
