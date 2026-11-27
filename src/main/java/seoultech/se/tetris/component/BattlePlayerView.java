package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import seoultech.se.tetris.attack.AttackRow;
import seoultech.se.tetris.game.GameSnapshot;

/** One player's board, points, next piece and incoming rows on either battle screen. */
final class BattlePlayerView extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final double NORMAL_BOARD_RATIO = 330.0 / 580.0;
    private static final Dimension BOARD_SIZE = new Dimension(180, 316);
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
        add(heading, BorderLayout.NORTH);
        board.setName(componentPrefix + "Board");
        board.setPreferredSize(BOARD_SIZE);

        JPanel information = new JPanel(new BorderLayout(0, 4));
        information.setOpaque(false);
        next.setPreferredSize(new Dimension(140, 140));
        next.setName(componentPrefix + "Next");
        JPanel nextArea = new JPanel(new BorderLayout());
        nextArea.setOpaque(false);
        JLabel nextTitle = new JLabel("NEXT", SwingConstants.CENTER);
        nextTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        nextTitle.setForeground(changeColor(Color.DARK_GRAY));
        nextArea.add(nextTitle, BorderLayout.NORTH);
        nextArea.add(new AspectPanel(next, 1.0), BorderLayout.CENTER);
        nextArea.add(score, BorderLayout.SOUTH);
        information.add(nextArea, BorderLayout.NORTH);
        incoming = new IncomingAttackPanel(componentPrefix);
        information.add(incoming, BorderLayout.CENTER);
        add(new BattleContentPanel(new AspectPanel(board, NORMAL_BOARD_RATIO), information),
            BorderLayout.CENTER);
    }

    void show(GameSnapshot snapshot,
            List<AttackRow> attacks) {
        board.showSnapshot(snapshot);
        next.drawNextBoard(snapshot.next());
        score.setText("SCORE " + snapshot.score());
        incoming.showRows(attacks);
    }

    /** Keeps the battle board at the same proportions as the normal game board. */
    private static final class AspectPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JComponent content;
        private final double ratio;

        private AspectPanel(JComponent content, double ratio) {
            this.content = content;
            this.ratio = ratio;
            setOpaque(false);
            setLayout(null);
            add(content);
        }

        @Override public Dimension getPreferredSize() {
            Insets insets = getInsets();
            Dimension preferred = content.getPreferredSize();
            return new Dimension(preferred.width + insets.left + insets.right,
                preferred.height + insets.top + insets.bottom);
        }

        @Override public void doLayout() {
            Insets insets = getInsets();
            int availableWidth = Math.max(0, getWidth() - insets.left - insets.right);
            int availableHeight = Math.max(0, getHeight() - insets.top - insets.bottom);
            int width = availableWidth;
            int height = (int) Math.round(width / ratio);
            if (height > availableHeight) {
                height = availableHeight;
                width = (int) Math.round(height * ratio);
            }
            content.setBounds(insets.left + (availableWidth - width) / 2,
                insets.top + (availableHeight - height) / 2, width, height);
        }
    }

    /** Mirrors the normal game's board-left and information-right composition. */
    private static final class BattleContentPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int GAP = 4;
        private final JComponent board;
        private final JComponent information;

        private BattleContentPanel(JComponent board, JComponent information) {
            this.board = board;
            this.information = information;
            setOpaque(false);
            setLayout(null);
            add(board);
            add(information);
        }

        @Override public Dimension getPreferredSize() {
            Dimension boardSize = board.getPreferredSize();
            return new Dimension(boardSize.width + GAP + 90, boardSize.height);
        }

        @Override public void doLayout() {
            int width = getWidth();
            int height = getHeight();
            double informationShare = width < 360 ? 0.45 : 0.30;
            int informationWidth = Math.min(140, (int) Math.round(width * informationShare));
            int gap = width > informationWidth ? GAP : 0;
            int boardWidth = Math.max(0, width - informationWidth - gap);
            board.setBounds(0, 0, boardWidth, height);
            information.setBounds(boardWidth + gap, 0, informationWidth, height);
        }
    }
}
