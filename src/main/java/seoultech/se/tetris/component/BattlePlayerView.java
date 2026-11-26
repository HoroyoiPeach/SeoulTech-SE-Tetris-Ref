package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import seoultech.se.tetris.attack.AttackRow;
import seoultech.se.tetris.game.GameSnapshot;

/** One player's board, points, next piece and incoming rows on either battle screen. */
final class BattlePlayerView extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Board board = new Board();
    private final Nextblockpanel next = new Nextblockpanel();
    private final JLabel score = new JLabel("SCORE 0", SwingConstants.CENTER);
    private final IncomingAttackPanel incoming;

    BattlePlayerView(String titleText, String componentPrefix) {
        super(new BorderLayout(3, 3));
        setOpaque(false);
        setBorder(BorderFactory.createLineBorder(changeColor(Color.DARK_GRAY)));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel(titleText, SwingConstants.CENTER);
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        title.setForeground(changeColor(Color.DARK_GRAY));
        heading.add(title, BorderLayout.CENTER);
        score.setName(componentPrefix + "Score");
        score.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        score.setForeground(changeColor(Color.RED));
        heading.add(score, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
        board.setName(componentPrefix + "Board");
        board.setPreferredSize(new Dimension(180, 330));
        add(board, BorderLayout.CENTER);

        JPanel information = new JPanel(new GridLayout(1, 2, 3, 0));
        information.setOpaque(false);
        next.setName(componentPrefix + "Next");
        JPanel nextArea = new JPanel(new BorderLayout());
        nextArea.setOpaque(false);
        JLabel nextTitle = new JLabel("NEXT", SwingConstants.CENTER);
        nextTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        nextTitle.setForeground(changeColor(Color.DARK_GRAY));
        nextArea.add(nextTitle, BorderLayout.NORTH);
        nextArea.add(next, BorderLayout.CENTER);
        incoming = new IncomingAttackPanel(componentPrefix);
        information.add(nextArea);
        information.add(incoming);
        add(information, BorderLayout.SOUTH);
    }

    void show(GameSnapshot snapshot,
            List<AttackRow> attacks) {
        board.showSnapshot(snapshot);
        next.drawNextBoard(snapshot.next());
        score.setText("SCORE " + snapshot.score());
        incoming.showRows(attacks);
    }
}
