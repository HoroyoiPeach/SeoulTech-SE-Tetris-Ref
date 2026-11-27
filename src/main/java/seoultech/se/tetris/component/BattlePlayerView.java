package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
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
    private final boolean classicSidebar;
    private final Board board = new Board();
    private final Nextblockpanel next = new Nextblockpanel();
    private final JLabel score = new JLabel("SCORE 0", SwingConstants.CENTER);
    private final JLabel playerTitle = new JLabel("", SwingConstants.CENTER);
    private final JLabel nextTitle = new JLabel("NEXT", SwingConstants.CENTER);
    private final JLabel scoreTitle = new JLabel("Scores", SwingConstants.CENTER);
    private final IncomingAttackPanel incoming;
    private JPanel scoreArea;

    BattlePlayerView(String titleText, String componentPrefix) {
        this(titleText, componentPrefix, true);
    }

    static BattlePlayerView local(String titleText, String componentPrefix) {
        return new BattlePlayerView(titleText, componentPrefix, false);
    }

    private BattlePlayerView(String titleText, String componentPrefix, boolean classicSidebar) {
        super(new BorderLayout(3, 3));
        this.classicSidebar = classicSidebar;
        setOpaque(false);
        setBorder(BorderFactory.createLineBorder(changeColor(Color.DARK_GRAY)));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        playerTitle.setText(titleText);
        playerTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        playerTitle.setForeground(changeColor(Color.DARK_GRAY));
        heading.add(playerTitle, BorderLayout.CENTER);
        score.setName(componentPrefix + "Score");
        score.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        score.setForeground(changeColor(Color.RED));
        if (!classicSidebar) heading.add(score, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
        board.setName(componentPrefix + "Board");
        board.setPreferredSize(classicSidebar ? BOARD_SIZE : new Dimension(180, 330));

        if (!classicSidebar) {
            add(board, BorderLayout.CENTER);
            incoming = originalInformation(componentPrefix);
            return;
        }

        next.setPreferredSize(new Dimension(140, 140));
        next.setName(componentPrefix + "Next");
        JPanel nextArea = new JPanel(new BorderLayout());
        nextArea.setOpaque(false);
        nextTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        nextTitle.setForeground(changeColor(Color.DARK_GRAY));
        nextArea.add(nextTitle, BorderLayout.NORTH);
        nextArea.add(new AspectPanel(next, 1.0), BorderLayout.CENTER);
        JPanel scoreArea = p2pScoreArea();
        score.setText("0");
        incoming = new IncomingAttackPanel(componentPrefix);
        JPanel information = new P2PSidebarPanel(nextArea, scoreArea, incoming);
        add(new BattleContentPanel(new AspectPanel(board, NORMAL_BOARD_RATIO), information),
            BorderLayout.CENTER);
    }

    private JPanel p2pScoreArea() {
        scoreArea = new JPanel(new GridLayout(2, 1));
        scoreArea.setOpaque(false);
        scoreArea.setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY)));
        scoreTitle.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
        scoreTitle.setForeground(changeColor(Color.GRAY));
        score.setHorizontalAlignment(SwingConstants.RIGHT);
        score.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
        scoreArea.add(scoreTitle);
        scoreArea.add(score);
        return scoreArea;
    }

    private IncomingAttackPanel originalInformation(String componentPrefix) {
        JPanel information = new JPanel(new GridLayout(1, 2, 3, 0));
        information.setOpaque(false);
        next.setName(componentPrefix + "Next");
        JPanel nextArea = new JPanel(new BorderLayout());
        nextArea.setOpaque(false);
        nextTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        nextTitle.setForeground(changeColor(Color.DARK_GRAY));
        nextArea.add(nextTitle, BorderLayout.NORTH);
        nextArea.add(next, BorderLayout.CENTER);
        IncomingAttackPanel originalIncoming = new IncomingAttackPanel(componentPrefix);
        information.add(nextArea);
        information.add(originalIncoming);
        add(information, BorderLayout.SOUTH);
        return originalIncoming;
    }

    void show(GameSnapshot snapshot,
            List<AttackRow> attacks) {
        board.showSnapshot(snapshot);
        next.drawNextBoard(snapshot.next());
        score.setText(classicSidebar ? Integer.toString(snapshot.score()) : "SCORE " + snapshot.score());
        incoming.showRows(attacks);
    }

    void refreshTheme() {
        setBorder(BorderFactory.createLineBorder(changeColor(Color.DARK_GRAY)));
        playerTitle.setForeground(changeColor(Color.DARK_GRAY));
        nextTitle.setForeground(changeColor(Color.DARK_GRAY));
        scoreTitle.setForeground(changeColor(Color.GRAY));
        score.setForeground(changeColor(Color.RED));
        if (scoreArea != null) {
            scoreArea.setBorder(BorderFactory.createLineBorder(changeColor(Color.GRAY)));
        }
        board.refreshTheme();
        next.refreshTheme();
        incoming.refreshTheme();
        repaint();
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

    /** Keeps every status readable while shrinking the expendable NEXT preview first. */
    private static final class P2PSidebarPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int GAP = 4;
        private final JComponent next;
        private final JComponent score;
        private final JComponent incoming;

        private P2PSidebarPanel(JComponent next, JComponent score, JComponent incoming) {
            this.next = next;
            this.score = score;
            this.incoming = incoming;
            setOpaque(false);
            setLayout(null);
            add(next);
            add(score);
            add(incoming);
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(140, next.getPreferredSize().height + score.getPreferredSize().height
                + incoming.getPreferredSize().height + 2 * GAP);
        }

        @Override public void doLayout() {
            int width = getWidth();
            int height = getHeight();
            int incomingHeight = Math.min(incoming.getPreferredSize().height, height);
            int remaining = Math.max(0, height - incomingHeight);
            int incomingGap = remaining > 0 ? GAP : 0;
            remaining = Math.max(0, remaining - incomingGap);
            int scoreHeight = Math.min(score.getPreferredSize().height, remaining);
            remaining -= scoreHeight;
            int scoreGap = remaining > 0 ? GAP : 0;
            int nextHeight = Math.min(next.getPreferredSize().height,
                Math.max(0, remaining - scoreGap));
            next.setBounds(0, 0, width, nextHeight);
            score.setBounds(0, nextHeight + scoreGap, width, scoreHeight);
            incoming.setBounds(0, nextHeight + scoreGap + scoreHeight + incomingGap,
                width, incomingHeight);
        }
    }
}
