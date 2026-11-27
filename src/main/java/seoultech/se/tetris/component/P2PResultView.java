package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.KeyStroke;
import seoultech.se.tetris.game.MatchDecision;
import seoultech.se.tetris.game.MatchOutcome;
import seoultech.se.tetris.network.P2PBattleSession.Phase;
import seoultech.se.tetris.network.P2PBattleSession.View;

/** Persistent end-of-round summary shown until both peers acknowledge the result. */
final class P2PResultView extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JLabel outcome = label(" ", 26);
    private final JLabel score = label(" ", 18);
    private final JLabel readiness = label(" ", 14);
    private final JButton continueButton = new JButton("CONTINUE");
    private final JButton disconnectButton = new JButton("DISCONNECT");

    P2PResultView(Runnable acknowledge, Runnable disconnect) {
        super(new GridLayout(5, 1, 0, 10));
        setName("p2pResultView");
        setOpaque(false);
        outcome.setName("matchOutcome");
        score.setName("matchResultScore");
        readiness.setName("matchResultReadiness");
        continueButton.setName("resultContinueButton");
        disconnectButton.setName("resultDisconnectButton");
        MenuButtons.styleActionButton(continueButton);
        MenuButtons.styleActionButton(disconnectButton);
        continueButton.setFocusable(true);
        disconnectButton.setFocusable(true);
        continueButton.setFocusPainted(true);
        disconnectButton.setFocusPainted(true);
        continueButton.addActionListener(event -> acknowledge.run());
        disconnectButton.addActionListener(event -> disconnect.run());
        bind(continueButton, KeyEvent.VK_ENTER, continueButton::doClick);
        bind(disconnectButton, KeyEvent.VK_ENTER, disconnectButton::doClick);
        bind(continueButton, KeyEvent.VK_DOWN, disconnectButton::requestFocusInWindow);
        bind(continueButton, KeyEvent.VK_RIGHT, disconnectButton::requestFocusInWindow);
        bind(disconnectButton, KeyEvent.VK_UP, continueButton::requestFocusInWindow);
        bind(disconnectButton, KeyEvent.VK_LEFT, continueButton::requestFocusInWindow);
        add(outcome);
        add(score);
        add(readiness);
        add(continueButton);
        add(disconnectButton);
    }

    void render(View view) {
        if (view.phase() != Phase.RESULT || view.match() == null) {
            throw new IllegalArgumentException("A final match is required");
        }
        MatchDecision decision = view.match().decision().orElseThrow();
        outcome.setText(outcomeText(decision.outcome(), view.host()));
        int ownScore = view.host() ? decision.localScore() : decision.remoteScore();
        int opponentScore = view.host() ? decision.remoteScore() : decision.localScore();
        score.setText("YOU " + ownScore + "   ·   OPPONENT " + opponentScore);
        boolean localReady = view.host() ? view.hostReady() : view.guestReady();
        boolean peerReady = view.host() ? view.guestReady() : view.hostReady();
        readiness.setText(view.delayed()
            ? localReady ? "Connection interrupted · result saved" : "Connection interrupted · continue or disconnect"
            : localReady ? "Waiting for opponent…"
            : peerReady ? "Opponent is ready" : "Review the result, then continue");
        continueButton.setEnabled(!localReady);
    }

    void requestContinueFocus() {
        continueButton.requestFocusInWindow();
    }

    private static String outcomeText(MatchOutcome result, boolean host) {
        if (result == MatchOutcome.DRAW) return "DRAW";
        boolean hostWon = result == MatchOutcome.LOCAL_WIN;
        return hostWon == host ? "YOU WIN" : "YOU LOSE";
    }

    private static void bind(JComponent component, int key, Runnable action) {
        KeyStroke stroke = KeyStroke.getKeyStroke(key, 0);
        component.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, stroke);
        component.getActionMap().put(stroke, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private static JLabel label(String text, int size) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, size));
        label.setForeground(changeColor(Color.DARK_GRAY));
        return label;
    }
}
